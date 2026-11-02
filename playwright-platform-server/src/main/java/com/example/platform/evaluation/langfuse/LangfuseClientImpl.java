package com.example.platform.evaluation.langfuse;

import com.example.platform.evaluation.config.LangfuseProperties;
import com.example.platform.evaluation.exception.LangfuseIntegrationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LangfuseClient 的 REST API 实现。
 *
 * <p>写入操作（writeTrace / writeObservation / writeScore）吞掉 HTTP 异常，仅日志告警 ——
 * 保证评测主流程不被 Langfuse 故障阻塞（C5 可降级）。
 *
 * <p>读取操作（queryTraces / aggregateMetrics）通过 {@link LangfuseGatekeeper} 控制并发（C21）。
 */
@Component
public class LangfuseClientImpl implements LangfuseClient {

    private static final Logger log = LoggerFactory.getLogger(LangfuseClientImpl.class);

    private final LangfuseProperties props;
    private final LangfuseGatekeeper gatekeeper;
    private final WebClient writeWebClient;
    private final WebClient queryWebClient;
    private final ObjectMapper objectMapper;

    public LangfuseClientImpl(LangfuseProperties props,
                              LangfuseGatekeeper gatekeeper,
                              ObjectMapper objectMapper) {
        this.props = props;
        this.gatekeeper = gatekeeper;
        this.objectMapper = objectMapper;

        // Write WebClient —— 用写 Key
        this.writeWebClient = WebClient.builder()
                .baseUrl(normalizeHost(props.getHost()))
                .build();
        // Query WebClient —— 用只读 Key（C12）
        this.queryWebClient = WebClient.builder()
                .baseUrl(normalizeHost(props.getHost()))
                .build();
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) return "http://localhost:3000";
        return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    }

    // ===== Write Path —— 吞异常 =====

    @Override
    public void writeTrace(TraceWriteRequest request) {
        if (!props.isEnabled() || request == null || request.traceId() == null) return;
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("id", UUID.randomUUID().toString());
            event.put("type", "trace-create");
            event.put("timestamp", Instant.now().toString());
            event.put("body", buildTraceBody(request));
            ingest(List.of(event));
        } catch (Exception e) {
            log.warn("Langfuse trace write failed (C5 degraded): traceId={}, err={}",
                    request.traceId(), e.getMessage());
        }
    }

    @Override
    public void writeObservation(ObservationWriteRequest request) {
        if (!props.isEnabled() || request == null || request.traceId() == null) return;
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("id", UUID.randomUUID().toString());
            // 有 model 的 observation 记为 generation，便于 Langfuse 统计 token/模型
            event.put("type", request.model() != null ? "generation-create" : "span-create");
            event.put("timestamp", Instant.now().toString());
            event.put("body", buildObservationBody(request));
            ingest(List.of(event));
        } catch (Exception e) {
            log.warn("Langfuse observation write failed (C5 degraded): traceId={}, err={}",
                    request.traceId(), e.getMessage());
        }
    }

    /** Langfuse v3 统一的批量写入入口（trace / span / generation 都走 ingestion）。 */
    private void ingest(List<Map<String, Object>> events) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("batch", events);
        writeWebClient.post()
                .uri("/api/public/ingestion")
                .header("Authorization", basicAuth(props.getPublicKey(), props.getSecretKey()))
                .header("Content-Type", "application/json")
                .bodyValue(objectMapper.writeValueAsString(payload))
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(5));
    }

    @Override
    public void writeScore(ScoreWriteRequest request) {
        if (!props.isEnabled() || request == null || request.traceId() == null) return;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("traceId", request.traceId());
            body.put("name", request.name());
            body.put("value", request.value());
            body.put("comment", request.comment());
            body.put("dataType", request.dataType());
            body.put("dimension", request.dimension());
            body.put("observationId", request.observationId());
            writeWebClient.post()
                    .uri("/api/public/scores")
                    .header("Authorization", basicAuth(props.getPublicKey(), props.getSecretKey()))
                    .header("Content-Type", "application/json")
                    .bodyValue(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(5));
        } catch (Exception e) {
            log.warn("Langfuse score write failed (C5 degraded): traceId={}, err={}",
                    request.traceId(), e.getMessage());
        }
    }

    // ===== Query Path —— 走 Gatekeeper =====

    @Override
    public Page<LangfuseTrace> queryTraces(LangfuseTraceFilter filter, Pageable pageable) {
        return gatekeeper.acquire(() -> doQueryTraces(filter, pageable));
    }

    private Page<LangfuseTrace> doQueryTraces(LangfuseTraceFilter filter, Pageable pageable) {
        if (!props.isEnabled()) return Page.empty();
        try {
            Map<String, Object> params = new HashMap<>();
            if (filter.startTimeFrom() != null) params.put("fromTimestamp", filter.startTimeFrom().toString());
            if (filter.startTimeTo() != null) params.put("toTimestamp", filter.startTimeTo().toString());

            // Langfuse 没有 targetId 参数，必须转成 tag 过滤（写入侧统一打 target_id=<id>）
            List<String> tags = new ArrayList<>();
            if (filter.tags() != null) tags.addAll(filter.tags());
            if (filter.targetId() != null && !filter.targetId().isBlank()) {
                tags.add("target_id=" + filter.targetId());
            }
            if (Boolean.TRUE.equals(filter.onlyFailed())) {
                tags.add("error=true");
            }
            if (!tags.isEmpty()) {
                params.put("tags", tags);
            }

            // Langfuse 只支持单个 sessionId，多值走本地过滤
            if (filter.sessionIds() != null && filter.sessionIds().size() == 1) {
                params.put("sessionId", filter.sessionIds().get(0));
            }
            if (filter.userId() != null && !filter.userId().isBlank()) {
                params.put("userId", filter.userId());
            }

            // Langfuse 分页从 1 开始，Spring Pageable 从 0 开始；limit 上限 100（超过会 400）
            int pageNumber = pageable != null && pageable.isPaged() ? pageable.getPageNumber() + 1 : 1;
            int requestedSize = pageable != null && pageable.isPaged() ? pageable.getPageSize() : 50;
            int pageSize = Math.min(Math.max(requestedSize, 1), 100);
            params.put("page", pageNumber);
            params.put("limit", pageSize);

            String response = queryWebClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/public/traces");
                        params.forEach((key, value) -> {
                            if (value instanceof List<?> list) {
                                list.forEach(item -> uriBuilder.queryParam(key, item));
                            } else {
                                uriBuilder.queryParam(key, value);
                            }
                        });
                        return uriBuilder.build();
                    })
                    .header("Authorization", basicAuth(props.queryPublicKey(), props.querySecretKey()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));

            List<LangfuseTrace> traces = parseTraces(response);
            List<LangfuseTrace> filtered = traces.stream()
                    .filter(trace -> LangfuseTraceFilterMatcher.matches(trace, filter))
                    .toList();
            // 本地过滤丢掉了数据时，以过滤后的条数为准，避免「total=2 但只显示 1 条」
            long total = filtered.size() == traces.size()
                    ? parseTotal(response, filtered.size())
                    : filtered.size();
            return new PageImpl<>(filtered, pageable != null ? pageable : Pageable.unpaged(), total);
        } catch (Exception e) {
            log.warn("Langfuse trace query failed: {}", e.getMessage());
            return Page.empty();
        }
    }

    @Override
    public List<LangfuseTrace> listTracesBefore(Instant cutoff, int limit) {
        if (!props.isEnabled() || cutoff == null) return List.of();
        return gatekeeper.acquire(() -> doListTracesBefore(cutoff, limit));
    }

    private List<LangfuseTrace> doListTracesBefore(Instant cutoff, int limit) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("toTimestamp", cutoff.toString());
            params.put("page", 1);
            params.put("limit", Math.min(Math.max(limit, 1), 100));
            String response = queryWebClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/public/traces");
                        params.forEach(uriBuilder::queryParam);
                        return uriBuilder.build();
                    })
                    .header("Authorization", basicAuth(props.queryPublicKey(), props.querySecretKey()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));
            return parseTraces(response);
        } catch (Exception e) {
            log.warn("Langfuse expired trace list failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean deleteTrace(String langfuseTraceId) {
        if (!props.isEnabled() || langfuseTraceId == null) return false;
        try {
            queryWebClient.delete()
                    .uri("/api/public/traces/{id}", langfuseTraceId)
                    .header("Authorization", basicAuth(props.queryPublicKey(), props.querySecretKey()))
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(10));
            return true;
        } catch (Exception e) {
            log.warn("Langfuse trace delete failed: traceId={}, err={}", langfuseTraceId, e.getMessage());
            return false;
        }
    }

    private long parseTotal(String response, long fallback) {
        if (response == null) return fallback;
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode meta = root.path("meta");
            if (meta.hasNonNull("totalItems")) {
                return meta.get("totalItems").asLong();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    @Override
    public Optional<LangfuseTrace> getTrace(String langfuseTraceId) {
        return getTraceDetail(langfuseTraceId).map(LangfuseTraceDetail::trace);
    }

    @Override
    public Optional<LangfuseTraceDetail> getTraceDetail(String langfuseTraceId) {
        if (!props.isEnabled() || langfuseTraceId == null) return Optional.empty();
        return gatekeeper.acquire(() -> doGetTraceDetail(langfuseTraceId));
    }

    private Optional<LangfuseTraceDetail> doGetTraceDetail(String langfuseTraceId) {
        try {
            String response = queryWebClient.get()
                    .uri("/api/public/traces/" + langfuseTraceId)
                    .header("Authorization", basicAuth(props.queryPublicKey(), props.querySecretKey()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));
            return parseTraceDetail(response);
        } catch (Exception e) {
            log.warn("Langfuse trace detail get failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public LangfuseMetricsResult aggregateMetrics(MetricsQuery query) {
        // C21: 先看本地缓存（Gatekeeper 内部带缓存）
        return gatekeeper.queryWithCache(query, () -> computeMetrics(query));
    }

    /** 按 Langfuse 上限 100/页循环拉取，最多取 maxTotal 条（用于 metrics 聚合）。 */
    private List<LangfuseTrace> queryAllTraces(LangfuseTraceFilter filter, int maxTotal) {
        List<LangfuseTrace> all = new ArrayList<>();
        for (int page = 0; page < 50 && all.size() < maxTotal; page++) {
            Page<LangfuseTrace> result = doQueryTraces(filter, PageRequest.of(page, 100));
            List<LangfuseTrace> content = result.getContent();
            if (content.isEmpty()) {
                break;
            }
            all.addAll(content);
            if (!result.hasNext()) {
                break;
            }
        }
        return all.size() > maxTotal ? all.subList(0, maxTotal) : all;
    }

    /**
     * Trace 列表接口不返回 usage，Token 用量需从 /api/public/observations 聚合；
     * 仅统计 traceIds 命中的 GENERATION 观测，保证与 Trace 列表口径一致。
     */
    private long[] sumGenerationTokens(Instant from, Instant to, Set<String> traceIds) {
        long input = 0L;
        long output = 0L;
        if (traceIds == null || traceIds.isEmpty()) {
            return new long[]{input, output};
        }
        for (int page = 1; page <= 50; page++) {
            final int currentPage = page;
            try {
                String response = queryWebClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path("/api/public/observations")
                                    .queryParam("type", "GENERATION")
                                    .queryParam("limit", 100)
                                    .queryParam("page", currentPage);
                            if (from != null) uriBuilder.queryParam("fromStartTime", from.toString());
                            if (to != null) uriBuilder.queryParam("toStartTime", to.toString());
                            return uriBuilder.build();
                        })
                        .header("Authorization", basicAuth(props.queryPublicKey(), props.querySecretKey()))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(10));
                if (response == null) break;
                JsonNode data = objectMapper.readTree(response).get("data");
                if (data == null || !data.isArray() || data.isEmpty()) break;
                for (JsonNode node : data) {
                    String traceId = text(node, "traceId");
                    if (traceId == null || !traceIds.contains(traceId)) continue;
                    LangfuseObservation observation = parseObservationNode(node);
                    if (observation.inputTokens() != null) input += observation.inputTokens();
                    if (observation.outputTokens() != null) output += observation.outputTokens();
                }
                if (data.size() < 100) break;
            } catch (Exception e) {
                log.warn("Langfuse observations aggregate failed (page={}): {}", page, e.getMessage());
                break;
            }
        }
        return new long[]{input, output};
    }

    private LangfuseMetricsResult computeMetrics(MetricsQuery query) {
        List<LangfuseTrace> list = queryAllTraces(
                new LangfuseTraceFilter(query.targetId(), query.from(), query.to(),
                        null, null, null, null, null, null),
                5000);
        if (list.isEmpty()) {
            return emptyResult();
        }
        long[] latencies = list.stream()
                .mapToLong(t -> t.durationMs() != null ? t.durationMs() : 0L)
                .filter(d -> d > 0).toArray();
        Arrays.sort(latencies);

        long p50 = percentile(latencies, 50);
        long p95 = percentile(latencies, 95);
        long p99 = percentile(latencies, 99);
        double avg = latencies.length > 0
                ? Arrays.stream(latencies).average().orElse(0) : 0;
        double stdDev = computeStdDev(latencies, avg);

        Set<String> traceIds = list.stream()
                .map(LangfuseTrace::traceId).filter(Objects::nonNull).collect(Collectors.toSet());
        long[] tokens = sumGenerationTokens(query.from(), query.to(), traceIds);
        long inputTokens = tokens[0];
        long outputTokens = tokens[1];

        // time series（按 HOUR 分桶）
        Map<Long, long[]> buckets = new TreeMap<>();
        for (LangfuseTrace t : list) {
            if (t.startTime() == null) continue;
            long bucketStart = t.startTime().truncatedTo(ChronoUnit.HOURS).toEpochMilli();
            buckets.computeIfAbsent(bucketStart, k -> new long[2]);
            long[] slot = buckets.get(bucketStart);
            slot[0]++;
            slot[1] += t.durationMs() != null ? t.durationMs() : 0L;
        }
        List<LangfuseMetricsResult.TimeSeriesPoint> ts = new ArrayList<>();
        buckets.forEach((start, slot) -> ts.add(new LangfuseMetricsResult.TimeSeriesPoint(
                start, slot[0], slot[0] > 0 ? (double) slot[1] / slot[0] : 0)));

        return new LangfuseMetricsResult(
                new LangfuseMetricsResult.LatencyMetrics(p50, p95, p99, avg, stdDev),
                new LangfuseMetricsResult.TokenMetrics(
                        inputTokens, outputTokens,
                        list.size() > 0 ? (double) (inputTokens + outputTokens) / list.size() : 0,
                        list.size()),
                new LangfuseMetricsResult.AutoScoreMetrics(null, null, null, 0),
                ts);
    }

    @Override
    public boolean isAvailable() {
        if (!props.isEnabled()) return false;
        try {
            queryWebClient.get()
                    .uri("/api/public/traces?limit=1")
                    .header("Authorization", basicAuth(props.queryPublicKey(), props.querySecretKey()))
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(3));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ===== 辅助方法 =====

    private String basicAuth(String pubKey, String secretKey) {
        if (pubKey == null) pubKey = "";
        if (secretKey == null) secretKey = "";
        String raw = pubKey + ":" + secretKey;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes());
    }

    private Map<String, Object> buildTraceBody(TraceWriteRequest r) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", r.traceId());
        body.put("name", r.name());
        // 结束时的 upsert 不带 sessionId，此处必须跳过 null，避免把已有会话归属清空
        if (r.sessionId() != null) body.put("sessionId", r.sessionId());
        if (r.userId() != null) body.put("userId", r.userId());
        if (r.input() != null) body.put("input", r.input());
        if (r.output() != null) body.put("output", r.output());
        body.put("tags", r.tags());
        body.put("metadata", r.metadata());
        body.put("timestamp", r.timestamp() != null ? r.timestamp().toString() : Instant.now().toString());
        return body;
    }

    private Map<String, Object> buildObservationBody(ObservationWriteRequest r) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", r.observationId());
        body.put("traceId", r.traceId());
        body.put("parentObservationId", r.parentObservationId());
        body.put("name", r.name());
        body.put("input", r.input());
        body.put("output", r.output());
        body.put("startTime", r.startTime() != null ? r.startTime().toString() : Instant.now().toString());
        body.put("endTime", r.endTime() != null ? r.endTime().toString() : Instant.now().toString());
        Map<String, Object> usage = new HashMap<>();
        if (r.inputTokens() != null || r.outputTokens() != null || r.totalTokens() != null) {
            usage.put("input", r.inputTokens());
            usage.put("output", r.outputTokens());
            usage.put("total", r.totalTokens());
            body.put("usage", usage);
        }
        body.put("model", r.model());
        body.put("statusMessage", r.success() ? null : r.level());
        if (r.metadata() != null && !r.metadata().isEmpty()) {
            body.put("metadata", r.metadata());
        }
        return body;
    }

    private List<LangfuseTrace> parseTraces(String response) {
        if (response == null) return List.of();
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.has("data") ? root.get("data") : root;
            List<LangfuseTrace> result = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode node : data) {
                    result.add(parseTraceNode(node));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Langfuse trace parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private Optional<LangfuseTraceDetail> parseTraceDetail(String response) {
        if (response == null) return Optional.empty();
        try {
            JsonNode root = objectMapper.readTree(response);
            LangfuseTrace trace = parseTraceNode(root);

            List<LangfuseObservation> observations = new ArrayList<>();
            JsonNode observationsNode = root.get("observations");
            if (observationsNode != null && observationsNode.isArray()) {
                for (JsonNode node : observationsNode) {
                    observations.add(parseObservationNode(node));
                }
            }

            long inputSum = 0;
            long outputSum = 0;
            boolean hasError = false;
            for (LangfuseObservation observation : observations) {
                if (observation.inputTokens() != null) inputSum += observation.inputTokens();
                if (observation.outputTokens() != null) outputSum += observation.outputTokens();
                if ("ERROR".equalsIgnoreCase(observation.level())) hasError = true;
            }
            // Trace 头部未写入 input/output 时，用 Observation 回退推导（首个输入 / 最后一个输出）
            String derivedInput = trace.input();
            String derivedOutput = trace.output();
            if (derivedInput == null || derivedInput.isBlank()) {
                derivedInput = observations.stream()
                        .map(LangfuseObservation::input)
                        .filter(value -> value != null && !value.isBlank())
                        .findFirst().orElse(null);
            }
            if (derivedOutput == null || derivedOutput.isBlank()) {
                for (LangfuseObservation observation : observations) {
                    if (observation.output() != null && !observation.output().isBlank()) {
                        derivedOutput = observation.output();
                    }
                }
            }

            // 列表接口不返回 usage，详情里用 Observation 汇总补齐 Token；任一 Observation ERROR 视为失败
            LangfuseTrace enriched = new LangfuseTrace(
                    trace.traceId(), trace.name(), trace.targetId(), trace.sessionId(), trace.userId(),
                    derivedInput, derivedOutput,
                    trace.inputTokens() != null ? trace.inputTokens() : inputSum,
                    trace.outputTokens() != null ? trace.outputTokens() : outputSum,
                    trace.durationMs(), trace.startTime(), trace.endTime(),
                    trace.tags(), trace.success() && !hasError, trace.metadata());
            return Optional.of(new LangfuseTraceDetail(enriched, observations));
        } catch (Exception e) {
            log.warn("Langfuse trace detail parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private LangfuseObservation parseObservationNode(JsonNode n) {
        JsonNode usage = n.get("usageDetails");
        if (usage == null || !usage.isObject() || usage.isEmpty()) {
            usage = n.get("usage");
        }
        Long inputTokens = tokenValue(usage, "input");
        Long outputTokens = tokenValue(usage, "output");
        Long totalTokens = tokenValue(usage, "total");
        if (inputTokens == null) inputTokens = longVal(n.get("promptTokens"));
        if (outputTokens == null) outputTokens = longVal(n.get("completionTokens"));
        if (totalTokens == null) totalTokens = longVal(n.get("totalTokens"));

        Instant startTime = instant(n, "startTime");
        Instant endTime = instant(n, "endTime");
        Long latencyMs = null;
        JsonNode latencyNode = n.get("latency");
        if (latencyNode != null && latencyNode.isNumber()) {
            latencyMs = Math.round(latencyNode.asDouble() * 1000);
        } else if (startTime != null && endTime != null) {
            latencyMs = ChronoUnit.MILLIS.between(startTime, endTime);
        }

        JsonNode metadataNode = n.get("metadata");
        Map<String, Object> metadata = metadataNode != null && metadataNode.isObject()
                ? objectMapper.convertValue(metadataNode, Map.class) : Map.of();

        return new LangfuseObservation(
                text(n, "id"), text(n, "traceId"), text(n, "parentObservationId"),
                text(n, "type"), text(n, "name"), text(n, "model"),
                jsonValue(n.get("input")), jsonValue(n.get("output")),
                inputTokens, outputTokens, totalTokens,
                latencyMs, startTime, endTime,
                text(n, "level"), text(n, "statusMessage"), metadata);
    }

    private LangfuseTrace parseTraceNode(JsonNode n) {
        String traceId = text(n, "id");
        String name = text(n, "name");
        Instant startTime = instant(n, "timestamp");
        Instant endTime = instant(n, "endTime");
        long durationMs = 0;
        // Langfuse 列表接口给的是 latency（秒），不一定有 endTime
        JsonNode latencyNode = n.get("latency");
        if (latencyNode != null && !latencyNode.isNull()) {
            durationMs = Math.round(latencyNode.asDouble() * 1000);
        } else if (startTime != null && endTime != null) {
            durationMs = ChronoUnit.MILLIS.between(startTime, endTime);
        }
        List<String> tags = new ArrayList<>();
        JsonNode tagsNode = n.get("tags");
        if (tagsNode != null && tagsNode.isArray()) {
            tagsNode.forEach(t -> tags.add(t.asText()));
        }
        // targetId 从 tags 提取
        String targetId = tags.stream()
                .filter(t -> t.startsWith("target_id="))
                .map(t -> t.substring("target_id=".length()))
                .findFirst().orElse(name);
        boolean success = !tags.contains("error=true");
        JsonNode meta = n.get("metadata");
        Map<String, Object> metadata = meta != null && meta.isObject()
                ? objectMapper.convertValue(meta, Map.class) : Map.of();

        return new LangfuseTrace(traceId, name, targetId, text(n, "sessionId"),
                text(n, "userId"),
                jsonValue(n.get("input")), jsonValue(n.get("output")),
                longVal(n.get("inputTokens")), longVal(n.get("outputTokens")),
                durationMs, startTime, endTime, tags, success, metadata);
    }

    private String jsonValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return node.isValueNode() ? node.asText() : node.toString();
    }

    private static Long tokenValue(JsonNode usage, String key) {
        if (usage == null) return null;
        JsonNode value = usage.get(key);
        return value != null && value.isNumber() ? value.asLong() : null;
    }

    private String text(JsonNode n, String field) {
        JsonNode v = n.get(field); return v != null && !v.isNull() ? v.asText() : null;
    }
    private Instant instant(JsonNode n, String field) {
        String s = text(n, field);
        if (s == null) return null;
        try { return Instant.parse(s); } catch (Exception e) { return null; }
    }
    private Long longVal(JsonNode n) { return n != null && !n.isNull() ? n.asLong() : null; }

    private static long percentile(long[] sorted, int pct) {
        if (sorted.length == 0) return 0;
        int idx = (int) Math.ceil(pct / 100.0 * sorted.length) - 1;
        idx = Math.max(0, Math.min(sorted.length - 1, idx));
        return sorted[idx];
    }
    private static double computeStdDev(long[] data, double mean) {
        if (data.length < 2) return 0;
        double sum = 0;
        for (long v : data) sum += (v - mean) * (v - mean);
        return Math.sqrt(sum / data.length);
    }
    private static LangfuseMetricsResult emptyResult() {
        return new LangfuseMetricsResult(
                new LangfuseMetricsResult.LatencyMetrics(0, 0, 0, 0, 0),
                new LangfuseMetricsResult.TokenMetrics(0, 0, 0, 0),
                new LangfuseMetricsResult.AutoScoreMetrics(null, null, null, 0),
                List.of());
    }
}
