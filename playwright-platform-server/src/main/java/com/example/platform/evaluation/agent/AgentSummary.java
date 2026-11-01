package com.example.platform.evaluation.agent;

import java.time.LocalDateTime;

/** Agent 接入列表摘要（eval_agent_connection + eval_target.name）。 */
public class AgentSummary {
    private Long targetId;
    private String name;
    private String agentKey;
    private String publicKey;
    private String status;
    private LocalDateTime lastSeenAt;
    private Long traceCount;
    private LocalDateTime createdAt;

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long v) { this.targetId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getAgentKey() { return agentKey; }
    public void setAgentKey(String v) { this.agentKey = v; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String v) { this.publicKey = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime v) { this.lastSeenAt = v; }
    public Long getTraceCount() { return traceCount; }
    public void setTraceCount(Long v) { this.traceCount = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
