package com.example.platform.task.dto;

import com.example.platform.task.model.TaskEntity;
import java.time.Instant;

public record TaskRunResponse(
        Long id,
        Long sceneId,
        Long repoId,
        String status,
        String triggerType,
        String triggerReason,
        String triggerUser,
        Instant queuedAt,
        String branch,
        String commitSha,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String runnerName,
        String currentStage,
        String resultCode,
        String resultMessage,
        boolean cancelRequested,
        String cancelRequestedBy,
        String logUrl,
        String resolvedBranch,
        String resolvedBrowser,
        String resolvedEnvJson,
        String resolvedMatchValue,
        String resolvedTestRoot,
        String resolvedRunCommand,
        Instant createdAt) {

    public static TaskRunResponse from(TaskEntity task) {
        return new TaskRunResponse(
                task.getId(),
                task.getSceneId(),
                task.getRepoId(),
                task.getStatus(),
                task.getTriggerType(),
                task.getTriggerReason(),
                task.getTriggerUser(),
                TaskTimeMapper.toInstant(task.getQueuedAt()),
                task.getBranch(),
                task.getCommitSha(),
                TaskTimeMapper.toInstant(task.getStartedAt()),
                TaskTimeMapper.toInstant(task.getFinishedAt()),
                task.getDurationMs(),
                task.getRunnerName(),
                task.getCurrentStage(),
                task.getResultCode(),
                task.getResultMessage(),
                Boolean.TRUE.equals(task.getCancelRequested()),
                task.getCancelRequestedBy(),
                task.getLogUrl(),
                task.getResolvedBranch(),
                task.getResolvedBrowser(),
                task.getResolvedEnvJson(),
                task.getResolvedMatchValue(),
                task.getResolvedTestRoot(),
                task.getResolvedRunCommand(),
                TaskTimeMapper.toInstant(task.getCreatedAt()));
    }
}
