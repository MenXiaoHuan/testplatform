package com.example.platform.task.dto;

import com.example.platform.task.model.TaskStageLogEntity;

public record TaskStageLogResponse(
        Long id,
        String stage,
        String streamType,
        String previewText,
        int lineCount,
        String downloadUrl) {

    public static TaskStageLogResponse from(TaskStageLogEntity entity, String downloadUrl) {
        return new TaskStageLogResponse(
                entity.getId(),
                entity.getStage(),
                entity.getStreamType(),
                entity.getPreviewText(),
                entity.getLineCount() == null ? 0 : entity.getLineCount(),
                downloadUrl);
    }
}
