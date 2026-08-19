package com.example.platform.scene.service;

import java.time.LocalDateTime;

/**
 * 场景调度服务接口 —— 定义定时触发到期场景的核心调度方法。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #triggerDueScenes} —— 触发所有到期的调度场景（含失败重试、遗留场景初始化）</li>
 * </ul>
 *
 * <p>依赖：{@link SceneMapper}、{@link SceneScheduleLeaseService}、
 * {@link ScheduleEventService}、{@link com.example.platform.task.service.TaskService}。
 */
public interface SceneSchedulerService {
    void triggerDueScenes(LocalDateTime now);
}
