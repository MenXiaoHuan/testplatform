package com.example.platform.task.dto;

import java.util.List;

/**
 * 任务诊断响应 DTO。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装任务诊断信息，用于故障排查</li>
 *   <li>包含当前阶段、结果码、阶段日志数量、最近应用错误等</li>
 * </ul>
 *
 * <p>依赖：{@link ApplicationErrorSummaryResponse}
 */
public record TaskDiagnosticsResponse(
        /** 任务ID */
        Long taskId,
        /** 当前执行阶段 */
        String currentStage,
        /** 结果码 */
        String resultCode,
        /** 结果消息 */
        String resultMessage,
        /** 额外诊断信息 */
        String additionalDiagnostic,
        /** 阶段日志数量 */
        int stageLogCount,
        /** 最近的应用程序错误列表 */
        List<ApplicationErrorSummaryResponse> recentApplicationErrors) {
}
