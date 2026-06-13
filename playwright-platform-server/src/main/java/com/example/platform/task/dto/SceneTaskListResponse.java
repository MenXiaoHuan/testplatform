package com.example.platform.task.dto;

import com.example.platform.task.model.TaskEntity;
import java.time.LocalDateTime;

public record SceneTaskListResponse(
        Long id,
        Long sceneId,
        String status,
        boolean detailAvailable,
        String triggerType,
        String branch,
        Long durationMs,
        LocalDateTime createdAt,
        String runnerName,
        String reportUrl,
        boolean reportReady,
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
                task.getBranch(),
                task.getDurationMs(),
                task.getCreatedAt(),
                task.getRunnerName(),
                task.getReportUrl(),
                task.isReportReady(),
                task.getPassedCount(),
                task.getFailedCount(),
                task.getSkippedCount());
    }

    private static boolean isDetailAvailable(TaskEntity task) {
        return !"RUNNING".equalsIgnoreCase(task.getStatus());
    }
}
