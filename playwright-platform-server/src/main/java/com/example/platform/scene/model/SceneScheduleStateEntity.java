package com.example.platform.scene.model;

import java.time.LocalDateTime;

public class SceneScheduleStateEntity {
    private Long sceneId;

    private LocalDateTime lastPlannedFireAt;

    private LocalDateTime lastTriggeredAt;

    private Long lastTaskId;

    private String leaseOwner;

    private LocalDateTime leaseUntil;

    private Long version = 0L;

    private LocalDateTime updatedAt;

    public Long getSceneId() { return sceneId; }
    public void setSceneId(Long sceneId) { this.sceneId = sceneId; }
    public LocalDateTime getLastPlannedFireAt() { return lastPlannedFireAt; }
    public void setLastPlannedFireAt(LocalDateTime lastPlannedFireAt) { this.lastPlannedFireAt = lastPlannedFireAt; }
    public LocalDateTime getLastTriggeredAt() { return lastTriggeredAt; }
    public void setLastTriggeredAt(LocalDateTime lastTriggeredAt) { this.lastTriggeredAt = lastTriggeredAt; }
    public Long getLastTaskId() { return lastTaskId; }
    public void setLastTaskId(Long lastTaskId) { this.lastTaskId = lastTaskId; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime leaseUntil) { this.leaseUntil = leaseUntil; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
