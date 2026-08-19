package com.example.platform.task.dto;

/**
 * 任务项目统计响应 DTO。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装按项目分组的任务统计信息</li>
 *   <li>包含项目名称和该项目的任务总数</li>
 * </ul>
 *
 * <p>依赖：无外部依赖
 */
public record TaskProjectStatResponse(
        /** 项目名称 */
        String projectName,
        /** 任务总数 */
        int total) {
}
