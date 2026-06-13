package com.example.platform.task.dto;

public record TaskArtifactSummaryResponse(
        int videoCount,
        int traceCount,
        int screenshotCount,
        int logCount,
        int otherCount) {
}
