package com.example.platform.evaluation.online;

import java.time.LocalDateTime;

/** 在线评测策略实体（V11 eval_online_policy）。 */
public class EvalOnlinePolicyEntity {
    private Long id;
    private Long spaceId;
    private Long targetId;
    private String name;
    private String status;
    private String filterConfig;
    private String evaluatorConfigs;
    private String alertingConfig;
    private String rateLimitConfig;
    private Integer scanIntervalMinutes;
    private LocalDateTime nextScanAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long v) { this.spaceId = v; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long v) { this.targetId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getFilterConfig() { return filterConfig; }
    public void setFilterConfig(String v) { this.filterConfig = v; }
    public String getEvaluatorConfigs() { return evaluatorConfigs; }
    public void setEvaluatorConfigs(String v) { this.evaluatorConfigs = v; }
    public String getAlertingConfig() { return alertingConfig; }
    public void setAlertingConfig(String v) { this.alertingConfig = v; }
    public String getRateLimitConfig() { return rateLimitConfig; }
    public void setRateLimitConfig(String v) { this.rateLimitConfig = v; }
    public Integer getScanIntervalMinutes() { return scanIntervalMinutes; }
    public void setScanIntervalMinutes(Integer v) { this.scanIntervalMinutes = v; }
    public LocalDateTime getNextScanAt() { return nextScanAt; }
    public void setNextScanAt(LocalDateTime v) { this.nextScanAt = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
