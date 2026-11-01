package com.example.platform.evaluation.offline;

import java.time.LocalDateTime;

/** 离线评测 Run 实体 —— 对应 eval_run 表。 */
public class EvalRunEntity {
    private Long id;
    private Long spaceId;
    private Long targetId;
    private Long promptVersionId;
    private Long datasetId;
    /** PENDING / RUNNING / COMPLETED / FAILED / CANCELLED */
    private String status;
    private Integer totalSamples;
    private Integer completedSamples;
    private Integer failedSamples;
    private Double overallScore;
    private String evaluatorConfigs;
    private Integer maxConcurrency;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String triggerUser;
    /** C20 乐观锁 */
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long v) { this.spaceId = v; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long v) { this.targetId = v; }
    public Long getPromptVersionId() { return promptVersionId; }
    public void setPromptVersionId(Long v) { this.promptVersionId = v; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long v) { this.datasetId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Integer getTotalSamples() { return totalSamples; }
    public void setTotalSamples(Integer v) { this.totalSamples = v; }
    public Integer getCompletedSamples() { return completedSamples; }
    public void setCompletedSamples(Integer v) { this.completedSamples = v; }
    public Integer getFailedSamples() { return failedSamples; }
    public void setFailedSamples(Integer v) { this.failedSamples = v; }
    public Double getOverallScore() { return overallScore; }
    public void setOverallScore(Double v) { this.overallScore = v; }
    public String getEvaluatorConfigs() { return evaluatorConfigs; }
    public void setEvaluatorConfigs(String v) { this.evaluatorConfigs = v; }
    public Integer getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(Integer v) { this.maxConcurrency = v; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime v) { this.startedAt = v; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime v) { this.finishedAt = v; }
    public String getTriggerUser() { return triggerUser; }
    public void setTriggerUser(String v) { this.triggerUser = v; }
    public Long getVersion() { return version; }
    public void setVersion(Long v) { this.version = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
