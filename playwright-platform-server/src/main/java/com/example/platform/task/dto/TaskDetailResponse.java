package com.example.platform.task.dto;

import com.example.platform.task.model.TaskEntity;
import java.time.LocalDateTime;

public record TaskDetailResponse(
        Long id,
        Long sceneId,
        Long repoId,
        String sceneName,
        String repositoryName,
        String status,
        boolean detailAvailable,
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
        String resolvedBranch,
        String resolvedBrowser,
        String resolvedEnvJson,
        String resolvedMatchValue,
        String resolvedTestRoot,
        String resolvedRunCommand,
        int environmentVariableCount,
        int artifactCount,
        boolean hasArtifacts,
        boolean reportReady) {

    public static TaskDetailResponse from(
            TaskEntity task,
            String sceneName,
            String repositoryName,
            int environmentVariableCount,
            int artifactCount) {
        boolean reportReady = task.getReportUrl() != null && !task.getReportUrl().isBlank();
        return new TaskDetailResponse(
                task.getId(),
                task.getSceneId(),
                task.getRepoId(),
                sceneName,
                repositoryName,
                task.getStatus(),
                !"RUNNING".equalsIgnoreCase(task.getStatus()),
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
                task.getResolvedBranch(),
                task.getResolvedBrowser(),
                task.getResolvedEnvJson(),
                task.getResolvedMatchValue(),
                task.getResolvedTestRoot(),
                task.getResolvedRunCommand(),
                environmentVariableCount,
                artifactCount,
                artifactCount > 0,
                reportReady);
    }
}
