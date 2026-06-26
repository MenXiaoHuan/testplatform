package com.example.platform.task.dto;

import java.time.Instant;

public record ApplicationErrorSummaryResponse(
        Instant occurredAt,
        String loggerName,
        String message,
        String exceptionType,
        String requestId,
        String traceId,
        Long taskId,
        Long sceneId,
        Long repoId,
        String stage) {
}
