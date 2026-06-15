package com.example.platform.task.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "task")
public class TaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scene_id", nullable = false)
    private Long sceneId;

    @Column(name = "repo_id", nullable = false)
    private Long repoId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "trigger_type", nullable = false, length = 32)
    private String triggerType;

    @Column(name = "trigger_reason", length = 128)
    private String triggerReason;

    @Column(name = "trigger_user", length = 64)
    private String triggerUser;

    @Column(name = "queued_at")
    private LocalDateTime queuedAt;

    @Column(nullable = false, length = 128)
    private String branch;

    @Column(name = "commit_sha", length = 128)
    private String commitSha;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "runner_name", length = 128)
    private String runnerName;

    @Column(name = "current_stage", length = 32)
    private String currentStage;

    @Column(name = "result_code", length = 32)
    private String resultCode;

    @Column(name = "result_message", length = 1024)
    private String resultMessage;

    @Column(name = "cancel_requested", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean cancelRequested = false;

    @Column(name = "cancel_requested_at")
    private LocalDateTime cancelRequestedAt;

    @Column(name = "cancel_requested_by", length = 64)
    private String cancelRequestedBy;

    @Column(name = "log_url", length = 1024)
    private String logUrl;

    @Column(name = "resolved_branch", length = 128)
    private String resolvedBranch;

    @Column(name = "resolved_browser", length = 32)
    private String resolvedBrowser;

    @Column(name = "resolved_env_json", columnDefinition = "TEXT")
    private String resolvedEnvJson;

    @Column(name = "resolved_match_value", length = 256)
    private String resolvedMatchValue;

    @Column(name = "resolved_test_root", length = 256)
    private String resolvedTestRoot;

    @Column(name = "resolved_run_command", length = 1024)
    private String resolvedRunCommand;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Transient
    private int passedCount;

    @Transient
    private int failedCount;

    @Transient
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
