package com.example.platform.evaluation.dataset;

import java.time.LocalDateTime;

/** 评测集实体。 */
public class EvalDatasetEntity {
    private Long id;
    private Long spaceId;
    private String name;
    private String version;
    private String description;
    private String scenarioTags;
    private String recommendedTargetTypes;
    /** 评测集推荐的评估器配置（JSON 数组），启动评测时自动带入。 */
    private String recommendedEvaluators;
    private String externalSource;
    private String builtinKey;
    private Boolean isBuiltin = false;
    private String syncStatus = "IDLE";
    private LocalDateTime lastSyncedAt;
    private String syncError;
    private Integer totalSamples;
    private Integer annotatedSamples;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long v) { this.spaceId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getVersion() { return version; }
    public void setVersion(String v) { this.version = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getScenarioTags() { return scenarioTags; }
    public void setScenarioTags(String v) { this.scenarioTags = v; }
    public String getRecommendedTargetTypes() { return recommendedTargetTypes; }
    public void setRecommendedTargetTypes(String v) { this.recommendedTargetTypes = v; }
    public String getRecommendedEvaluators() { return recommendedEvaluators; }
    public void setRecommendedEvaluators(String v) { this.recommendedEvaluators = v; }
    public String getExternalSource() { return externalSource; }
    public void setExternalSource(String v) { this.externalSource = v; }
    public String getBuiltinKey() { return builtinKey; }
    public void setBuiltinKey(String v) { this.builtinKey = v; }
    public Boolean getIsBuiltin() { return isBuiltin; }
    public void setIsBuiltin(Boolean v) { this.isBuiltin = v; }
    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String v) { this.syncStatus = v; }
    public LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(LocalDateTime v) { this.lastSyncedAt = v; }
    public String getSyncError() { return syncError; }
    public void setSyncError(String v) { this.syncError = v; }
    public Integer getTotalSamples() { return totalSamples; }
    public void setTotalSamples(Integer v) { this.totalSamples = v; }
    public Integer getAnnotatedSamples() { return annotatedSamples; }
    public void setAnnotatedSamples(Integer v) { this.annotatedSamples = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
