package com.example.platform.task.dto;

import java.time.Instant;

/**
 * 任务追踪分享响应 DTO。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装追踪报告分享信息</li>
 *   <li>包含分享URL和过期时间</li>
 * </ul>
 *
 * <p>依赖：{@link Instant}
 */
public record TaskTraceShareResponse(
        /** 分享URL，用于外部访问追踪报告 */
        String shareUrl,
        /** 过期时间 */
        Instant expiresAt) {
}
