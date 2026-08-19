package com.example.platform.task.dto;

import java.time.Instant;

/**
 * 应用程序错误摘要响应 DTO。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装应用程序运行时捕获的错误摘要信息</li>
 *   <li>包含错误发生时间、日志记录器名称、异常类型等关键字段</li>
 *   <li>关联的任务ID、场景ID、仓库ID用于溯源定位</li>
 * </ul>
 *
 * <p>依赖：无外部依赖，仅使用 {@link Instant} 时间类型
 */
public record ApplicationErrorSummaryResponse(
        /** 错误发生时间 */
        Instant occurredAt,
        /** 日志记录器名称 */
        String loggerName,
        /** 错误消息 */
        String message,
        /** 异常类型 */
        String exceptionType,
        /** 请求ID，用于关联请求链路 */
        String requestId,
        /** 链路追踪ID */
        String traceId,
        /** 关联的任务ID */
        Long taskId,
        /** 关联的场景ID */
        Long sceneId,
        /** 关联的仓库ID */
        Long repoId,
        /** 任务执行阶段 */
        String stage) {
}
