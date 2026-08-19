package com.example.platform.task.dto;

/**
 * 用例制品链接响应 DTO。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装单个制品的链接信息</li>
 *   <li>标识制品类型（如视频、追踪、截图、日志等）</li>
 *   <li>提供可访问的URL供前端展示</li>
 * </ul>
 *
 * <p>依赖：无外部依赖
 */
public record CaseArtifactLinkResponse(
        /** 制品类型：TRACE、VIDEO、SCREENSHOT、LOG、OTHER */
        String artifactType,
        /** 制品标签（小写化的类型名称） */
        String label,
        /** 作用域：TASK（任务级）或 CASE（用例级） */
        String scope,
        /** 制品访问URL */
        String url) {
}
