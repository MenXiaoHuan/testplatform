package com.example.platform.patrol.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** AI 模型配置：模型由空间自行新建。 */
public class AiModelConfigEntity {
    private Long id;
    private Long spaceId;
    private String name;
    private String providerType;
    private String baseUrl;
    private String apiKeyEncrypted;
    private String modelId;
    private Boolean supportsVision;
    private Boolean supportsTools;
    private Boolean supportsJson;
    private Integer maxTokens;
    private BigDecimal temperature;
    private Boolean enabled;
    private String lastTestStatus;
    private String lastTestMessage;
    private LocalDateTime lastTestedAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKeyEncrypted() { return apiKeyEncrypted; }
    public void setApiKeyEncrypted(String apiKeyEncrypted) { this.apiKeyEncrypted = apiKeyEncrypted; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public Boolean getSupportsVision() { return supportsVision; }
    public void setSupportsVision(Boolean supportsVision) { this.supportsVision = supportsVision; }
    public Boolean getSupportsTools() { return supportsTools; }
    public void setSupportsTools(Boolean supportsTools) { this.supportsTools = supportsTools; }
    public Boolean getSupportsJson() { return supportsJson; }
    public void setSupportsJson(Boolean supportsJson) { this.supportsJson = supportsJson; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getLastTestStatus() { return lastTestStatus; }
    public void setLastTestStatus(String lastTestStatus) { this.lastTestStatus = lastTestStatus; }
    public String getLastTestMessage() { return lastTestMessage; }
    public void setLastTestMessage(String lastTestMessage) { this.lastTestMessage = lastTestMessage; }
    public LocalDateTime getLastTestedAt() { return lastTestedAt; }
    public void setLastTestedAt(LocalDateTime lastTestedAt) { this.lastTestedAt = lastTestedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
