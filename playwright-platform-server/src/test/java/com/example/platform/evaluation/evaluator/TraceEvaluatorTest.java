package com.example.platform.evaluation.evaluator;

import com.example.platform.evaluation.common.EvaluatorType;
import com.example.platform.evaluation.langfuse.LangfuseObservation;
import com.example.platform.evaluation.langfuse.LangfuseTrace;
import com.example.platform.evaluation.langfuse.LangfuseTraceDetail;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TraceEvaluator 单测 —— 验证 span 数量/类型/名称/ERROR/延迟/Token 断言与部分得分。 */
class TraceEvaluatorTest {

    private final TraceEvaluator evaluator = new TraceEvaluator();

    @Test
    void shouldPassWhenAllAssertionsMatch() {
        LangfuseTraceDetail detail = detail(true, 1200,
                generation("gen", "llm", "gpt-test", 10, 20, 30, "DEFAULT"),
                span("tool", "tool:search", "DEFAULT"));

        EvaluatorScore score = evaluator.score("ok", "golden", context(detail), Map.of(
                "requiredTypes", List.of("GENERATION", "SPAN"),
                "requiredSpanNames", List.of("search"),
                "maxLatencyMs", 5000,
                "minTotalTokens", 10
        ));

        assertEquals(EvaluatorType.TRACE_ASSERTION, score.evaluatorType());
        assertEquals(100.0, score.score());
        assertTrue(score.reason().contains("全部 Trace 断言通过"));
        assertEquals(2, score.metadata().get("observationCount"));
    }

    @Test
    void shouldReturnPartialScoreWhenRequiredSpanMissing() {
        LangfuseTraceDetail detail = detail(true, 1000,
                generation("gen", "llm", "gpt-test", 10, 20, 30, "DEFAULT"));

        EvaluatorScore score = evaluator.score("ok", "golden", context(detail), Map.of(
                "requiredSpanNames", List.of("tool:search")
        ));

        assertTrue(score.score() < 100.0);
        assertTrue(score.reason().contains("缺少步骤 tool:search"));
    }

    @Test
    void shouldFailOnErrorObservation() {
        LangfuseTraceDetail detail = detail(true, 1000,
                generation("gen", "llm", "gpt-test", 10, 20, 30, "ERROR"));

        EvaluatorScore score = evaluator.score("ok", "golden", context(detail), Map.of(
                "maxErrorCount", 0
        ));

        assertTrue(score.score() < 100.0);
        assertTrue(score.reason().contains("ERROR 步骤数"));
    }

    @Test
    void shouldReturnZeroWhenAllOrNothingAndOneCheckFails() {
        LangfuseTraceDetail detail = detail(true, 1000,
                generation("gen", "llm", "gpt-test", 10, 20, 30, "ERROR"));

        EvaluatorScore score = evaluator.score("ok", "golden", context(detail), Map.of(
                "maxErrorCount", 0,
                "allOrNothing", true
        ));

        assertEquals(0.0, score.score());
    }

    @Test
    void shouldHandleMissingTrace() {
        EvaluatorScore strict = evaluator.score("ok", "golden", Map.of(), Map.of());
        assertEquals(0.0, strict.score());
        assertTrue(strict.reason().contains("Trace 未上报"));

        EvaluatorScore allowed = evaluator.score("ok", "golden", Map.of(),
                Map.of("allowMissingTrace", true));
        assertEquals(100.0, allowed.score());
        assertEquals(true, allowed.metadata().get("missingTrace"));
    }

    private static Map<String, Object> context(LangfuseTraceDetail detail) {
        return Map.of("traceDetail", detail);
    }

    private static LangfuseTraceDetail detail(boolean success, long durationMs,
                                              LangfuseObservation... observations) {
        LangfuseTrace trace = new LangfuseTrace(
                "t1", "trace-name", "7", "session-1", null,
                "{\"input\":\"hi\"}", "{\"output\":\"ok\"}", 10L, 20L,
                durationMs, Instant.now(), Instant.now(), List.of("target_id=7"), success, Map.of());
        return new LangfuseTraceDetail(trace, List.of(observations));
    }

    private static LangfuseObservation generation(String id, String name, String model,
                                                  long in, long out, long total, String level) {
        return new LangfuseObservation(id, "t1", null, "GENERATION", name, model,
                "{\"input\":\"hi\"}", "{\"output\":\"ok\"}", in, out, total, 500L,
                Instant.now(), Instant.now(), level, null, Map.of());
    }

    private static LangfuseObservation span(String id, String name, String level) {
        return new LangfuseObservation(id, "t1", null, "SPAN", name, null,
                "{\"input\":\"hi\"}", "{\"output\":\"ok\"}", null, null, null, 300L,
                Instant.now(), Instant.now(), level, null, Map.of());
    }
}
