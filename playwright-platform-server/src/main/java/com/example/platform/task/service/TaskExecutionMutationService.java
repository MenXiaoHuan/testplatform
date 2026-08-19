package com.example.platform.task.service;

import com.example.platform.cache.DetailCacheService;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.task.mapper.TaskMapper;
import com.example.platform.task.model.TaskEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务执行变更服务 —— 负责任务执行流程中的短暂数据库事务操作。
 *
 * <p>编排器有意避免在外部进程执行周围使用一个大事务，每个状态转换
 * 都在此独立持久化，并在写入后使受影响的详情缓存条目失效。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #saveTask(TaskEntity)} —— 更新任务状态并刷新缓存</li>
 *   <li>{@link #saveTaskAndScene(TaskEntity, SceneEntity)} —— 同时更新任务和场景状态</li>
 *   <li>{@link #refreshSceneSummary(SceneEntity, TaskEntity)} —— 根据最新任务结果刷新场景摘要信息</li>
 * </ul>
 *
 * <p>依赖：{@link TaskMapper}、{@link SceneMapper}、{@link DetailCacheService}（可选的缓存失效服务）。
 */
@Service
public class TaskExecutionMutationService {
    private final TaskMapper taskRepository;
    private final SceneMapper sceneMapper;
    private final DetailCacheService detailCacheService;

    @Autowired
    public TaskExecutionMutationService(
            TaskMapper taskRepository,
            SceneMapper sceneMapper,
            DetailCacheService detailCacheService) {
        this.taskRepository = taskRepository;
        this.sceneMapper = sceneMapper;
        this.detailCacheService = detailCacheService;
    }

    public TaskExecutionMutationService(
            TaskMapper taskRepository,
            SceneMapper sceneMapper) {
        this(taskRepository, sceneMapper, null);
    }

    /**
     * 保存任务状态变更，并失效对应的详情缓存。
     */
    @Transactional
    public void saveTask(TaskEntity task) {
        taskRepository.update(task);
        invalidateTaskDetail(task.getSpaceId(), task.getId());
    }

    /**
     * 同时保存任务和场景的状态变更，失效双方的缓存。
     */
    @Transactional
    public void saveTaskAndScene(TaskEntity task, SceneEntity scene) {
        taskRepository.update(task);
        invalidateTaskDetail(task.getSpaceId(), task.getId());
        sceneMapper.update(scene);
        invalidateSceneDetail(scene.getSpaceId(), scene.getId());
    }

    /**
     * 刷新场景摘要信息：查询该场景最新的任务结果，更新场景的最后运行时间和状态。
     */
    @Transactional
    public void refreshSceneSummary(SceneEntity scene, TaskEntity task) {
        // 查找该场景最新的任务作为摘要来源，找不到则使用当前任务
        TaskEntity summarySource = taskRepository.findFirstBySceneIdOrderByCreatedAtDescIdDesc(scene.getId())
                .orElse(task);
        scene.setLastRunAt(summarySource.getFinishedAt());
        scene.setLastTaskStatus(summarySource.getStatus());
        sceneMapper.update(scene);
        invalidateSceneDetail(scene.getSpaceId(), scene.getId());
    }

    /**
     * 失效任务详情缓存。
     */
    private void invalidateTaskDetail(Long spaceId, Long taskId) {
        if (detailCacheService != null && taskId != null) {
            String key = spaceId == null ? "task" : "task:%d".formatted(spaceId);
            detailCacheService.invalidate(key, taskId);
        }
    }

    /**
     * 失效场景详情缓存。
     */
    private void invalidateSceneDetail(Long spaceId, Long sceneId) {
        if (detailCacheService != null && sceneId != null) {
            String key = spaceId == null ? "scene" : "scene:%d".formatted(spaceId);
            detailCacheService.invalidate(key, sceneId);
        }
    }
}
