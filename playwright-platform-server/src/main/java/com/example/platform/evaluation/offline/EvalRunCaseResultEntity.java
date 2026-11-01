package com.example.platform.evaluation.offline;

import java.time.LocalDateTime;

/** 离线评测样本结果 —— 对应 eval_run_case_result 表。 */
public class EvalRunCaseResultEntity {
    private Long id;
    private Long runId;
    private Long sampleId;
    private Integer sampleIndex;
    private String agentOutput;
    private String goldenAnswer;
    /** JSON 数组：各 Evaluator 的评分明细 */
    private String evaluatorScores;
    private Double weightedScore;
    private Long tokensIn;
    private Long tokensOut;
    private Long durationMs;
    private String externalTraceId;
    /** Langfuse Trace ID —— 报告页据此打开完整链路 */
    private String langfuseTraceId;
    private Long actualPromptVersionId;
    /** SUCCESS / FAILED */
    private String status;
    private String errorMessage;
    /** 关联样本输入（查询时 join eval_dataset_sample 带出，非表字段） */
    private String sampleInputText;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getRunId() { return runId; }
    public void setRunId(Long v) { this.runId = v; }
    public Long getSampleId() { return sampleId; }
    public void setSampleId(Long v) { this.sampleId = v; }
    public Integer getSampleIndex() { return sampleIndex; }
    public void setSampleIndex(Integer v) { this.sampleIndex = v; }
    public String getAgentOutput() { return agentOutput; }
    public void setAgentOutput(String v) { this.agentOutput = v; }
    public String getGoldenAnswer() { return goldenAnswer; }
    public void setGoldenAnswer(String v) { this.goldenAnswer = v; }
    public String getEvaluatorScores() { return evaluatorScores; }
    public void setEvaluatorScores(String v) { this.evaluatorScores = v; }
    public Double getWeightedScore() { return weightedScore; }
    public void setWeightedScore(Double v) { this.weightedScore = v; }
    public Long getTokensIn() { return tokensIn; }
    public void setTokensIn(Long v) { this.tokensIn = v; }
    public Long getTokensOut() { return tokensOut; }
    public void setTokensOut(Long v) { this.tokensOut = v; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long v) { this.durationMs = v; }
    public String getExternalTraceId() { return externalTraceId; }
    public void setExternalTraceId(String v) { this.externalTraceId = v; }
    public String getLangfuseTraceId() { return langfuseTraceId; }
    public void setLangfuseTraceId(String v) { this.langfuseTraceId = v; }
    public Long getActualPromptVersionId() { return actualPromptVersionId; }
    public void setActualPromptVersionId(Long v) { this.actualPromptVersionId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }
    public String getSampleInputText() { return sampleInputText; }
    public void setSampleInputText(String v) { this.sampleInputText = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
