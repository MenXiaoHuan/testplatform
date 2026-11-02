package com.example.platform.patrol.dto;

import com.example.platform.patrol.model.PatrolCaseEntity;
import java.util.List;

/** 用例响应：一条用例就是一个 Trace 单元。 */
public record PatrolCaseResponse(
        Long id,
        int seq,
        String name,
        String intent,
        String expected,
        String priority,
        String worstRisk,
        String status,
        String execStatus,
        String source,
        String judgeReason,
        String videoKey,
        String consoleKey,
        String domKey,
        String screenshotKey,
        int tokens,
        Long durationMs,
        boolean degraded,
        List<PatrolCaseStepResponse> steps
) {
    public static PatrolCaseResponse from(PatrolCaseEntity entity, List<PatrolCaseStepResponse> steps) {
        return new PatrolCaseResponse(
                entity.getId(),
                entity.getSeq() == null ? 0 : entity.getSeq(),
                entity.getName(),
                entity.getIntent(),
                entity.getExpected(),
                entity.getPriority(),
                entity.getWorstRisk(),
                entity.getStatus(),
                entity.getExecStatus(),
                entity.getSource(),
                entity.getJudgeReason(),
                entity.getVideoKey(),
                entity.getConsoleKey(),
                entity.getDomKey(),
                entity.getScreenshotKey(),
                entity.getTokens() == null ? 0 : entity.getTokens(),
                entity.getDurationMs(),
                Boolean.TRUE.equals(entity.getDegraded()),
                steps);
    }
}
