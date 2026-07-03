package com.example.platform.scene.dto;

import com.example.platform.scene.model.ScheduleEventEntity;
import java.time.LocalDateTime;

public record ScheduleEventIssueResponse(
        Long id,
        Long sceneId,
        LocalDateTime plannedFireAt,
        String status,
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
                entity.getPlannedFireAt(),
                entity.getStatus(),
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
