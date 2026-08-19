package com.example.platform.scene.model;

import java.time.LocalDateTime;

/**
 * 场景调度状态实体 —— 对应 scene_schedule_state 表，存储每个场景的调度跟踪信息与租约。
 *
 * <p>核心字段：
 * <ul>
 *   <li>sceneId：场景 ID（主键）</li>
 *   <li>lastPlannedFireAt：上次计划触发时间</li>
 *   <li>lastTriggeredAt：上次实际触发时间</li>
 *   <li>lastTaskId：上次触发创建的任务 ID</li>
 *   <li>leaseOwner / leaseUntil：调度租约（多实例并发控制）</li>
 *   <li>version：乐观锁版本号</li>
 * </ul>
 */
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
