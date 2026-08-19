package com.example.platform.task.dto;

import com.example.platform.task.model.TaskStageLogEntity;
import java.time.LocalDateTime;

/**
 * 任务阶段日志响应 DTO。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装任务执行阶段的日志信息</li>
 *   <li>包含阶段名称、流类型、预览文本、行数、下载链接等</li>
 *   <li>提供静态工厂方法从实体转换</li>
 * </ul>
 *
 * <p>依赖：{@link TaskStageLogEntity}
 */
public record TaskStageLogResponse(
        /** 阶段日志ID */
        Long id,
        /** 阶段名称：PREPARING、INSTALLING、TESTING、ARCHIVING 等 */
        String stage,
        /** 流类型：stdout、stderr、combined */
        String streamType,
        /** 预览文本（日志前N行） */
        String previewText,
        /** 日志行数 */
        int lineCount,
        /** 下载URL */
        String downloadUrl,
        /** 执行耗时（毫秒） */
        Long durationMs,
        /** 退出码 */
        Integer exitCode,
        /** 阶段状态 */
        String stageStatus,
        /** 执行命令 */
        String command,
        /** 开始时间 */
        LocalDateTime startedAt,
        /** 结束时间 */
        LocalDateTime endedAt,
        /** 错误消息 */
        String errorMessage) {

    /**
     * 从实体创建阶段日志响应
     *
     * @param entity 阶段日志实体
     * @param downloadUrl 下载URL
     * @return 阶段日志响应
     */
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
