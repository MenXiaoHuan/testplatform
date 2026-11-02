package com.example.platform.patrol.dto;

/** 巡页执行历史汇总：平均 Token、平均元素覆盖率与平均耗时。 */
public record PatrolTaskSummaryResponse(
        long taskCount,
        long avgLlmTokens,
        double avgCoverageRate,
        double avgDurationMinutes
) {
}
