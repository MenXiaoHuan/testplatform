package com.example.platform.patrol.model;

import java.time.LocalDateTime;

/** 用例步骤：动作、操作风险等级、执行结果与证据。 */
public class PatrolCaseStepEntity {
    private Long id;
    private Long caseId;
    private Integer seq;
    private String action;
    private String targetSelector;
    private String locatorJson;
    private String targetLabel;
    private String inputValue;
    private String operation;
    private String risk;
    private Boolean approved;
    private String status;
    private String message;
    private String screenshotKey;
    private String requestJson;
    private Long durationMs;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetSelector() { return targetSelector; }
    public void setTargetSelector(String targetSelector) { this.targetSelector = targetSelector; }
    public String getLocatorJson() { return locatorJson; }
    public void setLocatorJson(String locatorJson) { this.locatorJson = locatorJson; }
    public String getTargetLabel() { return targetLabel; }
    public void setTargetLabel(String targetLabel) { this.targetLabel = targetLabel; }
    public String getInputValue() { return inputValue; }
    public void setInputValue(String inputValue) { this.inputValue = inputValue; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getRisk() { return risk; }
    public void setRisk(String risk) { this.risk = risk; }
    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean approved) { this.approved = approved; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getScreenshotKey() { return screenshotKey; }
    public void setScreenshotKey(String screenshotKey) { this.screenshotKey = screenshotKey; }
    public String getRequestJson() { return requestJson; }
    public void setRequestJson(String requestJson) { this.requestJson = requestJson; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
