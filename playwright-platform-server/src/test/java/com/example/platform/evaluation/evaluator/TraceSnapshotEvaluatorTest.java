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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TraceSnapshotEvaluator 单测 —— golden span 序列对比、额外 span 策略与缺失 Trace。 */
class TraceSnapshotEvaluatorTest {

    private final TraceSnapshotEvaluator evaluator = new TraceSnapshotEvaluator();

    @Test
    void shouldMatchNestedGoldenSpans() {
        LangfuseTraceDetail detail = detail(
                observation("root", null, "agent-root", "SPAN", 0),
                observation("retrieve", "root", "retrieve", "SPAN", 1),
                observation("llm", "root", "llm", "GENERATION", 2));

        EvaluatorScore score = evaluator.score("ok", "golden", context(detail), Map.of(
                "goldenSpans", List.of(Map.of(
                        "name", "agent-root",
                        "type", "SPAN",
                        "children", List.of(
                                Map.of("name", "retrieve", "type", "SPAN"),
                                Map.of("name", "llm", "type", "GENERATION")
                        )
                ))
        ));

        assertEquals(EvaluatorType.TRACE_SNAPSHOT, score.evaluatorType());
        assertEquals(100.0, score.score());
        assertTrue(score.reason().contains("完全匹配"));
        assertEquals(3, score.metadata().get("actualCount"));
    }

    @Test
    void shouldReportMismatchAndPartialScore() {
        LangfuseTraceDetail detail = detail(
                observation("root", null, "agent-root", "SPAN", 0),
                observation("llm", "root", "llm", "GENERATION", 1));

        EvaluatorScore score = evaluator.score("ok", "golden", context(detail), Map.of(
                "goldenSpans", List.of(
                        Map.of("name", "agent-root", "type", "SPAN"),
                        Map.of("name", "tool:search", "type", "SPAN"))
        ));

        assertTrue(score.score() < 100.0);
        assertFalse(((List<?>) score.metadata().get("mismatches")).isEmpty());
        assertTrue(score.reason().contains("快照差异"));
    }

    @Test
    void shouldAllowExtraSpansWhenConfigured() {
        LangfuseTraceDetail detail = detail(
                observation("root", null, "agent-root", "SPAN", 0),
                observation("debug", "root", "debug", "SPAN", 1),
                observation("llm", "root", "llm", "GENERATION", 2));

        EvaluatorScore strict = evaluator.score("ok", "golden", context(detail), Map.of(
                "goldenSpans", List.of(
                        Map.of("name", "agent-root"),
                        Map.of("name", "llm", "type", "GENERATION"))
        ));
        assertTrue(strict.score() < 100.0);

        EvaluatorScore lenient = evaluator.score("ok", "golden", context(detail), Map.of(
                "goldenSpans", List.of(
                        Map.of("name", "agent-root"),
                        Map.of("name", "debug")),
                "allowExtraSpans", true
        ));
        assertTrue(lenient.score() >= 50.0);
    }

    @Test
    void shouldUseBaselineTraceDetailWhenGoldenSpansMissing() {
        LangfuseTraceDetail baseline = detail(
                observation("root", null, "agent-root", "SPAN", 0),
                observation("retrieve", "root", "retrieve", "SPAN", 1),
                observation("llm", "root", "llm", "GENERATION", 2));
        LangfuseTraceDetail actual = detail(
                observation("a-root", null, "agent-root", "SPAN", 0),
                observation("a-retrieve", "a-root", "retrieve", "SPAN", 1),
                observation("a-llm", "a-root", "llm", "GENERATION", 2));

        EvaluatorScore score = evaluator.score("ok", "golden", Map.of(
                "traceDetail", actual,
                "baselineTraceDetail", baseline), Map.of());

        assertEquals(100.0, score.score());
        assertEquals("trace-1", score.metadata().get("baselineTraceId"));
        assertTrue(score.reason().contains("完全匹配"));
    }

    @Test
    void shouldHandleMissingTrace() {
        EvaluatorScore strict = evaluator.score("ok", "golden", Map.of(), Map.of(
                "goldenSpans", List.of("agent-root")));
        assertEquals(0.0, strict.score());

        EvaluatorScore allowed = evaluator.score("ok", "golden", Map.of(), Map.of(
                "goldenSpans", List.of("agent-root"),
                "allowMissingTrace", true));
        assertEquals(100.0, allowed.score());
    }

    private static Map<String, Object> context(LangfuseTraceDetail detail) {
        return Map.of("traceDetail", detail);
    }

    private static LangfuseTraceDetail detail(LangfuseObservation... observations) {
        LangfuseTrace trace = new LangfuseTrace(
                "trace-1", "snapshot", "7", "session", null, "input", "output",
                10L, 20L, 1000L, Instant.now(), Instant.now(),
                List.of("target_id=7"), true, Map.of());
        return new LangfuseTraceDetail(trace, List.of(observations));
    }

    private static LangfuseObservation observation(String id, String parentId, String name,
                                                    String type, int second) {
        Instant start = Instant.parse("2026-10-30T00:00:00Z").plusSeconds(second);
        return new LangfuseObservation(id, "trace-1", parentId, type, name, null,
                "input", "output", null, null, null, 100L,
                start, start.plusMillis(100), "DEFAULT", null, Map.of());
    }
}
