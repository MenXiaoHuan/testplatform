package com.example.platform.patrol.model;

import java.time.LocalDateTime;

/** 巡页任务：场景的一次执行，配置以快照保存。 */
public class PatrolTaskEntity {
    private Long id;
    private Long spaceId;
    private Long sceneId;
    private Long parentTaskId;
    private String entryType;
    private String entryLabel;
    private String setupStepsJson;
    private String sceneName;
    private String triggerType;
    private String status;
    private String currentStage;
    private String startUrl;
    private String taskBrief;
    private String planningNote;
    private String sceneSnapshotJson;
    private String modelSnapshotJson;
    private String snapshotJson;
    private String snapshotScreenshotKey;
    private String approvalJson;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private Integer budgetDurationMinutes;
    private Integer caseCount;
    private Integer casePassCount;
    private Integer caseFailCount;
    private Integer caseSkipCount;
    private Integer findingP0Count;
    private Integer findingP1Count;
    private Integer findingP2Count;
    private Integer llmTokenUsed;
    private Boolean degraded;
    private String resultMessage;
    /** 可执行解决建议（JSON 字符串数组），由失败归因生成，供概览展示。 */
    private String adviceJson;
    private Boolean cancelRequested;
    private LocalDateTime retentionUntil;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public Long getSceneId() { return sceneId; }
    public void setSceneId(Long sceneId) { this.sceneId = sceneId; }
    public Long getParentTaskId() { return parentTaskId; }
    public void setParentTaskId(Long parentTaskId) { this.parentTaskId = parentTaskId; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public String getEntryLabel() { return entryLabel; }
    public void setEntryLabel(String entryLabel) { this.entryLabel = entryLabel; }
    public String getSetupStepsJson() { return setupStepsJson; }
    public void setSetupStepsJson(String setupStepsJson) { this.setupStepsJson = setupStepsJson; }
    public String getSceneName() { return sceneName; }
    public void setSceneName(String sceneName) { this.sceneName = sceneName; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }
    public String getStartUrl() { return startUrl; }
    public void setStartUrl(String startUrl) { this.startUrl = startUrl; }
    public String getTaskBrief() { return taskBrief; }
    public void setTaskBrief(String taskBrief) { this.taskBrief = taskBrief; }
    public String getPlanningNote() { return planningNote; }
    public void setPlanningNote(String planningNote) { this.planningNote = planningNote; }
    public String getSceneSnapshotJson() { return sceneSnapshotJson; }
    public void setSceneSnapshotJson(String sceneSnapshotJson) { this.sceneSnapshotJson = sceneSnapshotJson; }
    public String getModelSnapshotJson() { return modelSnapshotJson; }
    public void setModelSnapshotJson(String modelSnapshotJson) { this.modelSnapshotJson = modelSnapshotJson; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public String getSnapshotScreenshotKey() { return snapshotScreenshotKey; }
    public void setSnapshotScreenshotKey(String snapshotScreenshotKey) { this.snapshotScreenshotKey = snapshotScreenshotKey; }
    public String getApprovalJson() { return approvalJson; }
    public void setApprovalJson(String approvalJson) { this.approvalJson = approvalJson; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public Integer getBudgetDurationMinutes() { return budgetDurationMinutes; }
    public void setBudgetDurationMinutes(Integer budgetDurationMinutes) { this.budgetDurationMinutes = budgetDurationMinutes; }
    public Integer getCaseCount() { return caseCount; }
    public void setCaseCount(Integer caseCount) { this.caseCount = caseCount; }
    public Integer getCasePassCount() { return casePassCount; }
    public void setCasePassCount(Integer casePassCount) { this.casePassCount = casePassCount; }
    public Integer getCaseFailCount() { return caseFailCount; }
    public void setCaseFailCount(Integer caseFailCount) { this.caseFailCount = caseFailCount; }
    public Integer getCaseSkipCount() { return caseSkipCount; }
    public void setCaseSkipCount(Integer caseSkipCount) { this.caseSkipCount = caseSkipCount; }
    public Integer getFindingP0Count() { return findingP0Count; }
    public void setFindingP0Count(Integer findingP0Count) { this.findingP0Count = findingP0Count; }
    public Integer getFindingP1Count() { return findingP1Count; }
    public void setFindingP1Count(Integer findingP1Count) { this.findingP1Count = findingP1Count; }
    public Integer getFindingP2Count() { return findingP2Count; }
    public void setFindingP2Count(Integer findingP2Count) { this.findingP2Count = findingP2Count; }
    public Integer getLlmTokenUsed() { return llmTokenUsed; }
    public void setLlmTokenUsed(Integer llmTokenUsed) { this.llmTokenUsed = llmTokenUsed; }
    public Boolean getDegraded() { return degraded; }
    public void setDegraded(Boolean degraded) { this.degraded = degraded; }
    public String getResultMessage() { return resultMessage; }
    public void setResultMessage(String resultMessage) { this.resultMessage = resultMessage; }
    public String getAdviceJson() { return adviceJson; }
    public void setAdviceJson(String adviceJson) { this.adviceJson = adviceJson; }
    public Boolean getCancelRequested() { return cancelRequested; }
    public void setCancelRequested(Boolean cancelRequested) { this.cancelRequested = cancelRequested; }
    public LocalDateTime getRetentionUntil() { return retentionUntil; }
    public void setRetentionUntil(LocalDateTime retentionUntil) { this.retentionUntil = retentionUntil; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
