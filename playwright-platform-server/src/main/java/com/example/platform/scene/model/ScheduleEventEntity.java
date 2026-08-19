package com.example.platform.scene.model;

import java.time.LocalDateTime;

/**
 * 调度事件实体 —— 对应 schedule_event 表，记录每次调度触发（定时/手动/Agent）的完整生命周期。
 *
 * <p>状态流转：ACQUIRED → TASK_CREATED → COMPLETED / FAILED → RETRYING → ABANDONED
 *
 * <p>核心字段：
 * <ul>
 *   <li>基础：id、spaceId、sceneId、plannedFireAt（计划触发时间）</li>
 *   <li>调度类型：scheduleType（CRON / AGENT / MANUAL）</li>
 *   <li>关联：traceId、sessionId、taskId</li>
 *   <li>状态与错误：status、errorMessage、failureCategory</li>
 *   <li>重试：retryCount、nextRetryAt、lastErrorAt</li>
 *   <li>Agent 调度专用：triggerReason、userMessage</li>
 * </ul>
 */
public class ScheduleEventEntity {
    private Long id;
    private Long spaceId;
    private Long sceneId;
    private LocalDateTime plannedFireAt;
    private String status;
    private String scheduleType;
    private String traceId;
    private String sessionId;
    private Long taskId;
    private String triggerReason;
    private String userMessage;
    private String errorMessage;
    private String failureCategory;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime lastErrorAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public Long getSceneId() { return sceneId; }
    public void setSceneId(Long sceneId) { this.sceneId = sceneId; }
    public LocalDateTime getPlannedFireAt() { return plannedFireAt; }
    public void setPlannedFireAt(LocalDateTime plannedFireAt) { this.plannedFireAt = plannedFireAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getScheduleType() { return scheduleType; }
    public void setScheduleType(String scheduleType) { this.scheduleType = scheduleType; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTriggerReason() { return triggerReason; }
    public void setTriggerReason(String triggerReason) { this.triggerReason = triggerReason; }
    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getFailureCategory() { return failureCategory; }
    public void setFailureCategory(String failureCategory) { this.failureCategory = failureCategory; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public LocalDateTime getLastErrorAt() { return lastErrorAt; }
    public void setLastErrorAt(LocalDateTime lastErrorAt) { this.lastErrorAt = lastErrorAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
