package com.example.platform.scene.service;

/**
 * 场景级联删除服务接口 —— 定义场景删除时的关联数据清理操作。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #deleteSceneGraph} —— 删除场景及其所有关联数据（任务、调度事件、制品、阶段日志、对象存储文件）</li>
 * </ul>
 */
public interface SceneCascadeDeleteService {
    void deleteSceneGraph(Long sceneId);
}
