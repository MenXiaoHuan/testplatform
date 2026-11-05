package com.example.platform.patrol.model;

import java.time.LocalDateTime;

/** 巡页场景：可复用的入口页巡检配置，可手动或定时重复执行。 */
public class PatrolSceneEntity {
    private Long id;
    private Long spaceId;
    private String name;
    private String description;
    private String startUrl;
    private String browser;
    private Boolean headless;
    private String authType;
    private String authCookieEncrypted;
    private String authTokenEncrypted;
    private String authTokenHeader;
    private String authLocalStorageEncrypted;
    private Long planModelId;
    private Long judgeModelId;
    private Long resolveModelId;
    private Long supplementModelId;
    private String supplementsJson;
    private Integer budgetDurationMinutes;
    private Integer visibleTextLimit;
    private Boolean scrollScan;
    private Boolean recordVideo;
    private Boolean autoApproveWrite;
    private Boolean autoApproveDestructive;
    private Boolean scheduleEnabled;
    private String cronExpression;
    private LocalDateTime nextRunAt;
    private LocalDateTime lastRunAt;
    private Long lastTaskId;
    private String lastTaskStatus;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStartUrl() { return startUrl; }
    public void setStartUrl(String startUrl) { this.startUrl = startUrl; }
    public String getBrowser() { return browser; }
    public void setBrowser(String browser) { this.browser = browser; }
    public Boolean getHeadless() { return headless; }
    public void setHeadless(Boolean headless) { this.headless = headless; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getAuthCookieEncrypted() { return authCookieEncrypted; }
    public void setAuthCookieEncrypted(String authCookieEncrypted) { this.authCookieEncrypted = authCookieEncrypted; }
    public String getAuthTokenEncrypted() { return authTokenEncrypted; }
    public void setAuthTokenEncrypted(String authTokenEncrypted) { this.authTokenEncrypted = authTokenEncrypted; }
    public String getAuthTokenHeader() { return authTokenHeader; }
    public void setAuthTokenHeader(String authTokenHeader) { this.authTokenHeader = authTokenHeader; }
    public String getAuthLocalStorageEncrypted() { return authLocalStorageEncrypted; }
    public void setAuthLocalStorageEncrypted(String authLocalStorageEncrypted) { this.authLocalStorageEncrypted = authLocalStorageEncrypted; }
    public Long getPlanModelId() { return planModelId; }
    public void setPlanModelId(Long planModelId) { this.planModelId = planModelId; }
    public Long getJudgeModelId() { return judgeModelId; }
    public void setJudgeModelId(Long judgeModelId) { this.judgeModelId = judgeModelId; }
    public Long getResolveModelId() { return resolveModelId; }
    public void setResolveModelId(Long resolveModelId) { this.resolveModelId = resolveModelId; }
    public Long getSupplementModelId() { return supplementModelId; }
    public void setSupplementModelId(Long supplementModelId) { this.supplementModelId = supplementModelId; }
    public String getSupplementsJson() { return supplementsJson; }
    public void setSupplementsJson(String supplementsJson) { this.supplementsJson = supplementsJson; }
    public Integer getBudgetDurationMinutes() { return budgetDurationMinutes; }
    public void setBudgetDurationMinutes(Integer budgetDurationMinutes) { this.budgetDurationMinutes = budgetDurationMinutes; }
    public Integer getVisibleTextLimit() { return visibleTextLimit; }
    public void setVisibleTextLimit(Integer visibleTextLimit) { this.visibleTextLimit = visibleTextLimit; }
    public Boolean getScrollScan() { return scrollScan; }
    public void setScrollScan(Boolean scrollScan) { this.scrollScan = scrollScan; }
    public Boolean getRecordVideo() { return recordVideo; }
    public void setRecordVideo(Boolean recordVideo) { this.recordVideo = recordVideo; }
    public Boolean getAutoApproveWrite() { return autoApproveWrite; }
    public void setAutoApproveWrite(Boolean autoApproveWrite) { this.autoApproveWrite = autoApproveWrite; }
    public Boolean getAutoApproveDestructive() { return autoApproveDestructive; }
    public void setAutoApproveDestructive(Boolean autoApproveDestructive) { this.autoApproveDestructive = autoApproveDestructive; }
    public Boolean getScheduleEnabled() { return scheduleEnabled; }
    public void setScheduleEnabled(Boolean scheduleEnabled) { this.scheduleEnabled = scheduleEnabled; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public LocalDateTime getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(LocalDateTime nextRunAt) { this.nextRunAt = nextRunAt; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public Long getLastTaskId() { return lastTaskId; }
    public void setLastTaskId(Long lastTaskId) { this.lastTaskId = lastTaskId; }
    public String getLastTaskStatus() { return lastTaskStatus; }
    public void setLastTaskStatus(String lastTaskStatus) { this.lastTaskStatus = lastTaskStatus; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
