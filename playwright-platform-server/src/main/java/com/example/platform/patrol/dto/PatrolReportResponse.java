package com.example.platform.patrol.dto;

import java.util.List;

/** 巡页报告：聚合口径以用例为准。 */
public record PatrolReportResponse(
        Long taskId,
        String status,
        String resultMessage,
        boolean degraded,
        boolean visionConfigured,
        CaseStats cases,
        CoverageStats coverage,
        TokenStats tokens,
        List<Long> pendingApprovalCaseIds
) {
    public record CaseStats(int total, int pass, int fail, int skip, double passRate) {
    }

    public record CoverageStats(int totalElements, int coveredElements, double coverageRate) {
    }

    public record TokenStats(int used, int budget) {
    }
}
