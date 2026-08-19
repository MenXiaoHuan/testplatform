package com.example.platform.task.service;

import com.example.platform.task.model.TaskStageLogEntity;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务阶段日志服务接口 —— 定义将任务执行各阶段的日志文件归档到对象存储并持久化的能力。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #archiveStageLog(Long, String, Path, int)} —— 归档简单阶段日志（无详细执行信息）</li>
 *   <li>{@link #archiveStageLog(Long, String, Path, int, Long, Integer, String, String, LocalDateTime, LocalDateTime, String)} —— 归档完整阶段日志</li>
 *   <li>{@link #listByTaskId(Long)} —— 查询任务的所有阶段日志记录</li>
 * </ul>
 */
public interface TaskStageLogService {

    /**
     * 归档简单阶段日志。
     *
     * @param taskId 任务 ID
     * @param stage 阶段名称（如 INSTALL、TEST 等）
     * @param logFile 日志文件路径
     * @param lineCount 日志行数
     * @return 归档后的阶段日志实体
     */
    TaskStageLogEntity archiveStageLog(Long taskId, String stage, Path logFile, int lineCount);

    /**
     * 归档完整阶段日志，包含执行时间、退出码、命令等详细信息。
     *
     * @param taskId 任务 ID
     * @param stage 阶段名称
     * @param logFile 日志文件路径
     * @param lineCount 日志行数
     * @param durationMs 执行耗时（毫秒）
     * @param exitCode 进程退出码
     * @param stageStatus 阶段状态（SUCCESS、FAILED 等）
     * @param command 执行的命令
     * @param startedAt 开始时间
     * @param endedAt 结束时间
     * @param errorMessage 错误消息
     * @return 归档后的阶段日志实体
     */
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

    /**
     * 查询指定任务的所有阶段日志记录，按 ID 升序排列。
     *
     * @param taskId 任务 ID
     * @return 阶段日志实体列表
     */
    List<TaskStageLogEntity> listByTaskId(Long taskId);
}
