package com.example.platform.evaluation.langfuse;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** queryTraces 本地过滤逻辑单测（Langfuse 服务端 tag 过滤的兜底）。 */
class LangfuseTraceFilterMatcherTest {

    private static LangfuseTrace trace(String targetId, List<String> tags, String sessionId,
                                       String name, String input, String output) {
        Instant now = Instant.now();
        return new LangfuseTrace("trace-" + targetId, name, targetId, sessionId, null,
                input, output, 10L, 20L, 100L, now, now, tags, true, Map.of());
    }

    @Test
    void shouldMatchTargetByTag() {
        LangfuseTrace trace = trace("1", List.of("target_id=1"), "s1", "chat", "hi", "hello");
        LangfuseTraceFilter filter = LangfuseTraceFilter.builder().targetId("1").build();

        assertTrue(LangfuseTraceFilterMatcher.matches(trace, filter));
    }

    @Test
    void shouldRejectOtherTarget() {
        LangfuseTrace trace = trace("2", List.of("target_id=2"), "s1", "chat", "hi", "hello");
        LangfuseTraceFilter filter = LangfuseTraceFilter.builder().targetId("1").build();

        assertFalse(LangfuseTraceFilterMatcher.matches(trace, filter));
    }

    @Test
    void shouldRequireAllTags() {
        LangfuseTrace trace = trace("1", List.of("target_id=1", "env=prod"), "s1", "chat", "hi", "hello");

        assertTrue(LangfuseTraceFilterMatcher.matches(trace,
                LangfuseTraceFilter.builder().tags(List.of("env=prod")).build()));
        assertFalse(LangfuseTraceFilterMatcher.matches(trace,
                LangfuseTraceFilter.builder().tags(List.of("env=dev")).build()));
    }

    @Test
    void shouldFilterBySessionIds() {
        LangfuseTrace trace = trace("1", List.of("target_id=1"), "s1", "chat", "hi", "hello");

        assertTrue(LangfuseTraceFilterMatcher.matches(trace,
                LangfuseTraceFilter.builder().sessionIds(List.of("s1", "s2")).build()));
        assertFalse(LangfuseTraceFilterMatcher.matches(trace,
                LangfuseTraceFilter.builder().sessionIds(List.of("s2")).build()));
    }

    @Test
    void shouldFilterOnlyFailedByErrorTag() {
        LangfuseTrace ok = trace("1", List.of("target_id=1"), "s1", "chat", "hi", "hello");
        LangfuseTrace failed = trace("1", List.of("target_id=1", "error=true"), "s1", "chat", "hi", "boom");

        assertFalse(LangfuseTraceFilterMatcher.matches(ok,
                LangfuseTraceFilter.builder().onlyFailed(true).build()));
        assertTrue(LangfuseTraceFilterMatcher.matches(failed,
                LangfuseTraceFilter.builder().onlyFailed(true).build()));
    }

    @Test
    void shouldSearchAcrossNameSessionInputOutput() {
        LangfuseTrace trace = trace("1", List.of("target_id=1"), "session-abc", "客服问答", "退款怎么处理", "已退款");

        assertTrue(LangfuseTraceFilterMatcher.matches(trace,
                LangfuseTraceFilter.builder().searchQuery("退款").build()));
        assertTrue(LangfuseTraceFilterMatcher.matches(trace,
                LangfuseTraceFilter.builder().searchQuery("session-abc").build()));
        assertFalse(LangfuseTraceFilterMatcher.matches(trace,
                LangfuseTraceFilter.builder().searchQuery("不存在的词").build()));
    }

    @Test
    void shouldPassWhenFilterHasNoConditions() {
        LangfuseTrace trace = trace("1", List.of("target_id=1"), "s1", "chat", "hi", "hello");

        assertTrue(LangfuseTraceFilterMatcher.matches(trace, LangfuseTraceFilter.builder().build()));
    }
}
