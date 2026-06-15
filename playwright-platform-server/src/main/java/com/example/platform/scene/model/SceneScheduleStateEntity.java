package com.example.platform.scene.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "scene_schedule_state")
public class SceneScheduleStateEntity {
    @Id
    @Column(name = "scene_id")
    private Long sceneId;

    @Column(name = "last_planned_fire_at")
    private LocalDateTime lastPlannedFireAt;

    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    @Column(name = "last_task_id")
    private Long lastTaskId;

    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "updated_at", insertable = false, updatable = false)
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
