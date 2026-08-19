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

/**
 * 任务阶段日志服务实现 —— 将任务执行各阶段的日志文件上传到对象存储并持久化元数据。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #archiveStageLog(Long, String, Path, int)} —— 归档简单阶段日志</li>
 *   <li>{@link #archiveStageLog(Long, String, Path, int, Long, Integer, String, String, LocalDateTime, LocalDateTime, String)} —— 归档带详细执行信息的阶段日志</li>
 *   <li>{@link #listByTaskId(Long)} —— 查询任务的所有阶段日志</li>
 * </ul>
 *
 * <p>依赖：{@link TaskStageLogMapper}（阶段日志数据访问层）、{@link ObjectStorageService}（对象存储服务）。
 */
@Service
public class TaskStageLogServiceImpl implements TaskStageLogService {
    /** 日志预览文本最大长度 */
    private static final int PREVIEW_TEXT_MAX_LENGTH = 512;
    /** 命令字段最大长度 */
    private static final int COMMAND_MAX_LENGTH = 1024;
    /** 错误消息最大长度 */
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

    /**
     * 归档简单阶段日志，委托给完整方法处理（执行信息均为 null）。
     */
    @Override
    public TaskStageLogEntity archiveStageLog(Long taskId, String stage, Path logFile, int lineCount) {
        return archiveStageLog(taskId, stage, logFile, lineCount, null, null, null, null, null, null, null);
    }

    /**
     * 归档完整阶段日志：上传日志文件到对象存储，读取预览文本，构建实体并保存。
     */
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
        // 规范化阶段名称并构建对象存储键
        String normalizedStage = stage == null || stage.isBlank()
                ? "unknown"
                : stage.toLowerCase(Locale.ROOT);
        String objectKey = "runs/" + taskId + "/logs/" + normalizedStage + ".log";
        // 上传日志文件到对象存储
        objectStorageService.uploadFile(storageBucket, objectKey, logFile);

        // 构建并保存阶段日志实体
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

    /**
     * 查询指定任务的所有阶段日志记录，按 ID 升序排列。
     */
    @Override
    public List<TaskStageLogEntity> listByTaskId(Long taskId) {
        return repository.findAllByTaskIdOrderByIdAsc(taskId);
    }

    /**
     * 读取日志文件大小，失败时返回 0。
     */
    private long resolveSize(Path logFile) {
        try {
            return Files.size(logFile);
        } catch (IOException exception) {
            return 0L;
        }
    }

    /**
     * 读取日志文件前 512 字符作为预览文本。
     */
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

    /**
     * 截断文本到指定最大长度。
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
