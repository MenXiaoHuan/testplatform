package com.example.platform.task.model;

import java.time.LocalDateTime;

/**
 * 任务实体类。
 *
 * <p>核心职责：
 * <ul>
 *   <li>映射 task 数据库表</li>
 *   <li>存储任务的完整生命周期信息</li>
 *   <li>包含触发方式、执行配置、状态转换、结果统计等字段</li>
 * </ul>
 *
 * <p>依赖：{@link LocalDateTime}
 */
public class TaskEntity {
    /** 任务ID */
    private Long id;

    /** 所属空间ID */
    private Long spaceId;

    /** 所属场景ID */
    private Long sceneId;

    /** 所属仓库ID */
    private Long repoId;

    /** 任务状态：QUEUED、RUNNING、SUCCESS、FAILED、CANCELED 等 */
    private String status;

    /** 触发类型：MANUAL、SCHEDULED 等 */
    private String triggerType;

    /** 触发原因 */
    private String triggerReason;

    /** 触发用户 */
    private String triggerUser;

    /** 排队时间 */
    private LocalDateTime queuedAt;

    /** 分支名称 */
    private String branch;

    /** 提交SHA */
    private String commitSha;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    /** 执行耗时（毫秒） */
    private Long durationMs;

    /** 执行器名称 */
    private String runnerName;

    /** 当前执行阶段 */
    private String currentStage;

    /** 结果码 */
    private String resultCode;

    /** 结果消息 */
    private String resultMessage;

    /** 是否已请求取消 */
    private Boolean cancelRequested = false;

    /** 取消请求时间 */
    private LocalDateTime cancelRequestedAt;

    /** 请求取消的用户 */
    private String cancelRequestedBy;

    /** 日志URL */
    private String logUrl;

    /** 解析后的分支 */
    private String resolvedBranch;

    /** 解析后的浏览器 */
    private String resolvedBrowser;

    /** 解析后的环境变量JSON */
    private String resolvedEnvJson;

    /** 解析后的匹配值 */
    private String resolvedMatchValue;

    /** 解析后的测试根目录 */
    private String resolvedTestRoot;

    /** 解析后的运行命令 */
    private String resolvedRunCommand;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 通过用例数 */
    private int passedCount;

    /** 失败用例数 */
    private int failedCount;

    /** 跳过用例数 */
    private int skippedCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
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
