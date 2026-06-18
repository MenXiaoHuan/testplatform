package com.example.platform.scene.model;

import java.time.LocalDateTime;

public class SceneEntity {
    private Long id;

    private Long repoId;

    private String name;

    private String description;

    private String branch;

    private String testSelectorType;

    private String testSelectorValue;

    private String matchValue;

    private String projectName;

    private String browser;

    private String envJson;

    private String runCommand;

    private Boolean scheduleEnabled = false;

    private String cronExpression;

    private LocalDateTime nextRunAt;

    private LocalDateTime lastRunAt;

    private String lastTaskStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRepoId() { return repoId; }
    public void setRepoId(Long repoId) { this.repoId = repoId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getTestSelectorType() { return testSelectorType; }
    public void setTestSelectorType(String testSelectorType) { this.testSelectorType = testSelectorType; }
    public String getTestSelectorValue() { return testSelectorValue; }
    public void setTestSelectorValue(String testSelectorValue) { this.testSelectorValue = testSelectorValue; }
    public String getMatchValue() {
        if (matchValue != null && !matchValue.isBlank()) {
            return matchValue;
        }
        return testSelectorValue;
    }
    public void setMatchValue(String matchValue) { this.matchValue = matchValue; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getBrowser() { return browser; }
    public void setBrowser(String browser) { this.browser = browser; }
    public String getEnvJson() { return envJson; }
    public void setEnvJson(String envJson) { this.envJson = envJson; }
    public String getRunCommand() { return runCommand; }
    public void setRunCommand(String runCommand) { this.runCommand = runCommand; }
    public Boolean getScheduleEnabled() { return scheduleEnabled; }
    public void setScheduleEnabled(Boolean scheduleEnabled) { this.scheduleEnabled = Boolean.TRUE.equals(scheduleEnabled); }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public LocalDateTime getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(LocalDateTime nextRunAt) { this.nextRunAt = nextRunAt; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public String getLastTaskStatus() { return lastTaskStatus; }
    public void setLastTaskStatus(String lastTaskStatus) { this.lastTaskStatus = lastTaskStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
