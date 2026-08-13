package com.example.platform.task.service;

import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.model.TaskStageLogEntity;
import com.example.platform.task.mapper.TaskStageLogMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TaskStageLogServiceImpl implements TaskStageLogService {
    private static final int PREVIEW_TEXT_MAX_LENGTH = 512;
    private static final int COMMAND_MAX_LENGTH = 1024;
    private static final int ERROR_MESSAGE_MAX_LENGTH = 2000;

    private final TaskStageLogMapper repository;
    private final ObjectStorageService objectStorageService;
    private final String storageBucket;

    public TaskStageLogServiceImpl(
            TaskStageLogMapper repository,
            ObjectStorageService objectStorageService,
            @Value("${platform.storage.bucket}") String storageBucket) {
        this.repository = repository;
        this.objectStorageService = objectStorageService;
        this.storageBucket = storageBucket;
    }

    @Override
    public TaskStageLogEntity archiveStageLog(Long taskId, String stage, Path logFile, int lineCount) {
        return archiveStageLog(taskId, stage, logFile, lineCount, null, null, null, null, null, null, null);
    }

    @Override
    public TaskStageLogEntity archiveStageLog(
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
            String errorMessage) {
        String normalizedStage = stage == null || stage.isBlank()
                ? "unknown"
                : stage.toLowerCase(Locale.ROOT);
        String objectKey = "runs/" + taskId + "/logs/" + normalizedStage + ".log";
        objectStorageService.uploadFile(storageBucket, objectKey, logFile);

        TaskStageLogEntity entity = new TaskStageLogEntity();
        entity.setTaskId(taskId);
        entity.setStage(stage);
        entity.setStreamType("COMBINED");
        entity.setObjectKey(objectKey);
        entity.setContentType("text/plain");
        entity.setSize(resolveSize(logFile));
        entity.setLineCount(lineCount);
        entity.setPreviewText(readPreview(logFile));
        entity.setDurationMs(durationMs != null ? durationMs : 0L);
        entity.setExitCode(exitCode);
        entity.setStageStatus(stageStatus != null ? stageStatus : "SUCCESS");
        entity.setCommand(truncate(command, COMMAND_MAX_LENGTH));
        entity.setStartedAt(startedAt);
        entity.setEndedAt(endedAt);
        entity.setErrorMessage(truncate(errorMessage, ERROR_MESSAGE_MAX_LENGTH));
        repository.insert(entity);
        return entity;
    }

    @Override
    public List<TaskStageLogEntity> listByTaskId(Long taskId) {
        return repository.findAllByTaskIdOrderByIdAsc(taskId);
    }

    private long resolveSize(Path logFile) {
        try {
            return Files.size(logFile);
        } catch (IOException exception) {
            return 0L;
        }
    }

    private String readPreview(Path logFile) {
        try {
            String preview = Files.readString(logFile);
            if (preview.length() <= PREVIEW_TEXT_MAX_LENGTH) {
                return preview;
            }
            return preview.substring(0, PREVIEW_TEXT_MAX_LENGTH);
        } catch (IOException exception) {
            return null;
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
