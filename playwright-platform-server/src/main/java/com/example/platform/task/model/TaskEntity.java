package com.example.platform.task.model;

import java.time.LocalDateTime;

public class TaskEntity {
    private Long id;

    private Long sceneId;

    private Long repoId;

    private String status;

    private String triggerType;

    private String triggerReason;

    private String triggerUser;

    private LocalDateTime queuedAt;

    private String branch;

    private String commitSha;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long durationMs;

    private String runnerName;

    private String currentStage;

    private String resultCode;

    private String resultMessage;

    private Boolean cancelRequested = false;

    private LocalDateTime cancelRequestedAt;

    private String cancelRequestedBy;

    private String logUrl;

    private String resolvedBranch;

    private String resolvedBrowser;

    private String resolvedEnvJson;

    private String resolvedMatchValue;

    private String resolvedTestRoot;

    private String resolvedRunCommand;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private int passedCount;

    private int failedCount;

    private int skippedCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSceneId() { return sceneId; }
    public void setSceneId(Long sceneId) { this.sceneId = sceneId; }
    public Long getRepoId() { return repoId; }
    public void setRepoId(Long repoId) { this.repoId = repoId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getTriggerReason() { return triggerReason; }
    public void setTriggerReason(String triggerReason) { this.triggerReason = triggerReason; }
    public String getTriggerUser() { return triggerUser; }
    public void setTriggerUser(String triggerUser) { this.triggerUser = triggerUser; }
    public LocalDateTime getQueuedAt() { return queuedAt; }
    public void setQueuedAt(LocalDateTime queuedAt) { this.queuedAt = queuedAt; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getRunnerName() { return runnerName; }
    public void setRunnerName(String runnerName) { this.runnerName = runnerName; }
    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }
    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public String getResultMessage() { return resultMessage; }
    public void setResultMessage(String resultMessage) { this.resultMessage = resultMessage; }
    public Boolean getCancelRequested() { return cancelRequested; }
    public void setCancelRequested(Boolean cancelRequested) { this.cancelRequested = cancelRequested; }
    public LocalDateTime getCancelRequestedAt() { return cancelRequestedAt; }
    public void setCancelRequestedAt(LocalDateTime cancelRequestedAt) { this.cancelRequestedAt = cancelRequestedAt; }
    public String getCancelRequestedBy() { return cancelRequestedBy; }
    public void setCancelRequestedBy(String cancelRequestedBy) { this.cancelRequestedBy = cancelRequestedBy; }
    public String getLogUrl() { return logUrl; }
    public void setLogUrl(String logUrl) { this.logUrl = logUrl; }
    public String getResolvedBranch() { return resolvedBranch; }
    public void setResolvedBranch(String resolvedBranch) { this.resolvedBranch = resolvedBranch; }
    public String getResolvedBrowser() { return resolvedBrowser; }
    public void setResolvedBrowser(String resolvedBrowser) { this.resolvedBrowser = resolvedBrowser; }
    public String getResolvedEnvJson() { return resolvedEnvJson; }
    public void setResolvedEnvJson(String resolvedEnvJson) { this.resolvedEnvJson = resolvedEnvJson; }
    public String getResolvedMatchValue() { return resolvedMatchValue; }
    public void setResolvedMatchValue(String resolvedMatchValue) { this.resolvedMatchValue = resolvedMatchValue; }
    public String getResolvedTestRoot() { return resolvedTestRoot; }
    public void setResolvedTestRoot(String resolvedTestRoot) { this.resolvedTestRoot = resolvedTestRoot; }
    public String getResolvedRunCommand() { return resolvedRunCommand; }
    public void setResolvedRunCommand(String resolvedRunCommand) { this.resolvedRunCommand = resolvedRunCommand; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public int getPassedCount() { return passedCount; }
    public void setPassedCount(int passedCount) { this.passedCount = passedCount; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public int getSkippedCount() { return skippedCount; }
    public void setSkippedCount(int skippedCount) { this.skippedCount = skippedCount; }
}
