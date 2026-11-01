package com.example.platform.evaluation.langfuse;

import java.time.Instant;
import java.util.Map;

/** 从 Langfuse Trace 详情解析出的 Observation（Span / Generation / Event）。 */
public record LangfuseObservation(
        String observationId,
        String traceId,
        String parentObservationId,
        String type,
        String name,
        String model,
        String input,
        String output,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens,
        Long latencyMs,
        Instant startTime,
        Instant endTime,
        String level,
        String statusMessage,
        Map<String, Object> metadata) {
}
