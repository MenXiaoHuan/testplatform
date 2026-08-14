package com.example.platform.ai.output;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatAssistantResult(
        String traceId,
        List<String> usedTools,
        String confidence,
        String responseType,
        FaultDetail faultDetail,
        List<ContentBlock> sections
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
        if (sections == null) {
            sections = List.of();
        }
    }

    public ChatAssistantResult(List<String> usedTools, String confidence) {
        this(null, usedTools, confidence, null, null, null);
    }

    public ChatAssistantResult(List<String> usedTools, String confidence, String responseType, FaultDetail faultDetail) {
        this(null, usedTools, confidence, responseType, faultDetail, null);
    }

    public ChatAssistantResult(List<String> usedTools, String confidence, String responseType, FaultDetail faultDetail, List<ContentBlock> sections) {
        this(null, usedTools, confidence, responseType, faultDetail, sections);
    }

    public String deriveResponse() {
        return OutputFormatFallbackService.deriveTextFromSections(sections);
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
