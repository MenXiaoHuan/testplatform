package com.example.platform.patrol.dto;

import com.example.platform.patrol.model.PatrolCaseStepEntity;

/** 用例步骤响应。 */
public record PatrolCaseStepResponse(
        Long id,
        int seq,
        String action,
        String targetSelector,
        String targetLabel,
        String inputValue,
        String operation,
        String risk,
        boolean approved,
        String status,
        String message,
        String screenshotKey,
        String requestJson,
        Long durationMs
) {
    public static PatrolCaseStepResponse from(PatrolCaseStepEntity step) {
        return new PatrolCaseStepResponse(
                step.getId(),
                step.getSeq() == null ? 0 : step.getSeq(),
                step.getAction(),
                step.getTargetSelector(),
                step.getTargetLabel(),
                step.getInputValue(),
                step.getOperation(),
                step.getRisk(),
                Boolean.TRUE.equals(step.getApproved()),
                step.getStatus(),
                step.getMessage(),
                step.getScreenshotKey(),
                step.getRequestJson(),
                step.getDurationMs());
    }
}
