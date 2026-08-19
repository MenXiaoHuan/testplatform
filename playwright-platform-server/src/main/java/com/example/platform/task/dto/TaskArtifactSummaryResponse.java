package com.example.platform.task.dto;

/**
 * 任务制品摘要响应 DTO。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装任务执行产生的各类型制品数量统计</li>
 *   <li>包含视频、追踪、截图、日志、其他类型制品的计数</li>
 * </ul>
 *
 * <p>依赖：无外部依赖
 */
public record TaskArtifactSummaryResponse(
        /** 视频制品数量 */
        int videoCount,
        /** 追踪制品数量 */
        int traceCount,
        /** 截图制品数量 */
        int screenshotCount,
        /** 日志制品数量 */
        int logCount,
        /** 其他类型制品数量 */
        int otherCount) {
}
