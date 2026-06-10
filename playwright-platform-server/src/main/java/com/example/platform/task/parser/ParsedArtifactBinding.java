package com.example.platform.task.parser;

public record ParsedArtifactBinding(
        String relativePath,
        String artifactType,
        String caseHistoryId) {
}
