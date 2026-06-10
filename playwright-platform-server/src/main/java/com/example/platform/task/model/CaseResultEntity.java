package com.example.platform.task.model;

import jakarta.persistence.*;

@Entity
@Table(name = "case_result")
public class CaseResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "history_id", length = 256)
    private String historyId;

    @Column(name = "full_name", nullable = false, length = 512)
    private String fullName;

    @Column(name = "suite_name", length = 256)
    private String suiteName;

    @Column(name = "story_name", length = 256)
    private String storyName;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "owner_name", length = 128)
    private String ownerName;

    @Column(length = 64)
    private String severity;

    @Column(name = "project_name", length = 64)
    private String projectName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getHistoryId() { return historyId; }
    public void setHistoryId(String historyId) { this.historyId = historyId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getSuiteName() { return suiteName; }
    public void setSuiteName(String suiteName) { this.suiteName = suiteName; }
    public String getStoryName() { return storyName; }
    public void setStoryName(String storyName) { this.storyName = storyName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
}
