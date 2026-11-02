package com.example.platform.patrol.dto;

import com.example.platform.patrol.model.PatrolTaskEntity;
import java.time.LocalDateTime;

/** 巡页任务响应。 */
public record PatrolTaskResponse(
        Long id,
        Long spaceId,
        Long sceneId,
        String sceneName,
        String triggerType,
        String status,
        String currentStage,
        String startUrl,
        String taskBrief,
        int budgetDurationMinutes,
        int budgetLlmTokens,
        int budgetCases,
        int caseCount,
        int casePassCount,
        int caseFailCount,
        int caseSkipCount,
        int findingP0Count,
        int findingP1Count,
        int findingP2Count,
        int llmTokenUsed,
        boolean degraded,
        String resultMessage,
        String approvedBy,
        LocalDateTime approvedAt,
        LocalDateTime retentionUntil,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMs,
        String createdBy,
        LocalDateTime createdAt,
        int coverageTotal,
        int coverageCovered,
        double coverageRate
) {
    public static PatrolTaskResponse from(PatrolTaskEntity task) {
        return from(task, 0, 0, 0d);
    }

    public static PatrolTaskResponse from(PatrolTaskEntity task, int coverageTotal, int coverageCovered,
                                          double coverageRate) {
        return new PatrolTaskResponse(
                task.getId(),
                task.getSpaceId(),
                task.getSceneId(),
                task.getSceneName(),
                task.getTriggerType(),
                task.getStatus(),
                task.getCurrentStage(),
                task.getStartUrl(),
                task.getTaskBrief(),
                task.getBudgetDurationMinutes() == null ? 15 : task.getBudgetDurationMinutes(),
                task.getBudgetLlmTokens() == null ? 300000 : task.getBudgetLlmTokens(),
                task.getBudgetCases() == null ? 10 : task.getBudgetCases(),
                task.getCaseCount() == null ? 0 : task.getCaseCount(),
                task.getCasePassCount() == null ? 0 : task.getCasePassCount(),
                task.getCaseFailCount() == null ? 0 : task.getCaseFailCount(),
                task.getCaseSkipCount() == null ? 0 : task.getCaseSkipCount(),
                task.getFindingP0Count() == null ? 0 : task.getFindingP0Count(),
                task.getFindingP1Count() == null ? 0 : task.getFindingP1Count(),
                task.getFindingP2Count() == null ? 0 : task.getFindingP2Count(),
                task.getLlmTokenUsed() == null ? 0 : task.getLlmTokenUsed(),
                Boolean.TRUE.equals(task.getDegraded()),
                task.getResultMessage(),
                task.getApprovedBy(),
                task.getApprovedAt(),
                task.getRetentionUntil(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getDurationMs(),
                task.getCreatedBy(),
                task.getCreatedAt(),
                coverageTotal,
                coverageCovered,
                coverageRate);
    }
}
