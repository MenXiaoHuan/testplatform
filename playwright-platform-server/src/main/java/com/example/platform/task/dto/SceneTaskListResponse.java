package com.example.platform.task.dto;

import com.example.platform.task.model.TaskEntity;
import java.time.Instant;

/**
 * 场景任务列表响应 DTO。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装场景下任务的列表摘要信息</li>
 *   <li>包含任务状态、触发类型、当前阶段、执行结果等关键字段</li>
 *   <li>提供静态工厂方法从实体转换</li>
 * </ul>
 *
 * <p>依赖：{@link TaskEntity}、{@link TaskTimeMapper}
 */
public record SceneTaskListResponse(
        /** 任务ID */
        Long id,
        /** 所属场景ID */
        Long sceneId,
        /** 任务状态 */
        String status,
        /** 详情是否可用（非运行中/排队中状态） */
        boolean detailAvailable,
        /** 触发类型：MANUAL、SCHEDULED 等 */
        String triggerType,
        /** 当前执行阶段 */
        String currentStage,
        /** 结果码 */
        String resultCode,
        /** 是否已请求取消 */
        boolean cancelRequested,
        /** 分支名称 */
        String branch,
        /** 排队时间 */
        Instant queuedAt,
        /** 开始时间 */
        Instant startedAt,
        /** 执行耗时（毫秒） */
        Long durationMs,
        /** 创建时间 */
        Instant createdAt,
        /** 执行器名称 */
        String runnerName,
        /** 通过用例数 */
        int passedCount,
        /** 失败用例数 */
        int failedCount,
        /** 跳过用例数 */
        int skippedCount) {

    /**
     * 从任务实体创建列表响应
     *
     * @param task 任务实体
     * @return 场景任务列表响应
     */
    public static SceneTaskListResponse from(TaskEntity task) {
        return new SceneTaskListResponse(
                task.getId(),
                task.getSceneId(),
                task.getStatus(),
                isDetailAvailable(task),
                task.getTriggerType(),
                task.getCurrentStage(),
                task.getResultCode(),
                Boolean.TRUE.equals(task.getCancelRequested()),
                task.getBranch(),
                TaskTimeMapper.toInstant(task.getQueuedAt()),
                TaskTimeMapper.toInstant(task.getStartedAt()),
                task.getDurationMs(),
                TaskTimeMapper.toInstant(task.getCreatedAt()),
                task.getRunnerName(),
                task.getPassedCount(),
                task.getFailedCount(),
                task.getSkippedCount());
    }

    /**
     * 判断详情是否可用（运行中或排队中的任务详情不可用）
     */
    private static boolean isDetailAvailable(TaskEntity task) {
        return !"RUNNING".equalsIgnoreCase(task.getStatus())
                && !"QUEUED".equalsIgnoreCase(task.getStatus());
    }
}
