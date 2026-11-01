package com.example.platform.evaluation.evaluator;

import com.example.platform.evaluation.common.EvaluatorType;
import com.example.platform.evaluation.langfuse.LangfuseObservation;
import com.example.platform.evaluation.langfuse.LangfuseTraceDetail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Trace 快照对比评分器 —— 与 golden span 序列比较，忽略 traceId / observationId / 时间戳等易变字段。
 *
 * <p>配置示例：
 * <pre>
 * {
 *   "goldenSpans": [
 *     {"name":"agent-root","type":"SPAN","children":[
 *       {"name":"retrieve","type":"SPAN"},
 *       {"name":"llm","type":"GENERATION"}
 *     ]}
 *   ],
 *   // 也可以用历史 Trace 作为基线：{"baselineTraceId": "..."}（与 goldenSpans 二选一）
 *   "ignoreSpanNames": ["heartbeat"],
 *   "allowExtraSpans": false,
 *   "allOrNothing": false,
 *   "allowMissingTrace": false
 * }
 * </pre>
 *
 * <p>对比规则：把整棵 span 树按父子关系 + startTime 拍平成序列，逐位置比较
 * name（包含匹配、忽略大小写）与 type；允许额外 span 时可忽略多出的步骤。
 */
@Component
public class TraceSnapshotEvaluator implements Evaluator {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public EvaluatorType type() {
        return EvaluatorType.TRACE_SNAPSHOT;
    }

    @Override
    public EvaluatorScore score(String agentOutput, String goldenAnswer,
                                Map<String, Object> context, Map<String, Object> config) {
        Map<String, Object> cfg = config != null ? config : Map.of();
        List<GoldenSpan> expected = parseGoldenSpans(cfg.get("goldenSpans"));
        String baselineTraceId = null;
        if (expected.isEmpty() && context != null) {
            Object rawBaseline = context.get("baselineTraceDetail");
            if (rawBaseline instanceof LangfuseTraceDetail baseline
                    && baseline.observations() != null && !baseline.observations().isEmpty()) {
                expected = flattenActual(baseline.observations(), List.of()).stream()
                        .map(ref -> new GoldenSpan(ref.name(), ref.type()))
                        .toList();
                baselineTraceId = baseline.trace().traceId();
            }
        }
        if (expected.isEmpty()) {
            return new EvaluatorScore(type(), 0.0,
                    "未配置 goldenSpans，且未能按 baselineTraceId 获取基线 Trace", Map.of());
        }

        Object rawDetail = context != null ? context.get("traceDetail") : null;
        if (!(rawDetail instanceof LangfuseTraceDetail detail)) {
            if (boolValue(cfg, "allowMissingTrace", false)) {
                return new EvaluatorScore(type(), 100.0,
                        "Trace 未上报，按 allowMissingTrace=true 忽略", Map.of("missingTrace", true));
            }
            return new EvaluatorScore(type(), 0.0,
                    "Trace 未上报或尚未被 Langfuse 处理完成", Map.of("missingTrace", true));
        }

        if (boolValue(cfg, "requireSuccess", false) && !detail.trace().success()) {
            return new EvaluatorScore(type(), 0.0, "Trace 标记为失败", Map.of("traceSuccess", false));
        }

        List<SpanRef> actual = flattenActual(detail.observations(), stringList(cfg.get("ignoreSpanNames")));
        boolean allowExtra = boolValue(cfg, "allowExtraSpans", false);

        int compareLength = Math.min(expected.size(), actual.size());
        int matched = 0;
        List<Map<String, Object>> mismatches = new ArrayList<>();
        for (int i = 0; i < compareLength; i++) {
            GoldenSpan expectedSpan = expected.get(i);
            SpanRef actualSpan = actual.get(i);
            if (matches(expectedSpan, actualSpan)) {
                matched++;
            } else {
                mismatches.add(mismatch(i, expectedSpan, actualSpan));
            }
        }
        for (int i = compareLength; i < expected.size(); i++) {
            mismatches.add(mismatch(i, expected.get(i), null));
        }
        for (int i = compareLength; i < actual.size(); i++) {
            mismatches.add(mismatch(i, null, actual.get(i)));
        }

        boolean lengthOk = allowExtra
                ? actual.size() >= expected.size()
                : actual.size() == expected.size();
        boolean allPassed = lengthOk && mismatches.isEmpty();

        double denominator = allowExtra ? expected.size() : Math.max(expected.size(), actual.size());
        double score = denominator > 0 ? Math.round(1000.0 * matched / denominator) / 10.0 : 0.0;
        if (boolValue(cfg, "allOrNothing", false)) {
            score = allPassed ? 100.0 : 0.0;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("traceId", detail.trace().traceId());
        metadata.put("expectedCount", expected.size());
        metadata.put("actualCount", actual.size());
        metadata.put("matched", matched);
        metadata.put("expectedSpans", expected.stream().map(GoldenSpan::label).toList());
        metadata.put("actualSpans", actual.stream().map(SpanRef::label).toList());
        metadata.put("mismatches", mismatches);
        metadata.put("allowExtraSpans", allowExtra);
        if (baselineTraceId != null) {
            metadata.put("baselineTraceId", baselineTraceId);
        }

        String reason = allPassed
                ? "快照完全匹配（" + expected.size() + " 个步骤）"
                : "快照差异 " + mismatches.size() + " 处，匹配 " + matched + "/" + expected.size();

        return new EvaluatorScore(type(), score, reason, metadata);
    }

    private static boolean matches(GoldenSpan expected, SpanRef actual) {
        if (actual == null) return false;
        if (expected.name() != null && !expected.name().isBlank()) {
            if (actual.name() == null
                    || !actual.name().toLowerCase(Locale.ROOT)
                    .contains(expected.name().trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return expected.type() == null || expected.type().isBlank()
                || expected.type().equalsIgnoreCase(String.valueOf(actual.type()));
    }

    private static Map<String, Object> mismatch(int index, GoldenSpan expected, SpanRef actual) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("index", index);
        map.put("expected", expected != null ? expected.label() : null);
        map.put("actual", actual != null ? actual.label() : null);
        return map;
    }

    /** 把 Trace observations 按父子关系拍平成 preorder 序列。 */
    private static List<SpanRef> flattenActual(List<LangfuseObservation> observations, List<String> ignoreNames) {
        if (observations == null || observations.isEmpty()) return List.of();

        Map<String, Node> nodes = new HashMap<>();
        for (LangfuseObservation observation : observations) {
            if (ignored(observation.name(), ignoreNames)) continue;
            nodes.put(observation.observationId(),
                    new Node(observation.observationId(), observation.parentObservationId(),
                            observation.name(), observation.type(), observation.startTime()));
        }
        List<Node> roots = new ArrayList<>();
        for (Node node : nodes.values()) {
            Node parent = node.parentObservationId() != null ? nodes.get(node.parentObservationId()) : null;
            if (parent != null) parent.children().add(node);
            else roots.add(node);
        }
        Comparator<Node> byStart = Comparator.comparing(node ->
                node.startTime() != null ? node.startTime() : java.time.Instant.EPOCH);
        roots.sort(byStart);
        roots.forEach(root -> root.children().sort(byStart));

        List<SpanRef> result = new ArrayList<>();
        for (Node root : roots) dfs(root, result);
        return result;
    }

    private static void dfs(Node node, List<SpanRef> result) {
        result.add(new SpanRef(node.name(), node.type()));
        node.children().forEach(child -> dfs(child, result));
    }

    private static boolean ignored(String name, List<String> ignoreNames) {
        if (name == null || ignoreNames.isEmpty()) return false;
        return ignoreNames.stream().anyMatch(ignore ->
                name.toLowerCase(Locale.ROOT).contains(ignore.toLowerCase(Locale.ROOT)));
    }

    /** 解析 goldenSpans；支持字符串简写与 children 嵌套。 */
    private static List<GoldenSpan> parseGoldenSpans(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        try {
            JsonNode node = JSON.valueToTree(raw);
            List<GoldenSpan> result = new ArrayList<>();
            for (JsonNode item : node) {
                parseGoldenNode(item, result);
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static void parseGoldenNode(JsonNode node, List<GoldenSpan> result) {
        if (node.isTextual()) {
            result.add(new GoldenSpan(node.asText(), null));
            return;
        }
        if (!node.isObject()) return;
        String name = node.path("name").asText(null);
        String type = node.hasNonNull("type") ? node.get("type").asText() : null;
        result.add(new GoldenSpan(name, type));
        JsonNode children = node.get("children");
        if (children != null && children.isArray()) {
            for (JsonNode child : children) parseGoldenNode(child, result);
        }
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

    private static boolean boolValue(Map<String, Object> cfg, String key, boolean defaultValue) {
        Object value = cfg.get(key);
        if (value instanceof Boolean bool) return bool;
        if (value != null && "true".equalsIgnoreCase(value.toString().trim())) return true;
        if (value != null && "false".equalsIgnoreCase(value.toString().trim())) return false;
        return defaultValue;
    }

    private record GoldenSpan(String name, String type) {
        String label() {
            return name == null ? "(unnamed)" : name;
        }
    }

    private record SpanRef(String name, String type) {
        String label() {
            return (name == null ? "(unnamed)" : name)
                    + (type != null ? "[" + type + "]" : "");
        }
    }

    private static final class Node {
        private final String id;
        private final String parentObservationId;
        private final String name;
        private final String type;
        private final java.time.Instant startTime;
        private final List<Node> children = new ArrayList<>();

        private Node(String id, String parentObservationId, String name, String type,
                     java.time.Instant startTime) {
            this.id = id;
            this.parentObservationId = parentObservationId;
            this.name = name;
            this.type = type;
            this.startTime = startTime;
        }

        String id() { return id; }
        String parentObservationId() { return parentObservationId; }
        String name() { return name; }
        String type() { return type; }
        java.time.Instant startTime() { return startTime; }
        List<Node> children() { return children; }
    }
}
