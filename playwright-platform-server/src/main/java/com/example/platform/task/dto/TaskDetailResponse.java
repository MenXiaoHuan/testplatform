package com.example.platform.task.dto;

import com.example.platform.task.model.TaskEntity;
import java.time.Instant;

/**
 * 任务详情响应 DTO。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装任务的完整详情信息，用于前端详情页展示</li>
 *   <li>包含场景名称、仓库名称、执行参数、解析后的配置等字段</li>
 *   <li>提供静态工厂方法从实体转换</li>
 * </ul>
 *
 * <p>依赖：{@link TaskEntity}、{@link TaskTimeMapper}
 */
public record TaskDetailResponse(
        /** 任务ID */
        Long id,
        /** 所属场景ID */
        Long sceneId,
        /** 所属仓库ID */
        Long repoId,
        /** 场景名称 */
        String sceneName,
        /** 仓库名称 */
        String repositoryName,
        /** 任务状态 */
        String status,
        /** 详情是否可用 */
        boolean detailAvailable,
        /** 触发类型 */
        String triggerType,
        /** 触发用户 */
        String triggerUser,
        /** 触发原因 */
        String triggerReason,
        /** 分支名称 */
        String branch,
        /** 提交SHA */
        String commitSha,
        /** 当前执行阶段 */
        String currentStage,
        /** 结果码 */
        String resultCode,
        /** 是否已请求取消 */
        boolean cancelRequested,
        /** 请求取消的用户 */
        String cancelRequestedBy,
        /** 开始时间 */
        Instant startedAt,
        /** 结束时间 */
        Instant finishedAt,
        /** 执行耗时（毫秒） */
        Long durationMs,
        /** 执行器名称 */
        String runnerName,
        /** 日志URL */
        String logUrl,
        /** 结果消息 */
        String resultMessage,
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
        /** 环境变量数量 */
        int environmentVariableCount,
        /** 制品数量 */
        int artifactCount,
        /** 是否有制品 */
        boolean hasArtifacts) {

    /**
     * 从实体创建详情响应
     *
     * @param task 任务实体
     * @param sceneName 场景名称
     * @param repositoryName 仓库名称
     * @param environmentVariableCount 环境变量数量
     * @param artifactCount 制品数量
     * @return 任务详情响应
     */
    public static TaskDetailResponse from(
            TaskEntity task,
            String sceneName,
            String repositoryName,
            int environmentVariableCount,
            int artifactCount) {
        return new TaskDetailResponse(
                task.getId(),
                task.getSceneId(),
                task.getRepoId(),
                sceneName,
                repositoryName,
                task.getStatus(),
                !"RUNNING".equalsIgnoreCase(task.getStatus()) && !"QUEUED".equalsIgnoreCase(task.getStatus()),
                task.getTriggerType(),
                task.getTriggerUser(),
                task.getTriggerReason(),
                task.getBranch(),
                task.getCommitSha(),
                task.getCurrentStage(),
                task.getResultCode(),
                Boolean.TRUE.equals(task.getCancelRequested()),
                task.getCancelRequestedBy(),
                TaskTimeMapper.toInstant(task.getStartedAt()),
                TaskTimeMapper.toInstant(task.getFinishedAt()),
                task.getDurationMs(),
                task.getRunnerName(),
                task.getLogUrl(),
                task.getResultMessage(),
                task.getResolvedBranch(),
                task.getResolvedBrowser(),
                task.getResolvedEnvJson(),
                task.getResolvedMatchValue(),
                task.getResolvedTestRoot(),
                task.getResolvedRunCommand(),
                environmentVariableCount,
                artifactCount,
                artifactCount > 0);
    }
}
