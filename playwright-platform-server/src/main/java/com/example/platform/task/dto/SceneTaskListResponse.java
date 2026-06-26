package com.example.platform.task.dto;

import com.example.platform.task.model.TaskEntity;
import java.time.Instant;

public record SceneTaskListResponse(
        Long id,
        Long sceneId,
        String status,
        boolean detailAvailable,
        String triggerType,
        String currentStage,
        String resultCode,
        boolean cancelRequested,
        String branch,
        Instant queuedAt,
        Instant startedAt,
        Long durationMs,
        Instant createdAt,
        String runnerName,
        int passedCount,
        int failedCount,
        int skippedCount) {
    public static SceneTaskListResponse from(TaskEntity task) {
        return new SceneTaskListResponse(
                task.getId(),
                task.getSceneId(),
                task.getStatus(),
                isDetailAvailable(task),
                task.getTriggerType(),
                task.getCurrentStage(),
                task.getResultCode(),
                Boolean.TRUE.equals(task.getCancelRequested()),
                task.getBranch(),
                TaskTimeMapper.toInstant(task.getQueuedAt()),
                TaskTimeMapper.toInstant(task.getStartedAt()),
                task.getDurationMs(),
                TaskTimeMapper.toInstant(task.getCreatedAt()),
                task.getRunnerName(),
                task.getPassedCount(),
                task.getFailedCount(),
                task.getSkippedCount());
    }

    private static boolean isDetailAvailable(TaskEntity task) {
        return !"RUNNING".equalsIgnoreCase(task.getStatus())
                && !"QUEUED".equalsIgnoreCase(task.getStatus());
    }
}
