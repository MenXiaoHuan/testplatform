package com.example.platform.task.service;

import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.mapper.TaskMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCreationService {
    private static final Collection<String> ACTIVE_TASK_STATUSES = List.of("QUEUED", "RUNNING");

    private final SceneMapper sceneMapper;
    private final TestRepositoryMapper repositoryMapper;
    private final TaskMapper taskRepository;
    private final TaskCommandBuilder taskCommandBuilder;

    public TaskCreationService(
            SceneMapper sceneMapper,
            TestRepositoryMapper repositoryMapper,
            TaskMapper taskRepository,
            TaskCommandBuilder taskCommandBuilder) {
        this.sceneMapper = sceneMapper;
        this.repositoryMapper = repositoryMapper;
        this.taskRepository = taskRepository;
        this.taskCommandBuilder = taskCommandBuilder;
    }

    @Transactional
    public TaskEntity createTask(Long sceneId, String triggerType, String triggerReason, String triggerUser) {
        SceneEntity scene = sceneMapper.findByIdForUpdate(sceneId)
                .or(() -> sceneMapper.findById(sceneId))
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + sceneId));
        TestRepositoryEntity repository = repositoryMapper.findById(scene.getRepoId())
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + scene.getRepoId()));
        if (!Boolean.TRUE.equals(repository.getEnabled())) {
            throw new IllegalArgumentException("所属仓库已停用，请先启用仓库");
        }
        if (taskRepository.existsBySceneIdAndStatusIn(sceneId, ACTIVE_TASK_STATUSES)) {
            throw new IllegalStateException("当前场景已有执行中的任务，请稍后再试");
        }

        TaskEntity task = new TaskEntity();
        String resolvedBranch = scene.getBranch() != null && !scene.getBranch().isBlank()
                ? scene.getBranch()
                : repository.getDefaultBranch();
        String resolvedRunCommand = taskCommandBuilder.buildRunCommand(repository, scene);
        task.setSceneId(scene.getId());
        task.setRepoId(repository.getId());
        task.setStatus("QUEUED");
        task.setCurrentStage("QUEUED");
        task.setTriggerType(triggerType);
        task.setTriggerReason(triggerReason);
        task.setTriggerUser(triggerUser);
        task.setBranch(resolvedBranch);
        task.setRunnerName("centralized-runner");
        task.setResolvedBranch(resolvedBranch);
        task.setResolvedBrowser(scene.getBrowser());
        task.setResolvedEnvJson(scene.getEnvJson());
        task.setResolvedMatchValue(scene.getMatchValue());
        task.setResolvedTestRoot(repository.getTestRoot());
        task.setResolvedRunCommand(resolvedRunCommand);
        task.setQueuedAt(LocalDateTime.now());
        taskRepository.insert(task);

        scene.setLastTaskStatus(task.getStatus());
        scene.setLastRunAt(task.getQueuedAt());
        sceneMapper.update(scene);
        return task;
    }
}
