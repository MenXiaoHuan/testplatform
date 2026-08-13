package com.example.platform.ai.output;

import java.util.List;

public record ChatAssistantResult(
        String traceId,
        String response,
        List<String> usedTools,
        String confidence,
        String responseType,
        FaultDetail faultDetail
) {
    public ChatAssistantResult {
        if (traceId == null || traceId.isBlank()) {
            traceId = java.util.UUID.randomUUID().toString();
        }
        if (usedTools == null) {
            usedTools = List.of();
        }
        if (confidence == null) {
            confidence = "MEDIUM";
        }
        if (responseType == null || responseType.isBlank()) {
            responseType = "UNKNOWN";
        }
    }

    public ChatAssistantResult(String response, List<String> usedTools, String confidence) {
        this(null, response, usedTools, confidence, null, null);
    }

    public ChatAssistantResult(String response, List<String> usedTools, String confidence, String responseType, FaultDetail faultDetail) {
        this(null, response, usedTools, confidence, responseType, faultDetail);
    }

    public record FaultDetail(
            String fault_type,
            String root_cause,
            String immediate_solution,
            String long_term_optimize,
            String test_risk,
            String reproduce_steps
    ) {
        public FaultDetail {
            if (fault_type == null) fault_type = "";
            if (root_cause == null) root_cause = "";
            if (immediate_solution == null) immediate_solution = "";
            if (long_term_optimize == null) long_term_optimize = "";
            if (test_risk == null) test_risk = "";
            if (reproduce_steps == null) reproduce_steps = "无";
        }
    }
}
