package com.example.platform.ai.dto;

import java.util.List;
import java.util.Map;

public record ChatResponse(
        String traceId,
        String response,
        List<String> usedTools,
        String confidence,
        String responseType,
        Map<String, Object> faultDetail,
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
        if (responseType == null) {
            responseType = "UNKNOWN";
        }
    }

    public ChatResponse(String traceId, String response, List<String> usedTools, String confidence,
                        String responseType, Long taskId, Long sceneId, String processingTime,
                        String sessionId, boolean contextCompressed) {
        this(traceId, response, usedTools, confidence, responseType, null,
                taskId, sceneId, processingTime, sessionId, contextCompressed);
    }
}
