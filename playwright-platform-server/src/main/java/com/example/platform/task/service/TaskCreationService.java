package com.example.platform.task.service;

import com.example.platform.cache.DetailCacheService;
import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.mapper.TaskMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务创建服务 —— 负责任务的创建和初始化。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #createTask()} —— 根据场景和仓库信息创建任务</li>
 *   <li>校验仓库是否启用、场景是否有活跃任务</li>
 *   <li>解析分支、浏览器、环境变量等配置</li>
 *   <li>初始化任务状态为 QUEUED 并持久化</li>
 * </ul>
 *
 * <p>依赖：{@link SceneMapper}、{@link TestRepositoryMapper}、{@link TaskMapper}、
 *         {@link TaskCommandBuilder}、{@link DetailCacheService}
 */
@Service
public class TaskCreationService {

    /** 活跃任务状态列表（QUEUED、RUNNING） */
    private static final Collection<String> ACTIVE_TASK_STATUSES = List.of("QUEUED", "RUNNING");

    private final SceneMapper sceneMapper;
    private final TestRepositoryMapper repositoryMapper;
    private final TaskMapper taskRepository;
    private final TaskCommandBuilder taskCommandBuilder;
    private final DetailCacheService detailCacheService;

    @Autowired
    public TaskCreationService(
            SceneMapper sceneMapper,
            TestRepositoryMapper repositoryMapper,
            TaskMapper taskRepository,
            TaskCommandBuilder taskCommandBuilder,
            DetailCacheService detailCacheService) {
        this.sceneMapper = sceneMapper;
        this.repositoryMapper = repositoryMapper;
        this.taskRepository = taskRepository;
        this.taskCommandBuilder = taskCommandBuilder;
        this.detailCacheService = detailCacheService;
    }

    public TaskCreationService(
            SceneMapper sceneMapper,
            TestRepositoryMapper repositoryMapper,
            TaskMapper taskRepository,
            TaskCommandBuilder taskCommandBuilder) {
        this(sceneMapper, repositoryMapper, taskRepository, taskCommandBuilder, null);
    }

    /**
     * 创建任务
     *
     * @param sceneId 场景ID
     * @param triggerType 触发类型（MANUAL、SCHEDULED）
     * @param triggerReason 触发原因
     * @param triggerUser 触发用户
     * @return 创建的任务实体
     * @throws IllegalArgumentException 当场景或仓库不存在、仓库已停用时
     * @throws IllegalStateException 当场景已有活跃任务时
     */
    @Transactional
    public TaskEntity createTask(Long sceneId, String triggerType, String triggerReason, String triggerUser) {
        // 获取并锁定场景
        SceneEntity scene = sceneMapper.findByIdForUpdate(sceneId)
                .or(() -> sceneMapper.findById(sceneId))
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + sceneId));
        // 获取关联的仓库
        TestRepositoryEntity repository = (scene.getSpaceId() == null
                ? repositoryMapper.findById(scene.getRepoId())
                : repositoryMapper.findByIdAndSpaceId(scene.getRepoId(), scene.getSpaceId()))
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + scene.getRepoId()));
        // 校验仓库是否启用
        if (!Boolean.TRUE.equals(repository.getEnabled())) {
            throw new IllegalArgumentException("所属仓库已停用，请先启用仓库");
        }
        // 校验是否已有活跃任务
        if (taskRepository.existsBySceneIdAndStatusIn(sceneId, ACTIVE_TASK_STATUSES)) {
            throw new IllegalStateException("当前场景已有执行中的任务，请稍后再试");
        }

        // 创建并初始化任务实体
        TaskEntity task = new TaskEntity();
        // 解析分支：优先使用场景配置的分支，否则使用仓库默认分支
        String resolvedBranch = scene.getBranch() != null && !scene.getBranch().isBlank()
                ? scene.getBranch()
                : repository.getDefaultBranch();
        // 构建运行命令
        String resolvedRunCommand = taskCommandBuilder.buildRunCommand(repository, scene);
        task.setSceneId(scene.getId());
        task.setSpaceId(scene.getSpaceId());
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

        // 更新场景的最后任务状态
        scene.setLastTaskStatus(task.getStatus());
        scene.setLastRunAt(task.getQueuedAt());
        sceneMapper.update(scene);
        // 清除场景详情缓存
        invalidateSceneDetail(scene.getSpaceId(), scene.getId());
        return task;
    }

    /**
     * 失效场景详情缓存
     */
    private void invalidateSceneDetail(Long spaceId, Long sceneId) {
        if (detailCacheService != null && sceneId != null) {
            String key = spaceId == null ? "scene" : "scene:%d".formatted(spaceId);
            detailCacheService.invalidate(key, sceneId);
        }
    }
}
