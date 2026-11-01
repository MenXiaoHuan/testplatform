package com.example.platform.evaluation.agent;

import java.time.LocalDateTime;

/** 外部 Agent 接入凭证与状态 —— 对应 eval_agent_connection 表。 */
public class EvalAgentConnectionEntity {
    private Long id;
    private Long spaceId;
    private Long targetId;
    /** 稳定标识：agent-xxxx */
    private String agentKey;
    /** 对外展示的 Langfuse 风格 public key（pk-lf-xxx） */
    private String publicKey;
    /** SHA-256(publicKey + ":" + secretKey)，不存明文 secret */
    private String secretHash;
    /** CONNECTED / DISABLED */
    private String status;
    private LocalDateTime lastSeenAt;
    private Long traceCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long v) { this.spaceId = v; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long v) { this.targetId = v; }
    public String getAgentKey() { return agentKey; }
    public void setAgentKey(String v) { this.agentKey = v; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String v) { this.publicKey = v; }
    public String getSecretHash() { return secretHash; }
    public void setSecretHash(String v) { this.secretHash = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime v) { this.lastSeenAt = v; }
    public Long getTraceCount() { return traceCount; }
    public void setTraceCount(Long v) { this.traceCount = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
