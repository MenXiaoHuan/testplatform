package com.example.platform.task.dto;

import java.util.List;

public record TaskDiagnosticsResponse(
        Long taskId,
        String currentStage,
        String resultCode,
        String resultMessage,
        String additionalDiagnostic,
        int stageLogCount,
        List<ApplicationErrorSummaryResponse> recentApplicationErrors) {
}
