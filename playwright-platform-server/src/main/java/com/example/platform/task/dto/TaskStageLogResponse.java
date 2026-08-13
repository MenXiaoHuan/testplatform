package com.example.platform.task.dto;

import com.example.platform.task.model.TaskStageLogEntity;
import java.time.LocalDateTime;

public record TaskStageLogResponse(
        Long id,
        String stage,
        String streamType,
        String previewText,
        int lineCount,
        String downloadUrl,
        Long durationMs,
        Integer exitCode,
        String stageStatus,
        String command,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        String errorMessage) {

    public static TaskStageLogResponse from(TaskStageLogEntity entity, String downloadUrl) {
        return new TaskStageLogResponse(
                entity.getId(),
                entity.getStage(),
                entity.getStreamType(),
                entity.getPreviewText(),
                entity.getLineCount() == null ? 0 : entity.getLineCount(),
                downloadUrl,
                entity.getDurationMs(),
                entity.getExitCode(),
                entity.getStageStatus(),
                entity.getCommand(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getErrorMessage());
    }
}
