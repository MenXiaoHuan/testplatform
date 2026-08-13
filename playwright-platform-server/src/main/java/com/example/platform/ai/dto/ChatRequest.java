package com.example.platform.ai.dto;

public record ChatRequest(
        String sessionId,
        String message,
        Long taskId,
        Long sceneId,
        Long spaceId,
        Boolean saveHistory
) {
    public ChatRequest {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = java.util.UUID.randomUUID().toString();
        }
        if (saveHistory == null) {
            saveHistory = false;
        }
    }
}