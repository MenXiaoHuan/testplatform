package com.example.platform.patrol.dto;

import com.example.platform.patrol.model.PatrolSceneEntity;
import java.time.LocalDateTime;

/** 巡页场景响应：认证信息只返回是否已配置。 */
public record PatrolSceneResponse(
        Long id,
        Long spaceId,
        String name,
        String description,
        String startUrl,
        String browser,
        boolean headless,
        String authType,
        boolean authConfigured,
        Long planModelId,
        String planModelName,
        Long judgeModelId,
        String judgeModelName,
        String taskBrief,
        int budgetDurationMinutes,
        int budgetLlmTokens,
        int visibleTextLimit,
        int budgetCases,
        boolean scrollScan,
        boolean recordVideo,
        boolean autoApproveWrite,
        boolean autoApproveDestructive,
        boolean scheduleEnabled,
        String cronExpression,
        LocalDateTime nextRunAt,
        LocalDateTime lastRunAt,
        Long lastTaskId,
        String lastTaskStatus,
        long totalTokenUsed,
        LocalDateTime createdAt
) {
    public static PatrolSceneResponse from(PatrolSceneEntity entity) {
        boolean authConfigured = "COOKIE".equalsIgnoreCase(entity.getAuthType())
                ? entity.getAuthCookieEncrypted() != null
                : "TOKEN".equalsIgnoreCase(entity.getAuthType())
                ? entity.getAuthTokenEncrypted() != null
                : "LOCAL_STORAGE".equalsIgnoreCase(entity.getAuthType())
                && entity.getAuthLocalStorageEncrypted() != null;
        return new PatrolSceneResponse(
                entity.getId(),
                entity.getSpaceId(),
                entity.getName(),
                entity.getDescription(),
                entity.getStartUrl(),
                entity.getBrowser(),
                !Boolean.FALSE.equals(entity.getHeadless()),
                entity.getAuthType(),
                authConfigured,
                entity.getPlanModelId(),
                null,
                entity.getJudgeModelId(),
                null,
                entity.getTaskBrief(),
                entity.getBudgetDurationMinutes() == null ? 15 : entity.getBudgetDurationMinutes(),
                entity.getBudgetLlmTokens() == null ? 300000 : entity.getBudgetLlmTokens(),
                entity.getVisibleTextLimit() == null ? 1800 : entity.getVisibleTextLimit(),
                entity.getBudgetCases() == null ? 10 : entity.getBudgetCases(),
                !Boolean.FALSE.equals(entity.getScrollScan()),
                !Boolean.FALSE.equals(entity.getRecordVideo()),
                Boolean.TRUE.equals(entity.getAutoApproveWrite()),
                Boolean.TRUE.equals(entity.getAutoApproveDestructive()),
                Boolean.TRUE.equals(entity.getScheduleEnabled()),
                entity.getCronExpression(),
                entity.getNextRunAt(),
                entity.getLastRunAt(),
                entity.getLastTaskId(),
                entity.getLastTaskStatus(),
                0L,
                entity.getCreatedAt());
    }

    public PatrolSceneResponse withModelNames(String planModelName, String judgeModelName, long totalTokenUsed) {
        return new PatrolSceneResponse(id, spaceId, name, description, startUrl, browser, headless, authType,
                authConfigured, planModelId, planModelName, judgeModelId, judgeModelName, taskBrief,
                budgetDurationMinutes, budgetLlmTokens, visibleTextLimit, budgetCases, scrollScan, recordVideo,
                autoApproveWrite, autoApproveDestructive, scheduleEnabled, cronExpression, nextRunAt,
                lastRunAt, lastTaskId, lastTaskStatus, totalTokenUsed, createdAt);
    }
}
