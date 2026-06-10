package com.example.platform.task.parser;

public record ParsedCaseResult(
        Long taskId,
        String historyId,
        String fullName,
        String suiteName,
        String storyName,
        String status,
        Long durationMs,
        String projectName) {
}
