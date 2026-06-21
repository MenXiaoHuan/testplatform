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
 * Owns the short database transactions used by the task execution pipeline.
 *
 * <p>The orchestrator intentionally avoids one large transaction around external
 * process execution. Each state transition is persisted independently here and
 * invalidates the affected detail cache entry after the write.
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

    @Transactional
    public void saveTask(TaskEntity task) {
        taskRepository.update(task);
        invalidateTaskDetail(task.getId());
    }

    @Transactional
    public void saveTaskAndScene(TaskEntity task, SceneEntity scene) {
        taskRepository.update(task);
        invalidateTaskDetail(task.getId());
        sceneMapper.update(scene);
        invalidateSceneDetail(scene.getId());
    }

    @Transactional
    public void refreshSceneSummary(SceneEntity scene, TaskEntity task) {
        TaskEntity summarySource = taskRepository.findFirstBySceneIdOrderByCreatedAtDescIdDesc(scene.getId())
                .orElse(task);
        scene.setLastRunAt(summarySource.getFinishedAt());
        scene.setLastTaskStatus(summarySource.getStatus());
        sceneMapper.update(scene);
        invalidateSceneDetail(scene.getId());
    }

    private void invalidateTaskDetail(Long taskId) {
        if (detailCacheService != null && taskId != null) {
            detailCacheService.invalidate("task", taskId);
        }
    }

    private void invalidateSceneDetail(Long sceneId) {
        if (detailCacheService != null && sceneId != null) {
            detailCacheService.invalidate("scene", sceneId);
        }
    }
}
