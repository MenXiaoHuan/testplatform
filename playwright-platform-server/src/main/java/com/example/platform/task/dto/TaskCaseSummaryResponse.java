package com.example.platform.task.dto;

public record TaskCaseSummaryResponse(
        int passed,
        int failed,
        int skipped,
        int total) {
}
