package com.example.platform.task.dto;

import com.example.platform.task.model.CaseResultEntity;
import java.util.List;

public record CaseResultResponse(
        Long id,
        Long taskId,
        String historyId,
        String fullName,
        String suiteName,
        String storyName,
        String status,
        Long durationMs,
        String projectName,
        String errorMessage,
        String videoUrl,
        String traceUrl,
        List<String> screenshotUrls,
        String logUrl,
        int artifactCount,
        List<CaseArtifactLinkResponse> artifacts) {
    public static CaseResultResponse from(CaseResultEntity entity, int artifactCount) {
        return from(entity, null, null, null, List.of(), null, artifactCount, List.of());
    }

    public static CaseResultResponse from(
            CaseResultEntity entity,
            String errorMessage,
            String videoUrl,
            String traceUrl,
            List<String> screenshotUrls,
            String logUrl,
            int artifactCount,
            List<CaseArtifactLinkResponse> artifacts) {
        return new CaseResultResponse(
                entity.getId(),
                entity.getTaskId(),
                entity.getHistoryId(),
                entity.getFullName(),
                entity.getSuiteName(),
                entity.getStoryName(),
                entity.getStatus(),
                entity.getDurationMs(),
                entity.getProjectName(),
                errorMessage,
                videoUrl,
                traceUrl,
                screenshotUrls,
                logUrl,
                artifactCount,
                artifacts);
    }
}
