package com.example.platform.evaluation.agent;

import com.example.platform.common.BusinessException;
import com.example.platform.evaluation.config.LangfuseProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * AgentIngestController 单测 —— 覆盖鉴权与标签注入两条核心逻辑：
 * ingestion 事件（trace-create/trace-update）自动追加平台标签；
 * OTLP/HTTP JSON 在资源属性与每个 span 属性中合并平台标签（Langfuse 只读取 span 属性）。
 */
@ExtendWith(MockitoExtension.class)
class AgentIngestControllerTest {

    private static final String PUBLIC_KEY = "pk-lf-test";
    private static final String SECRET_KEY = "sk-lf-test";

    @Mock
    private EvalAgentConnectionMapper connectionMapper;

    private final ObjectMapper json = new ObjectMapper();
    private AgentTokenService tokenService;
    private AgentIngestController controller;

    @BeforeEach
    void setUp() {
        tokenService = new AgentTokenService();
        LangfuseProperties properties = new LangfuseProperties();
        properties.setHost("http://langfuse-web:3000");
        properties.setPublicKey("pk-platform");
        properties.setSecretKey("sk-platform");
        controller = new AgentIngestController(connectionMapper, tokenService, properties);
    }

    // ================= 标签注入 =================

    @Test
    void ingestionInjectsPlatformTagsIntoTraceEventsOnly() throws Exception {
        String body = """
                {"batch":[
                  {"type":"trace-create","body":{"id":"t1","name":"n","tags":["keep"]}},
                  {"type":"trace-update","body":{"id":"t1","name":"n2"}},
                  {"type":"trace-create","body":{"id":"t2","name":"legacy","tags":"single"}},
                  {"type":"span-create","body":{"id":"s1","traceId":"t1","tags":["span-tag"]}}
                ]}
                """;

        AgentIngestController.InjectedPayload payload =
                controller.injectIngestTags(body, connection("CONNECTED"));

        JsonNode batch = json.readTree(payload.body()).path("batch");
        JsonNode traceTags = batch.get(0).path("body").path("tags");
        assertStringTags(traceTags, "keep", "space_id=1", "target_id=7", "agent_key=agent-abc", "source=online");

        JsonNode updateTags = batch.get(1).path("body").path("tags");
        assertStringTags(updateTags, "space_id=1", "target_id=7", "agent_key=agent-abc", "source=online");

        // tags 写成字符串的兼容处理：保留原标签并追加平台标签
        JsonNode legacyTags = batch.get(2).path("body").path("tags");
        assertStringTags(legacyTags, "single", "space_id=1", "target_id=7", "agent_key=agent-abc", "source=online");

        // span-create 不注入（Langfuse 的 observation 事件没有 tags 语义）
        JsonNode spanTags = batch.get(3).path("body").path("tags");
        assertEquals(1, spanTags.size());
        assertEquals("span-tag", spanTags.get(0).asText());

        // 只有 trace-create 计入 trace 数
        assertEquals(2, payload.traceCount());
    }

    @Test
    void otlpMergesTagsIntoResourceAndEverySpan() throws Exception {
        String body = """
                {"resourceSpans":[{
                  "resource":{"attributes":[
                    {"key":"service.name","value":{"stringValue":"svc"}}
                  ]},
                  "scopeSpans":[{"spans":[
                    {"traceId":"t","spanId":"s1","attributes":[
                      {"key":"langfuse.trace.tags","value":{"arrayValue":{"values":[
                        {"stringValue":"agent-side"},{"stringValue":"space_id=1"}
                      ]}}}
                    ]},
                    {"traceId":"t2","spanId":"s2","attributes":[
                      {"key":"langfuse.observation.type","value":{"stringValue":"span"}}
                    ]}
                  ]}]
                }]}
                """;

        AgentIngestController.InjectedPayload payload =
                controller.injectOtelTags(body, connection("CONNECTED"));

        JsonNode resourceSpan = json.readTree(payload.body()).path("resourceSpans").get(0);
        JsonNode resourceTags = tagsAttribute(resourceSpan.path("resource").path("attributes"));
        assertTags(resourceTags, "space_id=1", "target_id=7", "agent_key=agent-abc", "source=online");

        JsonNode spanWithExistingTags = resourceSpan.path("scopeSpans").get(0).path("spans").get(0);
        assertTags(tagsAttribute(spanWithExistingTags.path("attributes")),
                "space_id=1", "target_id=7", "agent_key=agent-abc", "source=online", "agent-side");

        JsonNode spanWithoutTags = resourceSpan.path("scopeSpans").get(0).path("spans").get(1);
        assertTags(tagsAttribute(spanWithoutTags.path("attributes")),
                "space_id=1", "target_id=7", "agent_key=agent-abc", "source=online");
        // 原有 span 属性保留（合并后的 tags 属性追加在末尾）
        assertEquals("langfuse.observation.type",
                spanWithoutTags.path("attributes").get(0).path("key").asText());

        // traceCount 按去重后的 traceId 计数
        assertEquals(2, payload.traceCount());
    }

    @Test
    void otlpRejectsProtobufContentType() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.otelTraces(null, "application/x-protobuf", "{}"));
        assertEquals("INGEST_INVALID", ex.getCode());
        assertTrue(ex.getMessage().contains("JSON"));
    }

    @Test
    void invalidJsonIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.injectIngestTags("{not-json", connection("CONNECTED")));
        assertEquals("INGEST_INVALID", ex.getCode());
    }

    // ================= 鉴权 =================

    @Test
    void rejectsMissingAuthorization() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.ingest(null, "{\"batch\":[]}"));
        assertEquals("AGENT_UNAUTHORIZED", ex.getCode());
    }

    @Test
    void rejectsUnknownPublicKey() {
        when(connectionMapper.findByPublicKey(PUBLIC_KEY)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.ingest(basic(PUBLIC_KEY, SECRET_KEY), "{\"batch\":[]}"));
        assertEquals("AGENT_UNAUTHORIZED", ex.getCode());
    }

    @Test
    void rejectsWrongSecret() {
        EvalAgentConnectionEntity stored = connection("CONNECTED");
        when(connectionMapper.findByPublicKey(PUBLIC_KEY)).thenReturn(Optional.of(stored));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.ingest(basic(PUBLIC_KEY, "sk-lf-wrong"), "{\"batch\":[]}"));
        assertEquals("AGENT_UNAUTHORIZED", ex.getCode());
    }

    @Test
    void rejectsDisabledAgent() {
        EvalAgentConnectionEntity stored = connection("DISABLED");
        when(connectionMapper.findByPublicKey(PUBLIC_KEY)).thenReturn(Optional.of(stored));

        // Basic 认证 scheme 大小写不敏感
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.ingest(basic(PUBLIC_KEY, SECRET_KEY).replaceFirst("Basic", "basic"),
                        "{\"batch\":[]}"));
        assertEquals("AGENT_DISABLED", ex.getCode());
    }

    // ================= helpers =================

    private EvalAgentConnectionEntity connection(String status) {
        EvalAgentConnectionEntity entity = new EvalAgentConnectionEntity();
        entity.setSpaceId(1L);
        entity.setTargetId(7L);
        entity.setAgentKey("agent-abc");
        entity.setPublicKey(PUBLIC_KEY);
        entity.setSecretHash(tokenService.hash(PUBLIC_KEY, SECRET_KEY));
        entity.setStatus(status);
        return entity;
    }

    private static String basic(String publicKey, String secretKey) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
    }

    private static JsonNode tagsAttribute(JsonNode attributes) {
        for (JsonNode attribute : attributes) {
            if ("langfuse.trace.tags".equals(attribute.path("key").asText())) {
                return attribute.path("value").path("arrayValue").path("values");
            }
        }
        throw new AssertionError("langfuse.trace.tags attribute not found");
    }

    /** OTLP 属性格式：values[*].stringValue */
    private static void assertTags(JsonNode tagValues, String... expected) {
        assertEquals(expected.length, tagValues.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], tagValues.get(i).path("stringValue").asText());
        }
    }

    /** Langfuse ingestion 格式：body.tags 是字符串数组 */
    private static void assertStringTags(JsonNode tagValues, String... expected) {
        assertEquals(expected.length, tagValues.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], tagValues.get(i).asText());
        }
    }
}
