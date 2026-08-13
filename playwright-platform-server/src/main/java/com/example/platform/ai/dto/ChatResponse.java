package com.example.platform.ai.dto;

import java.util.List;

public record ChatResponse(
        String response,
        List<String> usedTools,
        String confidence,
        Long taskId,
        Long sceneId,
        String processingTime,
        String sessionId,
        boolean contextCompressed
) {
    public ChatResponse {
        if (usedTools == null) {
            usedTools = List.of();
        }
        if (confidence == null) {
            confidence = "MEDIUM";
        }
        if (sessionId == null) {
            sessionId = "";
        }
    }

    public ChatResponse(String response, List<String> usedTools, String confidence,
                        Long taskId, Long sceneId, String processingTime) {
        this(response, usedTools, confidence, taskId, sceneId, processingTime, "", false);
    }
}