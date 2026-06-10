package com.example.platform.task.dto;

import com.example.platform.task.model.CaseResultEntity;

public record CaseResultResponse(
        Long id,
        Long taskId,
        String historyId,
        String fullName,
        String suiteName,
        String storyName,
        String status,
        Long durationMs,
        String projectName,
        int artifactCount) {
    public static CaseResultResponse from(CaseResultEntity entity, int artifactCount) {
        return new CaseResultResponse(
                entity.getId(),
                entity.getTaskId(),
                entity.getHistoryId(),
                entity.getFullName(),
                entity.getSuiteName(),
                entity.getStoryName(),
                entity.getStatus(),
                entity.getDurationMs(),
                entity.getProjectName(),
                artifactCount);
    }
}
