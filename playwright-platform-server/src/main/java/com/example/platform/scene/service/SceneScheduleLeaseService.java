package com.example.platform.scene.service;

import java.time.LocalDateTime;

/**
 * 场景调度租约服务接口 —— 定义调度触发时的并发控制操作。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #tryAcquire} —— 尝试获取场景调度租约（乐观锁，确保同一场景不会被多实例重复触发）</li>
 *   <li>{@link #markTriggered} —— 标记场景已触发，记录触发时间与关联任务 ID</li>
 * </ul>
 *
 * <p>依赖：{@link com.example.platform.scene.mapper.SceneScheduleStateMapper}、
 * {@link SchedulerInstanceIdProvider}。
 */
public interface SceneScheduleLeaseService {
    boolean tryAcquire(Long sceneId, LocalDateTime plannedFireAt);
    void markTriggered(Long sceneId, LocalDateTime plannedFireAt, Long taskId, LocalDateTime triggeredAt);
}
