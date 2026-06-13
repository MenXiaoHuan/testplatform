package com.example.platform.task.dto;

public record CaseArtifactLinkResponse(
        String artifactType,
        String label,
        String scope,
        String url) {
}
