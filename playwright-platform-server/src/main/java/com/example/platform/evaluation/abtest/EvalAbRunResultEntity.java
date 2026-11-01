package com.example.platform.evaluation.abtest;

import java.time.LocalDateTime;

/** A/B 运行结果留痕（V11 eval_ab_run_result）。 */
public class EvalAbRunResultEntity {
    private Long id;
    private Long experimentId;
    private String inputText;
    private String controlOutput;
    private String treatmentOutputs;
    private String controlScores;
    private String treatmentScores;
    private String langfuseTraceIds;
    private String promptVersionsUsed;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getExperimentId() { return experimentId; }
    public void setExperimentId(Long v) { this.experimentId = v; }
    public String getInputText() { return inputText; }
    public void setInputText(String v) { this.inputText = v; }
    public String getControlOutput() { return controlOutput; }
    public void setControlOutput(String v) { this.controlOutput = v; }
    public String getTreatmentOutputs() { return treatmentOutputs; }
    public void setTreatmentOutputs(String v) { this.treatmentOutputs = v; }
    public String getControlScores() { return controlScores; }
    public void setControlScores(String v) { this.controlScores = v; }
    public String getTreatmentScores() { return treatmentScores; }
    public void setTreatmentScores(String v) { this.treatmentScores = v; }
    public String getLangfuseTraceIds() { return langfuseTraceIds; }
    public void setLangfuseTraceIds(String v) { this.langfuseTraceIds = v; }
    public String getPromptVersionsUsed() { return promptVersionsUsed; }
    public void setPromptVersionsUsed(String v) { this.promptVersionsUsed = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
