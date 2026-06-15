package com.example.platform.scene.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;

@Entity
@Table(name = "scene")
public class SceneEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repo_id", nullable = false)
    private Long repoId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(nullable = false, length = 128)
    private String branch;

    @Column(name = "test_selector_type", nullable = false, length = 32)
    private String testSelectorType;

    @Column(name = "test_selector_value", nullable = false, length = 512)
    private String testSelectorValue;

    @Transient
    private String matchValue;

    @Column(name = "project_name", length = 64)
    private String projectName;

    @Column(length = 64)
    private String browser;

    @Column(name = "env_json", columnDefinition = "json")
    private String envJson;

    @Column(name = "run_command", nullable = false, length = 512)
    private String runCommand;

    @Column(name = "schedule_enabled", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean scheduleEnabled = false;

    @Column(name = "cron_expression", length = 64)
    private String cronExpression;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "last_task_status", length = 32)
    private String lastTaskStatus;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
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
