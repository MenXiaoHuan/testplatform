package com.example.platform.task.dto;

import com.example.platform.task.model.ArtifactEntity;
import java.util.List;

public record TaskReportSummaryResponse(
        TaskDetailResponse task,
        String reportStatus,
        String reportUrl,
        List<ArtifactEntity> artifacts,
        List<CaseResultResponse> caseResults,
        TaskCaseSummaryResponse caseSummary,
        TaskArtifactSummaryResponse artifactSummary,
        List<TaskProjectStatResponse> projectStats,
        int artifactCount,
        boolean reportReady) {
}
