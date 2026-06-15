package com.example.platform.task.service;

import com.example.platform.task.model.TaskStageLogEntity;
import java.nio.file.Path;
import java.util.List;

public interface TaskStageLogService {
    TaskStageLogEntity archiveStageLog(Long taskId, String stage, Path logFile, int lineCount);
    List<TaskStageLogEntity> listByTaskId(Long taskId);
}
