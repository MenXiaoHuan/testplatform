package com.example.platform.evaluation.evaluator;

import com.example.platform.evaluation.common.EvaluatorType;
import com.example.platform.evaluation.langfuse.LangfuseObservation;
import com.example.platform.evaluation.langfuse.LangfuseTraceDetail;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Trace 断言评分器 —— 离线评测读取 Langfuse 完整链路后，对 span 树做单元/端到端断言。
 *
 * <p>支持的配置：
 * <pre>
 * {
 *   "minObservations": 1,              // 至少多少个步骤
 *   "maxObservations": 0,              // 0 = 不限制
 *   "requiredTypes": ["GENERATION"],   // 必须出现的 Observation 类型
 *   "requiredSpanNames": ["tool:search"], // 名称包含即可（忽略大小写）
 *   "forbiddenSpanNames": [],
 *   "maxErrorCount": 0,                // 允许的 ERROR 步骤数
 *   "maxLatencyMs": 0,                 // 0 = 不限制
 *   "minTotalTokens": 0,
 *   "maxTotalTokens": 0,               // 0 = 不限制
 *   "requireSuccess": true,            // Trace 必须成功
 *   "allOrNothing": false,             // true = 任一断言失败直接 0 分
 *   "allowMissingTrace": false         // true = Trace 未上报时按通过处理
 * }
 * </pre>
 */
@Component
public class TraceEvaluator implements Evaluator {

    @Override
    public EvaluatorType type() {
        return EvaluatorType.TRACE_ASSERTION;
    }

    @Override
    public EvaluatorScore score(String agentOutput, String goldenAnswer,
                                Map<String, Object> context, Map<String, Object> config) {
        Map<String, Object> cfg = config != null ? config : Map.of();
        Object rawDetail = context != null ? context.get("traceDetail") : null;

        if (!(rawDetail instanceof LangfuseTraceDetail detail)) {
            if (boolValue(cfg, "allowMissingTrace", false)) {
                return new EvaluatorScore(type(), 100.0,
                        "Trace 未上报，按 allowMissingTrace=true 忽略", Map.of("missingTrace", true));
            }
            return new EvaluatorScore(type(), 0.0,
                    "Trace 未上报或尚未被 Langfuse 处理完成", Map.of("missingTrace", true));
        }

        List<LangfuseObservation> observations =
                detail.observations() != null ? detail.observations() : List.of();
        List<Check> checks = new ArrayList<>();

        checks.add(new Check("minObservations", observations.size() >= intValue(cfg, "minObservations", 1),
                "步骤数 " + observations.size()));

        int maxObservations = intValue(cfg, "maxObservations", 0);
        if (maxObservations > 0) {
            checks.add(new Check("maxObservations", observations.size() <= maxObservations,
                    "步骤数 " + observations.size() + " > " + maxObservations));
        }

        if (boolValue(cfg, "requireSuccess", true)) {
            boolean traceSuccess = detail.trace().success();
            checks.add(new Check("requireSuccess", traceSuccess,
                    traceSuccess ? "Trace 成功" : "Trace 标记为失败"));
        }

        for (String requiredType : stringList(cfg.get("requiredTypes"))) {
            boolean found = observations.stream()
                    .anyMatch(o -> requiredType.equalsIgnoreCase(String.valueOf(o.type())));
            checks.add(new Check("requiredType:" + requiredType, found,
                    found ? "包含类型 " + requiredType : "缺少类型 " + requiredType));
        }

        for (String requiredName : stringList(cfg.get("requiredSpanNames"))) {
            boolean found = observations.stream()
                    .anyMatch(o -> nameMatches(o.name(), requiredName));
            checks.add(new Check("requiredSpan:" + requiredName, found,
                    found ? "包含步骤 " + requiredName : "缺少步骤 " + requiredName));
        }

        for (String forbiddenName : stringList(cfg.get("forbiddenSpanNames"))) {
            boolean found = observations.stream()
                    .anyMatch(o -> nameMatches(o.name(), forbiddenName));
            checks.add(new Check("forbiddenSpan:" + forbiddenName, !found,
                    found ? "出现了禁止的步骤 " + forbiddenName : "未出现禁止步骤 " + forbiddenName));
        }

        int errorCount = (int) observations.stream()
                .filter(o -> "ERROR".equalsIgnoreCase(String.valueOf(o.level())))
                .count();
        int maxErrorCount = intValue(cfg, "maxErrorCount", 0);
        checks.add(new Check("maxErrorCount", errorCount <= maxErrorCount,
                errorCount <= maxErrorCount
                        ? "ERROR 步骤数 " + errorCount + "（上限 " + maxErrorCount + "）"
                        : "ERROR 步骤数 " + errorCount + " > " + maxErrorCount));

        long maxLatencyMs = longValue(cfg, "maxLatencyMs", 0);
        if (maxLatencyMs > 0) {
            long latency = detail.trace().durationMs() != null ? detail.trace().durationMs() : 0L;
            checks.add(new Check("maxLatencyMs", latency <= maxLatencyMs,
                    latency <= maxLatencyMs
                            ? "延迟 " + latency + "ms（上限 " + maxLatencyMs + "ms）"
                            : "延迟 " + latency + "ms > " + maxLatencyMs + "ms"));
        }

        long totalTokens = totalTokens(observations);
        long minTotalTokens = longValue(cfg, "minTotalTokens", 0);
        if (minTotalTokens > 0) {
            checks.add(new Check("minTotalTokens", totalTokens >= minTotalTokens,
                    totalTokens >= minTotalTokens
                            ? "Token " + totalTokens + "（下限 " + minTotalTokens + "）"
                            : "Token " + totalTokens + " < " + minTotalTokens));
        }
        long maxTotalTokens = longValue(cfg, "maxTotalTokens", 0);
        if (maxTotalTokens > 0) {
            checks.add(new Check("maxTotalTokens", totalTokens <= maxTotalTokens,
                    totalTokens <= maxTotalTokens
                            ? "Token " + totalTokens + "（上限 " + maxTotalTokens + "）"
                            : "Token " + totalTokens + " > " + maxTotalTokens));
        }

        long passed = checks.stream().filter(Check::passed).count();
        boolean allPassed = passed == checks.size();
        boolean allOrNothing = boolValue(cfg, "allOrNothing", false);
        double score = allOrNothing ? (allPassed ? 100.0 : 0.0)
                : Math.round(1000.0 * passed / checks.size()) / 10.0;

        List<String> failedDetails = checks.stream()
                .filter(check -> !check.passed())
                .map(Check::detail)
                .toList();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("traceId", detail.trace().traceId());
        metadata.put("observationCount", observations.size());
        metadata.put("errorCount", errorCount);
        metadata.put("totalTokens", totalTokens);
        metadata.put("checks", checks.stream().map(Check::toMap).toList());

        String reason = allPassed
                ? "全部 Trace 断言通过（" + checks.size() + " 项）"
                : "未通过断言: " + String.join("；", failedDetails);

        return new EvaluatorScore(type(), score, reason, metadata);
    }

    private static boolean nameMatches(String name, String expected) {
        if (name == null || expected == null) return false;
        return name.toLowerCase(Locale.ROOT).contains(expected.trim().toLowerCase(Locale.ROOT));
    }

    private static long totalTokens(List<LangfuseObservation> observations) {
        long total = 0;
        for (LangfuseObservation observation : observations) {
            if (observation.totalTokens() != null) {
                total += observation.totalTokens();
            } else {
                if (observation.inputTokens() != null) total += observation.inputTokens();
                if (observation.outputTokens() != null) total += observation.outputTokens();
            }
        }
        return total;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(s -> !s.isBlank()).toList();
        }
        if (raw instanceof String text && !text.isBlank()) {
            return List.of(text.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        return List.of();
    }

    private static int intValue(Map<String, Object> cfg, String key, int defaultValue) {
        Object value = cfg.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value != null) {
            try { return Integer.parseInt(value.toString().trim()); } catch (Exception ignored) { }
        }
        return defaultValue;
    }

    private static long longValue(Map<String, Object> cfg, String key, long defaultValue) {
        Object value = cfg.get(key);
        if (value instanceof Number number) return number.longValue();
        if (value != null) {
            try { return Long.parseLong(value.toString().trim()); } catch (Exception ignored) { }
        }
        return defaultValue;
    }

    private static boolean boolValue(Map<String, Object> cfg, String key, boolean defaultValue) {
        Object value = cfg.get(key);
        if (value instanceof Boolean bool) return bool;
        if (value != null && "true".equalsIgnoreCase(value.toString().trim())) return true;
        if (value != null && "false".equalsIgnoreCase(value.toString().trim())) return false;
        return defaultValue;
    }

    private record Check(String name, boolean passed, String detail) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", name);
            map.put("passed", passed);
            map.put("detail", detail);
            return map;
        }
    }
}
