package com.example.platform.evaluation.agent;

import com.example.platform.common.BusinessException;
import com.example.platform.evaluation.config.LangfuseProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Agent Ingest 代理 —— Agent 把 Langfuse SDK 的 HOST 指向这里，请求经校验后转发到自建 Langfuse。
 *
 * <ul>
 *   <li>{@code POST /api/evaluation/ingest/api/public/ingestion}：Langfuse SDK JSON 批量事件，自动注入
 *       {@code space_id / target_id / agent_key} 标签</li>
 *   <li>{@code POST /api/evaluation/ingest/api/public/otel/v1/traces}：OTLP/HTTP JSON，自动注入
 *       {@code langfuse.trace.tags} 资源属性</li>
 * </ul>
 *
 * <p>鉴权使用发放给 Agent 的 Langfuse 风格 public/secret key（Basic Auth），平台只保存 secret hash。
 */
@RestController
@RequestMapping("/api/evaluation/ingest")
public class AgentIngestController {

    private static final Logger log = LoggerFactory.getLogger(AgentIngestController.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final EvalAgentConnectionMapper connectionMapper;
    private final AgentTokenService tokenService;
    private final LangfuseProperties langfuse;
    private final WebClient webClient;

    public AgentIngestController(EvalAgentConnectionMapper connectionMapper,
                                 AgentTokenService tokenService,
                                 LangfuseProperties langfuse) {
        this.connectionMapper = connectionMapper;
        this.tokenService = tokenService;
        this.langfuse = langfuse;
        this.webClient = WebClient.builder().baseUrl(normalizeHost(langfuse.getHost())).build();
    }

    /** Langfuse SDK JSON ingestion 透传（v2 风格批量事件）。 */
    @PostMapping("/api/public/ingestion")
    public ResponseEntity<String> ingest(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) String body) {
        EvalAgentConnectionEntity connection = authenticate(authorization);
        InjectedPayload injected = injectIngestTags(body, connection);
        ResponseEntity<String> response = forward("/api/public/ingestion",
                injected.body(), MediaType.APPLICATION_JSON_VALUE);
        if (response.getStatusCode().is2xxSuccessful()) {
            connectionMapper.touch(connection.getTargetId(), Math.max(injected.traceCount(), 1));
        }
        return response;
    }

    /** OTLP/HTTP JSON trace 透传（Langfuse SDK v3 / OTel）。 */
    @PostMapping("/api/public/otel/v1/traces")
    public ResponseEntity<String> otelTraces(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @RequestBody(required = false) String body) {
        if (contentType == null || !contentType.toLowerCase().contains("json")) {
            throw new BusinessException("INGEST_INVALID",
                    "当前仅支持 OTLP/HTTP JSON。请设置 OTEL_EXPORTER_OTLP_PROTOCOL=http/json，"
                            + "或使用 Langfuse SDK 的 /api/public/ingestion 通道");
        }
        EvalAgentConnectionEntity connection = authenticate(authorization);
        InjectedPayload injected = injectOtelTags(body, connection);
        ResponseEntity<String> response = forward("/api/public/otel/v1/traces",
                injected.body(), MediaType.APPLICATION_JSON_VALUE);
        if (response.getStatusCode().is2xxSuccessful()) {
            // 一个 OTLP 批次可能包含多条 trace，按去重后的 traceId 数量累计
            connectionMapper.touch(connection.getTargetId(), Math.max(injected.traceCount(), 1));
        }
        return response;
    }

    // ================= 鉴权 =================

    private EvalAgentConnectionEntity authenticate(String authorization) {
        if (authorization == null || authorization.length() <= 6
                || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            throw new BusinessException("AGENT_UNAUTHORIZED",
                    "缺少 Agent 接入密钥（Authorization: Basic base64(publicKey:secretKey)）");
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(authorization.substring(6).trim()),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("AGENT_UNAUTHORIZED", "Authorization 头格式非法");
        }
        int separator = decoded.indexOf(':');
        if (separator <= 0) {
            throw new BusinessException("AGENT_UNAUTHORIZED", "Authorization 头格式非法");
        }
        String publicKey = decoded.substring(0, separator);
        String secretKey = decoded.substring(separator + 1);

        EvalAgentConnectionEntity connection = connectionMapper.findByPublicKey(publicKey)
                .orElseThrow(() -> new BusinessException("AGENT_UNAUTHORIZED", "Agent 公钥无效"));
        if (!tokenService.matches(publicKey, secretKey, connection.getSecretHash())) {
            throw new BusinessException("AGENT_UNAUTHORIZED", "Agent 密钥无效");
        }
        if (!"CONNECTED".equals(connection.getStatus())) {
            throw new BusinessException("AGENT_DISABLED", "Agent 接入已停用，请先在平台启用");
        }
        return connection;
    }

    // ================= 标签注入 =================

    InjectedPayload injectIngestTags(String body, EvalAgentConnectionEntity connection) {
        if (body == null || body.isBlank()) {
            throw new BusinessException("INGEST_INVALID", "请求体不能为空");
        }
        try {
            JsonNode root = JSON.readTree(body);
            if (!root.isObject() || !root.path("batch").isArray()) {
                throw new BusinessException("INGEST_INVALID", "ingestion 请求体必须包含 batch 数组");
            }
            int traceCount = 0;
            for (JsonNode event : root.path("batch")) {
                if (!event.isObject()) {
                    continue;
                }
                String type = event.path("type").asText("");
                if (!"trace-create".equals(type) && !"trace-update".equals(type)) {
                    continue;
                }
                JsonNode eventBody = event.get("body");
                if (!(eventBody instanceof ObjectNode bodyNode)) {
                    continue;
                }
                appendTag(bodyNode, "space_id=" + connection.getSpaceId());
                appendTag(bodyNode, "target_id=" + connection.getTargetId());
                appendTag(bodyNode, "agent_key=" + connection.getAgentKey());
                appendTag(bodyNode, "source=online");
                if ("trace-create".equals(type)) {
                    traceCount++;
                }
            }
            return new InjectedPayload(JSON.writeValueAsString(root), traceCount);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("INGEST_INVALID", "ingestion 请求体不是合法 JSON: " + e.getMessage());
        }
    }

    private static void appendTag(ObjectNode bodyNode, String tag) {
        JsonNode existing = bodyNode.get("tags");
        ArrayNode tags;
        if (existing instanceof ArrayNode arrayNode) {
            tags = arrayNode;
        } else {
            tags = bodyNode.putArray("tags");
            // 兼容把 tags 写成单个字符串的 SDK 实现，避免覆盖掉 Agent 原有标签
            if (existing != null && existing.isTextual() && !existing.asText().isBlank()) {
                tags.add(existing.asText());
            }
        }
        for (JsonNode item : tags) {
            if (tag.equals(item.asText())) {
                return;
            }
        }
        tags.add(tag);
    }

    InjectedPayload injectOtelTags(String body, EvalAgentConnectionEntity connection) {
        if (body == null || body.isBlank()) {
            throw new BusinessException("INGEST_INVALID", "请求体不能为空");
        }
        try {
            JsonNode root = JSON.readTree(body);
            if (!root.isObject() || !root.path("resourceSpans").isArray()) {
                throw new BusinessException("INGEST_INVALID", "OTLP JSON 请求体必须包含 resourceSpans 数组");
            }
            List<String> requiredTags = List.of(
                    "space_id=" + connection.getSpaceId(),
                    "target_id=" + connection.getTargetId(),
                    "agent_key=" + connection.getAgentKey(),
                    "source=online");
            Set<String> traceIds = new HashSet<>();

            for (JsonNode resourceSpan : root.path("resourceSpans")) {
                if (!(resourceSpan instanceof ObjectNode spanNode)) {
                    continue;
                }
                JsonNode resourceNode = spanNode.get("resource");
                ObjectNode resource = resourceNode instanceof ObjectNode r
                        ? r : spanNode.putObject("resource");
                JsonNode resourceAttributesNode = resource.get("attributes");
                ArrayNode resourceAttributes = resourceAttributesNode instanceof ArrayNode a
                        ? a : resource.putArray("attributes");
                mergeTagsAttribute(resourceAttributes, requiredTags);

                // Langfuse 的 OTel 处理器只从 span 属性读取 langfuse.trace.tags（资源级不会生效），
                // 因此必须把标签逐个合并进 span 的属性中，才能保证 trace 带上平台标签。
                for (JsonNode scopeSpan : spanNode.path("scopeSpans")) {
                    if (!(scopeSpan instanceof ObjectNode scopeSpanNode)) {
                        continue;
                    }
                    for (JsonNode span : scopeSpanNode.path("spans")) {
                        if (!(span instanceof ObjectNode spanObject)) {
                            continue;
                        }
                        JsonNode spanAttributesNode = spanObject.get("attributes");
                        ArrayNode spanAttributes = spanAttributesNode instanceof ArrayNode a
                                ? a : spanObject.putArray("attributes");
                        mergeTagsAttribute(spanAttributes, requiredTags);

                        String traceId = spanObject.path("traceId").asText("");
                        if (!traceId.isBlank()) {
                            traceIds.add(traceId);
                        }
                    }
                }
            }
            return new InjectedPayload(JSON.writeValueAsString(root), Math.max(traceIds.size(), 1));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("INGEST_INVALID", "OTLP JSON 请求体非法: " + e.getMessage());
        }
    }

    /**
     * 合并 {@code langfuse.trace.tags} 属性：平台标签在前，Agent 原有标签保留且去重。
     * 兼容 OTLP JSON 的 {@code arrayValue.values[*].stringValue} 与单个 {@code stringValue} 写法。
     */
    private static void mergeTagsAttribute(ArrayNode attributes, List<String> requiredTags) {
        List<String> merged = new ArrayList<>(requiredTags);
        for (JsonNode attribute : attributes) {
            if (!"langfuse.trace.tags".equals(attribute.path("key").asText())) {
                continue;
            }
            JsonNode values = attribute.path("value").path("arrayValue").path("values");
            if (values.isArray()) {
                for (JsonNode item : values) {
                    addTagIfAbsent(merged, item.path("stringValue").asText(null));
                }
            } else {
                addTagIfAbsent(merged, attribute.path("value").path("stringValue").asText(null));
            }
            break;
        }
        removeAttribute(attributes, "langfuse.trace.tags");
        ObjectNode attribute = JSON.createObjectNode();
        attribute.put("key", "langfuse.trace.tags");
        ArrayNode values = attribute.putObject("value").putObject("arrayValue").putArray("values");
        for (String tag : merged) {
            values.addObject().put("stringValue", tag);
        }
        attributes.add(attribute);
    }

    private static void addTagIfAbsent(List<String> tags, String tag) {
        if (tag != null && !tag.isBlank() && !tags.contains(tag)) {
            tags.add(tag);
        }
    }

    private static void removeAttribute(ArrayNode attributes, String key) {
        for (int i = attributes.size() - 1; i >= 0; i--) {
            if (key.equals(attributes.get(i).path("key").asText())) {
                attributes.remove(i);
            }
        }
    }

    // ================= 转发 =================

    private ResponseEntity<String> forward(String path, String body, String contentType) {
        try {
            ResponseEntity<String> response = webClient.post()
                    .uri(path)
                    .header("Authorization", platformBasicAuth())
                    .header("Content-Type", contentType)
                    .bodyValue(body)
                    .exchangeToMono(resp -> resp.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(responseBody -> ResponseEntity.status(resp.statusCode())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(responseBody)))
                    .block(Duration.ofSeconds(20));
            return response != null ? response : badGateway("Langfuse 无响应");
        } catch (Exception e) {
            log.warn("Agent ingest forward failed: {}", e.getMessage());
            return badGateway("Langfuse 转发失败: " + e.getMessage());
        }
    }

    private String platformBasicAuth() {
        String raw = nullToEmpty(langfuse.getPublicKey()) + ":" + nullToEmpty(langfuse.getSecretKey());
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static ResponseEntity<String> badGateway(String message) {
        String payload = "{\"code\":\"LANGFUSE_UPSTREAM_FAILED\",\"msg\":\""
                + message.replace("\"", "\\\"") + "\"}";
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload);
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return "http://localhost:3000";
        }
        return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    record InjectedPayload(String body, int traceCount) {}
}
