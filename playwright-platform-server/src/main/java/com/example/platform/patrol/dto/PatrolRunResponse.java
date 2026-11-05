package com.example.platform.patrol.dto;

import com.example.platform.patrol.model.PatrolTaskEntity;
import java.time.LocalDateTime;

/** 任务中的一个 Run：主入口或某个补充入口。 */
public record PatrolRunResponse(
        Long runId,
        String entryType,
        String entryLabel,
        String startUrl,
        String status,
        String currentStage,
        String resultMessage,
        int caseCount,
        int casePassCount,
        int caseFailCount,
        int caseSkipCount,
        int findingP0Count,
        int findingP1Count,
        int findingP2Count,
        int llmTokenUsed,
        boolean degraded,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMs,
        int coverageTotal,
        int coverageCovered,
        double coverageRate
) {
    public static PatrolRunResponse from(PatrolTaskEntity task, int coverageTotal,
                                         int coverageCovered, double coverageRate) {
        return from(task,
                task.getCaseCount() == null ? 0 : task.getCaseCount(),
                task.getCasePassCount() == null ? 0 : task.getCasePassCount(),
                task.getCaseFailCount() == null ? 0 : task.getCaseFailCount(),
                task.getCaseSkipCount() == null ? 0 : task.getCaseSkipCount(),
                task.getFindingP0Count() == null ? 0 : task.getFindingP0Count(),
                task.getFindingP1Count() == null ? 0 : task.getFindingP1Count(),
                task.getFindingP2Count() == null ? 0 : task.getFindingP2Count(),
                task.getLlmTokenUsed() == null ? 0 : task.getLlmTokenUsed(),
                coverageTotal, coverageCovered, coverageRate);
    }

    /** 显式统计口径：主入口的自身统计需要从汇总值中扣除补充入口。 */
    public static PatrolRunResponse from(PatrolTaskEntity task, int caseCount, int casePassCount,
                                         int caseFailCount, int caseSkipCount, int findingP0Count,
                                         int findingP1Count, int findingP2Count, int llmTokenUsed,
                                         int coverageTotal, int coverageCovered, double coverageRate) {
        return new PatrolRunResponse(
                task.getId(),
                task.getEntryType() == null ? "MAIN" : task.getEntryType(),
                task.getEntryLabel(),
                task.getStartUrl(),
                task.getStatus(),
                task.getCurrentStage(),
                task.getResultMessage(),
                caseCount,
                casePassCount,
                caseFailCount,
                caseSkipCount,
                findingP0Count,
                findingP1Count,
                findingP2Count,
                llmTokenUsed,
                Boolean.TRUE.equals(task.getDegraded()),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getDurationMs(),
                coverageTotal,
                coverageCovered,
                coverageRate);
    }
}
