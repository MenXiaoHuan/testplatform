package com.example.platform.patrol.dto;

import com.example.platform.patrol.model.PatrolSceneEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;

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
        Long resolveModelId,
        String resolveModelName,
        Long supplementModelId,
        String supplementModelName,
        List<PatrolSceneSupplementDraft> supplements,
        int budgetDurationMinutes,
        int visibleTextLimit,
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
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<PatrolSceneSupplementDraft>> SUPPLEMENT_TYPE =
            new TypeReference<>() {
            };

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
                entity.getResolveModelId(),
                null,
                entity.getSupplementModelId(),
                null,
                parseSupplements(entity.getSupplementsJson()),
                entity.getBudgetDurationMinutes() == null ? 20 : entity.getBudgetDurationMinutes(),
                entity.getVisibleTextLimit() == null ? 1800 : entity.getVisibleTextLimit(),
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

    public PatrolSceneResponse withModelNames(String planModelName, String judgeModelName,
                                              String resolveModelName, String supplementModelName,
                                              long totalTokenUsed) {
        return new PatrolSceneResponse(id, spaceId, name, description, startUrl, browser, headless, authType,
                authConfigured, planModelId, planModelName, judgeModelId, judgeModelName,
                resolveModelId, resolveModelName, supplementModelId, supplementModelName, supplements,
                budgetDurationMinutes, visibleTextLimit, scrollScan, recordVideo,
                autoApproveWrite, autoApproveDestructive, scheduleEnabled, cronExpression, nextRunAt,
                lastRunAt, lastTaskId, lastTaskStatus, totalTokenUsed, createdAt);
    }

    private static List<PatrolSceneSupplementDraft> parseSupplements(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, SUPPLEMENT_TYPE);
        } catch (Exception exception) {
            return List.of();
        }
    }
}
