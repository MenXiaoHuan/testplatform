package com.example.platform.evaluation.offline;

import com.example.platform.evaluation.common.EvaluatorType;
import com.example.platform.evaluation.evaluator.Evaluator;
import com.example.platform.evaluation.evaluator.EvaluatorRegistry;
import com.example.platform.evaluation.evaluator.EvaluatorScore;
import com.example.platform.evaluation.evaluator.ExactMatchEvaluator;
import com.example.platform.evaluation.evaluator.RuleMatchEvaluator;
import com.example.platform.evaluation.langfuse.LangfuseClient;
import com.example.platform.evaluation.langfuse.LangfuseObservation;
import com.example.platform.evaluation.langfuse.LangfuseTrace;
import com.example.platform.evaluation.langfuse.LangfuseTraceDetail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** OfflineEvaluatorService 单测 —— 配置解析、0-1/0-100 分制归一化、加权分与 Trace 注入。 */
class OfflineEvaluatorServiceTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void shouldParseSpecsAndComputeWeightedScore() {
        OfflineEvaluatorService service = serviceWith(
                fakeEvaluator(EvaluatorType.EXACT_MATCH, 1.0),
                fakeEvaluator(EvaluatorType.RULE_MATCH, 80.0));

        List<OfflineEvaluatorService.EvaluatorSpec> specs = service.parseSpecs("""
                [
                  {"type":"EXACT_MATCH","weight":1},
                  {"type":"RULE_MATCH","weight":1,"config":{"keywords":["ok"]}}
                ]
                """);
        assertEquals(2, specs.size());
        assertEquals(EvaluatorType.RULE_MATCH, specs.get(1).type());

        OfflineEvaluatorService.EvaluationOutcome outcome =
                service.evaluate(specs, "question", "ok", "ok", null, null, null, "trace-1", null);

        // EXACT_MATCH 返回 1.0 → 归一化为 100；RULE_MATCH 返回 80；平均 90
        assertEquals(90.0, outcome.weightedScore());
        JsonNode scores = readJson(outcome.evaluatorScoresJson());
        assertEquals(100.0, scores.get(0).path("normalizedScore").asDouble());
        assertEquals(80.0, scores.get(1).path("normalizedScore").asDouble());
    }

    @Test
    void shouldFetchTraceDetailForTraceAssertion() {
        LangfuseTraceDetail detail = traceDetail();
        LangfuseClient langfuse = mock(LangfuseClient.class);
        when(langfuse.getTraceDetail("trace-1")).thenReturn(Optional.of(detail));

        AtomicReference<Map<String, Object>> capturedContext = new AtomicReference<>();
        Evaluator traceEvaluator = new Evaluator() {
            @Override public EvaluatorType type() { return EvaluatorType.TRACE_ASSERTION; }
            @Override public EvaluatorScore score(String agentOutput, String goldenAnswer,
                                                  Map<String, Object> context, Map<String, Object> config) {
                capturedContext.set(context);
                return new EvaluatorScore(type(), 100.0, "ok", Map.of());
            }
        };

        OfflineEvaluatorService service = new OfflineEvaluatorService(
                new EvaluatorRegistry(List.of(traceEvaluator)), langfuse, json);
        List<OfflineEvaluatorService.EvaluatorSpec> specs =
                service.parseSpecs("[{\"type\":\"TRACE_ASSERTION\",\"config\":{\"minObservations\":1}}]");

        OfflineEvaluatorService.EvaluationOutcome outcome =
                service.evaluate(specs, "question", "ok", "golden", null, null, null, "trace-1", null);

        assertEquals(100.0, outcome.weightedScore());
        assertTrue(capturedContext.get().containsKey("traceDetail"),
                "TRACE_ASSERTION evaluator should receive traceDetail");
        assertTrue(capturedContext.get().containsKey("originalInput"),
                "evaluator should receive sample original input");
        JsonNode scores = readJson(outcome.evaluatorScoresJson());
        assertTrue(scores.get(0).path("traceFetched").asBoolean());
    }

    @Test
    void shouldResolveBaselineTraceIdForSnapshot() {
        LangfuseClient langfuse = mock(LangfuseClient.class);
        when(langfuse.getTraceDetail("baseline-1")).thenReturn(Optional.of(traceDetail()));

        AtomicReference<Map<String, Object>> capturedContext = new AtomicReference<>();
        Evaluator snapshotEvaluator = new Evaluator() {
            @Override public EvaluatorType type() { return EvaluatorType.TRACE_SNAPSHOT; }
            @Override public EvaluatorScore score(String agentOutput, String goldenAnswer,
                                                  Map<String, Object> context, Map<String, Object> config) {
                capturedContext.set(context);
                return new EvaluatorScore(type(), 100.0, "ok", Map.of());
            }
        };

        OfflineEvaluatorService service = new OfflineEvaluatorService(
                new EvaluatorRegistry(List.of(snapshotEvaluator)), langfuse, json);
        List<OfflineEvaluatorService.EvaluatorSpec> specs =
                service.parseSpecs("[{\"type\":\"TRACE_SNAPSHOT\",\"config\":{\"baselineTraceId\":\"baseline-1\"}}]");

        OfflineEvaluatorService.EvaluationOutcome outcome = service.evaluate(
                specs, "question", "ok", "golden", null, null, null, "actual-1", traceDetail());

        assertEquals(100.0, outcome.weightedScore());
        assertTrue(capturedContext.get().containsKey("baselineTraceDetail"),
                "snapshot evaluator should receive baselineTraceDetail");
        assertEquals("trace-1",
                ((LangfuseTraceDetail) capturedContext.get().get("baselineTraceDetail")).trace().traceId());
    }

    @Test
    void shouldExtractAnswerBeforeExactMatch() {
        OfflineEvaluatorService service = serviceWith(new ExactMatchEvaluator());
        List<OfflineEvaluatorService.EvaluatorSpec> specs = service.parseSpecs("""
                [{"type":"EXACT_MATCH","config":{
                    "extractPattern":"(-?[0-9]+)",
                    "extractLast":true,
                    "goldenExtractPattern":"####[ ]*(-?[0-9]+)"
                }}]
                """);

        OfflineEvaluatorService.EvaluationOutcome outcome = service.evaluate(
                specs, null, "She sold 24 clips in May, so altogether 72 clips.", "reasoning ... #### 72",
                null, null, null, null, null);

        assertEquals(100.0, outcome.weightedScore());
    }

    @Test
    void shouldPreferSampleKeywordsForRuleMatch() {
        OfflineEvaluatorService service = serviceWith(new RuleMatchEvaluator());
        List<OfflineEvaluatorService.EvaluatorSpec> specs = service.parseSpecs(
                "[{\"type\":\"RULE_MATCH\",\"config\":{\"keywords\":[\"成功\"]}}]");

        // 样本关键词优先：输出未包含全局关键词「成功」，但命中样本关键词「已发货」
        assertEquals(100.0, service.evaluate(
                specs, null, "订单已发货，预计 3 天送达", null, "[\"已发货\"]",
                null, null, null, null).weightedScore());

        // 样本无关键词时回退到评估器配置
        assertEquals(100.0, service.evaluate(
                specs, null, "操作成功", null,
                null, null, null, null, null).weightedScore());
    }

    private OfflineEvaluatorService serviceWith(Evaluator... evaluators) {
        return new OfflineEvaluatorService(
                new EvaluatorRegistry(List.of(evaluators)), mock(LangfuseClient.class), json);
    }

    private static Evaluator fakeEvaluator(EvaluatorType type, double score) {
        return new Evaluator() {
            @Override public EvaluatorType type() { return type; }
            @Override public EvaluatorScore score(String agentOutput, String goldenAnswer,
                                                  Map<String, Object> context, Map<String, Object> config) {
                return new EvaluatorScore(type, score, "fake", Map.of());
            }
        };
    }

    private JsonNode readJson(String raw) {
        try {
            return json.readTree(raw);
        } catch (Exception e) {
            throw new AssertionError("invalid json: " + raw, e);
        }
    }

    private static LangfuseTraceDetail traceDetail() {
        LangfuseTrace trace = new LangfuseTrace(
                "trace-1", "name", "7", "session", null, "input", "output", 10L, 20L,
                1000L, Instant.now(), Instant.now(), List.of("target_id=7"), true, Map.of());
        LangfuseObservation observation = new LangfuseObservation(
                "obs-1", "trace-1", null, "GENERATION", "llm", "gpt-test",
                "input", "output", 10L, 20L, 30L, 900L,
                Instant.now(), Instant.now(), "DEFAULT", null, Map.of());
        return new LangfuseTraceDetail(trace, List.of(observation));
    }
}
