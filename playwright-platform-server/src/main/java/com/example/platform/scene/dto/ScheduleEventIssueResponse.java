package com.example.platform.scene.dto;

import com.example.platform.scene.model.ScheduleEventEntity;
import java.time.LocalDateTime;

/**
 * 调度事件（问题排查）响应 DTO —— 用于前端调度事件列表的详细展示。
 *
 * @param id              事件 ID
 * @param sceneId         关联场景 ID
 * @param sceneName       场景名称（通过 JOIN 查询填充）
 * @param plannedFireAt   计划触发时间
 * @param status          事件状态（ACQUIRED / TASK_CREATED / RUNNING / COMPLETED / FAILED / ABANDONED）
 * @param scheduleType    调度类型（CRON / AGENT / MANUAL）
 * @param traceId         trace 链路 ID
 * @param sessionId       Agent 会话 ID
 * @param userMessage     用户消息（Agent 调度时）
 * @param retryCount      已重试次数
 * @param nextRetryAt     下次重试时间
 * @param lastErrorAt     最近一次错误时间
 * @param triggerReason   触发原因
 * @param errorMessage    错误信息
 * @param taskId          关联任务 ID
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 */
public record ScheduleEventIssueResponse(
        Long id,
        Long sceneId,
        String sceneName,
        LocalDateTime plannedFireAt,
        String status,
        String scheduleType,
        String traceId,
        String sessionId,
        String userMessage,
        Integer retryCount,
        LocalDateTime nextRetryAt,
        LocalDateTime lastErrorAt,
        String triggerReason,
        String errorMessage,
        Long taskId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** 从实体转换为响应 DTO（不含场景名称）。 */
    public static ScheduleEventIssueResponse from(ScheduleEventEntity entity) {
        return new ScheduleEventIssueResponse(
                entity.getId(),
                entity.getSceneId(),
                null,
                entity.getPlannedFireAt(),
                entity.getStatus(),
                entity.getScheduleType(),
                entity.getTraceId(),
                entity.getSessionId(),
                entity.getUserMessage(),
                entity.getRetryCount(),
                entity.getNextRetryAt(),
                entity.getLastErrorAt(),
                entity.getTriggerReason(),
                entity.getErrorMessage(),
                entity.getTaskId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    /** 从实体转换为响应 DTO 并填充场景名称。 */
    public static ScheduleEventIssueResponse withSceneName(ScheduleEventEntity entity, String sceneName) {
        return new ScheduleEventIssueResponse(
                entity.getId(),
                entity.getSceneId(),
                sceneName,
                entity.getPlannedFireAt(),
                entity.getStatus(),
                entity.getScheduleType(),
                entity.getTraceId(),
                entity.getSessionId(),
                entity.getUserMessage(),
                entity.getRetryCount(),
                entity.getNextRetryAt(),
                entity.getLastErrorAt(),
                entity.getTriggerReason(),
                entity.getErrorMessage(),
                entity.getTaskId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
