package com.example.platform.task.dto;

/**
 * 任务用例统计摘要响应 DTO。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装任务执行后的用例统计数据</li>
 *   <li>包含通过、失败、跳过的用例数及总数</li>
 * </ul>
 *
 * <p>依赖：无外部依赖
 */
public record TaskCaseSummaryResponse(
        /** 通过用例数 */
        int passed,
        /** 失败用例数 */
        int failed,
        /** 跳过用例数 */
        int skipped,
        /** 用例总数 */
        int total) {
}
