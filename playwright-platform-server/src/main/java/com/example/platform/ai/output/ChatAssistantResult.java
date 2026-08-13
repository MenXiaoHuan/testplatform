package com.example.platform.ai.output;

import java.util.List;

public record ChatAssistantResult(
        String response,
        List<String> usedTools,
        String confidence
) {
    public ChatAssistantResult {
        if (usedTools == null) {
            usedTools = List.of();
        }
        if (confidence == null) {
            confidence = "MEDIUM";
        }
    }
}