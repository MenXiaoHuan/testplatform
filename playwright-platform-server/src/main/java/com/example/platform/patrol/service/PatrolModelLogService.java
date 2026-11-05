package com.example.platform.patrol.service;

import com.example.platform.patrol.dto.PatrolModelLogResponse;
import com.example.platform.patrol.mapper.PatrolModelLogMapper;
import com.example.platform.patrol.model.AiModelConfigEntity;
import com.example.platform.patrol.model.PatrolModelLogEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/** 记录巡页各阶段模型的完整上下文、原始输出与调用状态。 */
@Service
public class PatrolModelLogService {

    public static final String STAGE_PLAN = "PLAN";
    public static final String STAGE_COVERAGE = "COVERAGE";
    public static final String STAGE_JUDGE = "JUDGE";
    public static final String STAGE_RESOLVE = "RESOLVE";

    private final PatrolModelLogMapper mapper;

    public PatrolModelLogService(PatrolModelLogMapper mapper) {
        this.mapper = mapper;
    }

    public void record(Long taskId, Long caseId, String caseName, String stage, String purpose,
                       AiModelConfigEntity model, String status, String systemPrompt, String userPrompt,
                       String rawOutput, int promptTokens, int completionTokens, long durationMs,
                       String errorMessage) {
        if (taskId == null) {
            return;
        }
        PatrolModelLogEntity entity = new PatrolModelLogEntity();
        entity.setTaskId(taskId);
        entity.setCaseId(caseId);
        entity.setCaseName(caseName);
        entity.setStage(stage);
        entity.setPurpose(purpose);
        entity.setModelId(model == null ? null : model.getModelId());
        entity.setStatus(status);
        entity.setSystemPrompt(systemPrompt);
        entity.setUserPrompt(userPrompt);
        entity.setRawOutput(rawOutput);
        entity.setPromptTokens(Math.max(promptTokens, 0));
        entity.setCompletionTokens(Math.max(completionTokens, 0));
        entity.setDurationMs(Math.max(durationMs, 0));
        entity.setErrorMessage(errorMessage);
        entity.setCreatedAt(LocalDateTime.now());
        mapper.insert(entity);
    }

    public List<PatrolModelLogResponse> list(long taskId) {
        return mapper.findByTaskId(taskId).stream().map(PatrolModelLogResponse::from).toList();
    }
}
