package com.example.platform.task.dto;

import com.example.platform.task.model.TaskEntity;
import java.time.LocalDateTime;

public record TaskDetailResponse(
        Long id,
        Long sceneId,
        Long repoId,
        String status,
        String triggerType,
        String triggerUser,
        String branch,
        String commitSha,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMs,
        String runnerName,
        String reportUrl,
        String logUrl,
        int artifactCount,
        boolean hasArtifacts,
        boolean reportReady) {

    public static TaskDetailResponse from(TaskEntity task, int artifactCount) {
        boolean reportReady = task.getReportUrl() != null && !task.getReportUrl().isBlank();
        return new TaskDetailResponse(
                task.getId(),
                task.getSceneId(),
                task.getRepoId(),
                task.getStatus(),
                task.getTriggerType(),
                task.getTriggerUser(),
                task.getBranch(),
                task.getCommitSha(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getDurationMs(),
                task.getRunnerName(),
                task.getReportUrl(),
                task.getLogUrl(),
                artifactCount,
                artifactCount > 0,
                reportReady);
    }
}
