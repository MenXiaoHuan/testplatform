package com.example.platform.task.service;

import com.example.platform.task.model.TaskStageLogEntity;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public interface TaskStageLogService {
    TaskStageLogEntity archiveStageLog(Long taskId, String stage, Path logFile, int lineCount);

    TaskStageLogEntity archiveStageLog(
            Long taskId,
            String stage,
            Path logFile,
            int lineCount,
            Long durationMs,
            Integer exitCode,
            String stageStatus,
            String command,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            String errorMessage);

    List<TaskStageLogEntity> listByTaskId(Long taskId);
}
