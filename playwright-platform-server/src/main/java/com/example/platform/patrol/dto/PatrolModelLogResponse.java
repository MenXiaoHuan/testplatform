package com.example.platform.patrol.dto;

import com.example.platform.patrol.model.PatrolModelLogEntity;
import java.time.LocalDateTime;

/** 巡页模型日志：包含阶段上下文、完整原始输出与调用状态。 */
public record PatrolModelLogResponse(
        Long id,
        Long taskId,
        Long caseId,
        String caseName,
        String stage,
        String purpose,
        String modelId,
        String status,
        String systemPrompt,
        String userPrompt,
        String rawOutput,
        int promptTokens,
        int completionTokens,
        long durationMs,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static PatrolModelLogResponse from(PatrolModelLogEntity entity) {
        return new PatrolModelLogResponse(
                entity.getId(), entity.getTaskId(), entity.getCaseId(), entity.getCaseName(),
                entity.getStage(), entity.getPurpose(), entity.getModelId(), entity.getStatus(),
                entity.getSystemPrompt(), entity.getUserPrompt(), entity.getRawOutput(),
                entity.getPromptTokens(), entity.getCompletionTokens(), entity.getDurationMs(),
                entity.getErrorMessage(), entity.getCreatedAt()
        );
    }
}
