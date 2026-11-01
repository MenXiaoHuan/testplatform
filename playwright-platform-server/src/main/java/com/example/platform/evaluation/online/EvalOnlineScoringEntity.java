package com.example.platform.evaluation.online;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 在线评分留痕（V11 eval_online_scoring）—— C13 断点续跑用。 */
public class EvalOnlineScoringEntity {
    private Long id;
    private Long policyId;
    private String langfuseTraceId;
    private String evaluatorScores;
    private BigDecimal weightedScore;
    private Boolean lowScore;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long v) { this.policyId = v; }
    public String getLangfuseTraceId() { return langfuseTraceId; }
    public void setLangfuseTraceId(String v) { this.langfuseTraceId = v; }
    public String getEvaluatorScores() { return evaluatorScores; }
    public void setEvaluatorScores(String v) { this.evaluatorScores = v; }
    public BigDecimal getWeightedScore() { return weightedScore; }
    public void setWeightedScore(BigDecimal v) { this.weightedScore = v; }
    public Boolean getLowScore() { return lowScore; }
    public void setLowScore(Boolean v) { this.lowScore = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
