package com.example.platform.patrol.model;

import java.time.LocalDateTime;

/** 巡页用例：LLM 规划或规则兜底产生的可执行用例。 */
public class PatrolCaseEntity {
    private Long id;
    private Long taskId;
    private Integer seq;
    private String name;
    private String intent;
    private String expected;
    private String priority;
    private String worstRisk;
    private String status;
    private String execStatus;
    private String source;
    private String judgeReason;
    private String videoKey;
    private String consoleKey;
    private String domKey;
    private String screenshotKey;
    private String modelId;
    private Integer tokens;
    private Long durationMs;
    private Boolean degraded;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getExpected() { return expected; }
    public void setExpected(String expected) { this.expected = expected; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getWorstRisk() { return worstRisk; }
    public void setWorstRisk(String worstRisk) { this.worstRisk = worstRisk; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExecStatus() { return execStatus; }
    public void setExecStatus(String execStatus) { this.execStatus = execStatus; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getJudgeReason() { return judgeReason; }
    public void setJudgeReason(String judgeReason) { this.judgeReason = judgeReason; }
    public String getVideoKey() { return videoKey; }
    public void setVideoKey(String videoKey) { this.videoKey = videoKey; }
    public String getConsoleKey() { return consoleKey; }
    public void setConsoleKey(String consoleKey) { this.consoleKey = consoleKey; }
    public String getDomKey() { return domKey; }
    public void setDomKey(String domKey) { this.domKey = domKey; }
    public String getScreenshotKey() { return screenshotKey; }
    public void setScreenshotKey(String screenshotKey) { this.screenshotKey = screenshotKey; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public Integer getTokens() { return tokens; }
    public void setTokens(Integer tokens) { this.tokens = tokens; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public Boolean getDegraded() { return degraded; }
    public void setDegraded(Boolean degraded) { this.degraded = degraded; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
