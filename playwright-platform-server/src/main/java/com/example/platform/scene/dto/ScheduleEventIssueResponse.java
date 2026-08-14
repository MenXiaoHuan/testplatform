package com.example.platform.scene.dto;

import com.example.platform.scene.model.ScheduleEventEntity;
import java.time.LocalDateTime;

public record ScheduleEventIssueResponse(
        Long id,
        Long sceneId,
        String sceneName,
        LocalDateTime plannedFireAt,
        String status,
        String scheduleType,
        String traceId,
        String sessionId,
        String userMessage,
        Integer retryCount,
        LocalDateTime nextRetryAt,
        LocalDateTime lastErrorAt,
        String triggerReason,
        String errorMessage,
        Long taskId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ScheduleEventIssueResponse from(ScheduleEventEntity entity) {
        return new ScheduleEventIssueResponse(
                entity.getId(),
                entity.getSceneId(),
                null,
                entity.getPlannedFireAt(),
                entity.getStatus(),
                entity.getScheduleType(),
                entity.getTraceId(),
                entity.getSessionId(),
                entity.getUserMessage(),
                entity.getRetryCount(),
                entity.getNextRetryAt(),
                entity.getLastErrorAt(),
                entity.getTriggerReason(),
                entity.getErrorMessage(),
                entity.getTaskId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public static ScheduleEventIssueResponse withSceneName(ScheduleEventEntity entity, String sceneName) {
        return new ScheduleEventIssueResponse(
                entity.getId(),
                entity.getSceneId(),
                sceneName,
                entity.getPlannedFireAt(),
                entity.getStatus(),
                entity.getScheduleType(),
                entity.getTraceId(),
                entity.getSessionId(),
                entity.getUserMessage(),
                entity.getRetryCount(),
                entity.getNextRetryAt(),
                entity.getLastErrorAt(),
                entity.getTriggerReason(),
                entity.getErrorMessage(),
                entity.getTaskId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
