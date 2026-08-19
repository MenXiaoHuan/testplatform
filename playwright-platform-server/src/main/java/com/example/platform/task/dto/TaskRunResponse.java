package com.example.platform.task.dto;

import com.example.platform.task.model.TaskEntity;
import java.time.Instant;

/**
 * 任务运行响应 DTO。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装任务启动时的响应信息</li>
 *   <li>包含任务的基本运行状态和配置详情</li>
 *   <li>提供静态工厂方法从实体转换</li>
 * </ul>
 *
 * <p>依赖：{@link TaskEntity}、{@link TaskTimeMapper}
 */
public record TaskRunResponse(
        /** 任务ID */
        Long id,
        /** 所属场景ID */
        Long sceneId,
        /** 所属仓库ID */
        Long repoId,
        /** 任务状态 */
        String status,
        /** 触发类型 */
        String triggerType,
        /** 触发原因 */
        String triggerReason,
        /** 触发用户 */
        String triggerUser,
        /** 排队时间 */
        Instant queuedAt,
        /** 分支名称 */
        String branch,
        /** 提交SHA */
        String commitSha,
        /** 开始时间 */
        Instant startedAt,
        /** 结束时间 */
        Instant finishedAt,
        /** 执行耗时（毫秒） */
        Long durationMs,
        /** 执行器名称 */
        String runnerName,
        /** 当前执行阶段 */
        String currentStage,
        /** 结果码 */
        String resultCode,
        /** 结果消息 */
        String resultMessage,
        /** 是否已请求取消 */
        boolean cancelRequested,
        /** 请求取消的用户 */
        String cancelRequestedBy,
        /** 日志URL */
        String logUrl,
        /** 解析后的分支 */
        String resolvedBranch,
        /** 解析后的浏览器 */
        String resolvedBrowser,
        /** 解析后的环境变量JSON */
        String resolvedEnvJson,
        /** 解析后的匹配值 */
        String resolvedMatchValue,
        /** 解析后的测试根目录 */
        String resolvedTestRoot,
        /** 解析后的运行命令 */
        String resolvedRunCommand,
        /** 创建时间 */
        Instant createdAt) {

    /**
     * 从任务实体创建运行响应
     *
     * @param task 任务实体
     * @return 任务运行响应
     */
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
