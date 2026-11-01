package com.example.platform.evaluation.langfuse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 从 Langfuse 拉回的 Trace 实体（不可变 DTO）。 */
public record LangfuseTrace(
        String traceId,
        String name,
        String targetId,
        String sessionId,
        String userId,
        String input,
        String output,
        Long inputTokens,
        Long outputTokens,
        Long durationMs,
        Instant startTime,
        Instant endTime,
        List<String> tags,
        boolean success,
        Map<String, Object> metadata) {
}
