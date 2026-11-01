package com.example.platform.evaluation.offline;

import com.example.platform.evaluation.common.EvaluatorType;
import com.example.platform.evaluation.evaluator.Evaluator;
import com.example.platform.evaluation.evaluator.EvaluatorRegistry;
import com.example.platform.evaluation.evaluator.EvaluatorScore;
import com.example.platform.evaluation.langfuse.LangfuseClient;
import com.example.platform.evaluation.langfuse.LangfuseTraceDetail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 离线评测的评分执行器：
 * <ul>
 *   <li>解析 Run 的 evaluatorConfigs JSON</li>
 *   <li>按 type 路由到 {@link EvaluatorRegistry} 中的评分器</li>
 *   <li>若包含 TRACE_ASSERTION / TRACE_SNAPSHOT，则轮询 Langfuse 拉取 Trace 详情后注入 Evaluator context</li>
 *   <li>归一化各评分器分制并计算加权分</li>
 * </ul>
 */
@Service
public class OfflineEvaluatorService {

    private static final Logger log = LoggerFactory.getLogger(OfflineEvaluatorService.class);
    private static final long TRACE_FETCH_TIMEOUT_MS = 6000;
    private static final long TRACE_FETCH_INTERVAL_MS = 500;

    private final EvaluatorRegistry registry;
    private final LangfuseClient langfuse;
    private final ObjectMapper objectMapper;
    /** 快照基线 Trace 缓存 —— baselineTraceId 在同一个 Run 的所有样本间复用。 */
    private final Cache<String, LangfuseTraceDetail> baselineCache = Caffeine.newBuilder()
            .maximumSize(256)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    public OfflineEvaluatorService(EvaluatorRegistry registry, LangfuseClient langfuse,
                                   ObjectMapper objectMapper) {
        this.registry = registry;
        this.langfuse = langfuse;
        this.objectMapper = objectMapper;
    }

    public record EvaluatorSpec(EvaluatorType type, Map<String, Object> config, double weight) {}

    public record EvaluationOutcome(String evaluatorScoresJson, Double weightedScore) {}

    /** 样本关键词存为 JSON 数组；兼容逗号分隔的纯文本。 */
    private List<String> parseKeywordList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.isArray()) {
                List<String> list = new ArrayList<>();
                for (JsonNode item : node) {
                    String value = item.asText("").trim();
                    if (!value.isEmpty()) list.add(value);
                }
                return list;
            }
        } catch (Exception ignored) {
            // 非 JSON，按分隔符继续解析
        }
        List<String> list = new ArrayList<>();
        for (String part : raw.split("[,，、\\s]+")) {
            if (!part.isBlank()) list.add(part.trim());
        }
        return list;
    }

    /** 解析 evaluatorConfigs；非法配置跳过并记录日志，不影响整个 Run。 */
    public List<EvaluatorSpec> parseSpecs(String evaluatorConfigsJson) {
        if (evaluatorConfigsJson == null || evaluatorConfigsJson.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(evaluatorConfigsJson);
            if (!root.isArray()) {
                log.warn("evaluatorConfigs 不是 JSON 数组，忽略: {}", evaluatorConfigsJson);
                return List.of();
            }
            List<EvaluatorSpec> specs = new ArrayList<>();
            for (JsonNode node : root) {
                String rawType = node.path("type").asText("");
                if (rawType.isBlank()) continue;
                try {
                    EvaluatorType type = EvaluatorType.valueOf(rawType.trim().toUpperCase());
                    Map<String, Object> config = node.has("config") && node.get("config").isObject()
                            ? objectMapper.convertValue(node.get("config"), Map.class) : Map.of();
                    double weight = node.path("weight").asDouble(1.0);
                    if (weight <= 0) weight = 1.0;
                    specs.add(new EvaluatorSpec(type, config, weight));
                } catch (IllegalArgumentException e) {
                    log.warn("跳过未知 EvaluatorType: {}", rawType);
                }
            }
            return specs;
        } catch (Exception e) {
            log.warn("evaluatorConfigs 解析失败，忽略评分: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * @param localTraceDetail 平台刚写入的本地 Trace 视图（含边界 observation），
     *                         可让 Trace 断言立即执行，无需等待 Langfuse 异步处理；
     *                         当本地断言失败/缺失时，再轮询 Langfuse 拉取外部 Agent 的完整链路重试。
     */
    public EvaluationOutcome evaluate(List<EvaluatorSpec> specs, String originalInput, String agentOutput,
                                      String goldenAnswer, String sampleKeywords, String referenceContext,
                                      String metadataJson, String traceId,
                                      LangfuseTraceDetail localTraceDetail) {
        if (specs == null || specs.isEmpty()) {
            return new EvaluationOutcome("[]", null);
        }

        Map<String, Object> baseContext = new HashMap<>();
        if (traceId != null && !traceId.isBlank()) baseContext.put("langfuseTraceId", traceId);
        if (originalInput != null && !originalInput.isBlank()) baseContext.put("originalInput", originalInput);
        if (referenceContext != null) baseContext.put("referenceContext", referenceContext);
        List<String> keywords = parseKeywordList(sampleKeywords);
        if (!keywords.isEmpty()) baseContext.put("sampleKeywords", keywords);
        if (metadataJson != null && !metadataJson.isBlank()) {
            try {
                baseContext.put("metadata", objectMapper.readValue(metadataJson, Map.class));
            } catch (Exception ignored) {
                baseContext.put("metadata", metadataJson);
            }
        }

        List<Map<String, Object>> scoreEntries = new ArrayList<>();
        double weightedSum = 0;
        double weightSum = 0;
        LangfuseTraceDetail remoteDetail = null;
        boolean remoteAttempted = false;
        boolean remoteUsed = false;

        for (EvaluatorSpec spec : specs) {
            Evaluator evaluator = registry.find(spec.type()).orElse(null);
            boolean traceSpec = usesTraceDetail(spec.type());
            LangfuseTraceDetail baselineDetail = spec.type() == EvaluatorType.TRACE_SNAPSHOT
                    ? resolveBaseline(spec.config()) : null;
            Map<String, Object> context = baseContext;

            if (traceSpec) {
                if (remoteUsed && hasObservations(remoteDetail)) {
                    context = withTraceDetail(baseContext, remoteDetail);
                } else if (hasObservations(localTraceDetail)) {
                    context = withTraceDetail(baseContext, localTraceDetail);
                } else if (traceId != null && !traceId.isBlank() && !remoteAttempted) {
                    remoteAttempted = true;
                    remoteDetail = fetchTraceDetail(traceId);
                    if (hasObservations(remoteDetail)) {
                        context = withTraceDetail(baseContext, remoteDetail);
                        remoteUsed = true;
                    }
                }
            }

            if (baselineDetail != null) {
                context = withBaseline(context, baselineDetail);
            }

            EvaluatorScore score = safeScore(evaluator, spec, agentOutput, goldenAnswer, context);

            // 本地/首次断言未通过时，给 SDK 异步上报留时间，再用 Langfuse 远端完整链路重试一次
            if (traceSpec && !remoteUsed && score.score() != null && score.score() < 100
                    && traceId != null && !traceId.isBlank()) {
                if (!remoteAttempted) {
                    remoteAttempted = true;
                    remoteDetail = fetchTraceDetail(traceId);
                }
                if (hasObservations(remoteDetail)) {
                    remoteUsed = true;
                    context = withTraceDetail(baseContext, remoteDetail);
                    if (baselineDetail != null) context = withBaseline(context, baselineDetail);
                    score = safeScore(evaluator, spec, agentOutput, goldenAnswer, context);
                }
            }

            double normalized = normalize(score.score(), spec.type());
            weightedSum += normalized * spec.weight();
            weightSum += spec.weight();

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", spec.type().name());
            entry.put("score", score.score());
            entry.put("normalizedScore", normalized);
            entry.put("weight", spec.weight());
            entry.put("reason", score.reason());
            entry.put("metadata", score.metadata());
            if (traceSpec) {
                entry.put("traceFetched", remoteUsed);
            }
            scoreEntries.add(entry);
        }

        Double weightedScore = weightSum > 0
                ? Math.round(10.0 * weightedSum / weightSum) / 10.0 : null;

        try {
            return new EvaluationOutcome(objectMapper.writeValueAsString(scoreEntries), weightedScore);
        } catch (Exception e) {
            log.warn("评分结果序列化失败: {}", e.getMessage());
            return new EvaluationOutcome("[]", weightedScore);
        }
    }

    private static Map<String, Object> withTraceDetail(Map<String, Object> baseContext,
                                                       LangfuseTraceDetail detail) {
        Map<String, Object> context = new HashMap<>(baseContext);
        context.put("traceDetail", detail);
        return context;
    }

    /** 快照基线：优先使用内联 goldenSpans；否则按 baselineTraceId 从 Langfuse 拉取并缓存。 */
    private LangfuseTraceDetail resolveBaseline(Map<String, Object> config) {
        if (config == null) return null;
        Object inlineGolden = config.get("goldenSpans");
        if (inlineGolden instanceof List<?> list && !list.isEmpty()) return null;
        Object rawBaselineId = config.get("baselineTraceId");
        if (rawBaselineId == null || rawBaselineId.toString().isBlank()) return null;
        String baselineTraceId = rawBaselineId.toString().trim();
        try {
            return baselineCache.get(baselineTraceId,
                    key -> langfuse.getTraceDetail(key).orElse(null));
        } catch (Exception e) {
            log.warn("Baseline trace fetch failed: {} ({})", baselineTraceId, e.getMessage());
            return null;
        }
    }

    private static Map<String, Object> withBaseline(Map<String, Object> context,
                                                    LangfuseTraceDetail baselineDetail) {
        Map<String, Object> enriched = new HashMap<>(context);
        enriched.put("baselineTraceDetail", baselineDetail);
        return enriched;
    }

    private static boolean usesTraceDetail(EvaluatorType type) {
        return type == EvaluatorType.TRACE_ASSERTION || type == EvaluatorType.TRACE_SNAPSHOT;
    }

    private static boolean hasObservations(LangfuseTraceDetail detail) {
        return detail != null && detail.observations() != null && !detail.observations().isEmpty();
    }

    private EvaluatorScore safeScore(Evaluator evaluator, EvaluatorSpec spec, String agentOutput,
                                     String goldenAnswer, Map<String, Object> context) {
        if (evaluator == null) {
            return new EvaluatorScore(spec.type(), 0.0,
                    "未找到 Evaluator 实现: " + spec.type(), Map.of());
        }
        try {
            return evaluator.score(agentOutput, goldenAnswer, context, spec.config());
        } catch (Exception e) {
            log.warn("Evaluator {} 执行失败: {}", spec.type(), e.getMessage());
            return new EvaluatorScore(spec.type(), 0.0,
                    "执行失败: " + e.getMessage(), Map.of());
        }
    }

    /**
     * SDK 写入 Langfuse 是异步的，最多等待 {@link #TRACE_FETCH_TIMEOUT_MS}；
     * 找到 Trace 但 observation 仍为空时继续等待，超时后返回最后找到的 Trace。
     */
    private LangfuseTraceDetail fetchTraceDetail(String traceId) {
        long deadline = System.currentTimeMillis() + TRACE_FETCH_TIMEOUT_MS;
        LangfuseTraceDetail lastFound = null;
        while (System.currentTimeMillis() < deadline) {
            Optional<LangfuseTraceDetail> detail = langfuse.getTraceDetail(traceId);
            if (detail.isPresent()) {
                lastFound = detail.get();
                if (hasObservations(lastFound)) {
                    return lastFound;
                }
            }
            try {
                Thread.sleep(TRACE_FETCH_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return lastFound;
    }

    /** 统一到 0-100 分制；ExactMatch / JsonSchema / Similarity 返回 0-1，需要乘 100。 */
    private static double normalize(Double rawScore, EvaluatorType type) {
        if (rawScore == null) return 0;
        double score = rawScore;
        if (type == EvaluatorType.EXACT_MATCH || type == EvaluatorType.JSON_SCHEMA
                || type == EvaluatorType.SIMILARITY) {
            if (score <= 1.0) score *= 100;
        }
        score = Math.max(0, Math.min(100, score));
        return Math.round(score * 100) / 100.0;
    }
}
