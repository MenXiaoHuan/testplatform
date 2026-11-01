package com.example.platform.evaluation.abtest;

import java.time.LocalDateTime;

/** A/B 实验实体（V11 eval_ab_experiment）。 */
public class EvalAbExperimentEntity {
    private Long id;
    private Long spaceId;
    private String name;
    private String hypothesis;
    private String status;
    private Long controlTargetId;
    private Long controlPromptVersionId;
    private String treatmentTargetIds;
    private String treatmentPromptVersionIds;
    private Integer trafficPercentage;
    private String coreMetrics;
    private Long datasetId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long v) { this.spaceId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getHypothesis() { return hypothesis; }
    public void setHypothesis(String v) { this.hypothesis = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Long getControlTargetId() { return controlTargetId; }
    public void setControlTargetId(Long v) { this.controlTargetId = v; }
    public Long getControlPromptVersionId() { return controlPromptVersionId; }
    public void setControlPromptVersionId(Long v) { this.controlPromptVersionId = v; }
    public String getTreatmentTargetIds() { return treatmentTargetIds; }
    public void setTreatmentTargetIds(String v) { this.treatmentTargetIds = v; }
    public String getTreatmentPromptVersionIds() { return treatmentPromptVersionIds; }
    public void setTreatmentPromptVersionIds(String v) { this.treatmentPromptVersionIds = v; }
    public Integer getTrafficPercentage() { return trafficPercentage; }
    public void setTrafficPercentage(Integer v) { this.trafficPercentage = v; }
    public String getCoreMetrics() { return coreMetrics; }
    public void setCoreMetrics(String v) { this.coreMetrics = v; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long v) { this.datasetId = v; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime v) { this.startedAt = v; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime v) { this.finishedAt = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
