package com.example.platform.task.service;

import com.example.platform.cache.DetailCacheService;
import com.example.platform.common.PageResponse;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.runner.service.RunnerExecutionService;
import com.example.platform.runner.service.RunnerWorkspaceService;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.dto.SceneTaskListResponse;
import com.example.platform.task.dto.CaseResultResponse;
import com.example.platform.task.dto.TaskDetailResponse;
import com.example.platform.task.dto.TaskStageLogResponse;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.mapper.ArtifactMapper;
import com.example.platform.task.model.CaseResultEntity;
import com.example.platform.task.mapper.CaseResultMapper;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.mapper.TaskMapper;
import com.example.platform.task.model.TaskStageLogEntity;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class TaskServiceImpl implements TaskService {
    private static final Logger log = LoggerFactory.getLogger(TaskServiceImpl.class);
    private static final Field TASK_CREATED_AT_FIELD = initTaskCreatedAtField();

    private final SceneMapper sceneRepository;
    private final TestRepositoryMapper repositoryRepository;
    private final TaskMapper taskRepository;
    private final ArtifactMapper artifactRepository;
    private final CaseResultMapper caseResultRepository;
    private final Executor taskExecutionExecutor;
    private final TaskStageLogService taskStageLogService;
    private final TaskExecutionOrchestrator taskExecutionOrchestrator;
    private final TaskQueryViewService taskQueryViewService;
    private final TaskCreationService taskCreationService;
    private final DetailCacheService detailCacheService;

    @Autowired
    public TaskServiceImpl(
            SceneMapper sceneRepository,
            TestRepositoryMapper repositoryRepository,
            TaskMapper taskRepository,
            ArtifactMapper artifactRepository,
            CaseResultMapper caseResultRepository,
            RunnerWorkspaceService runnerWorkspaceService,
            RunnerExecutionService runnerExecutionService,
            TaskArtifactArchiveService taskArtifactArchiveService,
            TaskCaseResultParseService taskCaseResultParseService,
            TaskCaseResultPersistenceService taskCaseResultPersistenceService,
            ObjectStorageService objectStorageService,
            TaskCommandBuilder taskCommandBuilder,
            TaskCreationService taskCreationService,
            DetailCacheService detailCacheService,
            @Qualifier("taskExecutionExecutor") Executor taskExecutionExecutor,
            TaskStageLogService taskStageLogService,
            TaskExecutionProperties taskExecutionProperties,
            @Value("${platform.storage.bucket}") String storageBucket,
            @Value("${platform.storage.minio.endpoint}") String minioEndpoint) {
        this.sceneRepository = sceneRepository;
        this.repositoryRepository = repositoryRepository;
        this.taskRepository = taskRepository;
        this.artifactRepository = artifactRepository;
        this.caseResultRepository = caseResultRepository;
        this.taskExecutionExecutor = taskExecutionExecutor;
        this.taskStageLogService = taskStageLogService;
        this.taskCreationService = taskCreationService;
        this.detailCacheService = detailCacheService;
        this.taskExecutionOrchestrator = new TaskExecutionOrchestrator(
                taskRepository,
                sceneRepository,
                runnerWorkspaceService,
                runnerExecutionService,
                taskArtifactArchiveService,
                taskCaseResultParseService,
                taskCaseResultPersistenceService,
                taskStageLogService,
                taskExecutionProperties,
                detailCacheService);
        this.taskQueryViewService = new TaskQueryViewService(
                sceneRepository,
                repositoryRepository,
                caseResultRepository,
                objectStorageService,
                storageBucket);
    }

    public TaskServiceImpl(
            SceneMapper sceneRepository,
            TestRepositoryMapper repositoryRepository,
            TaskMapper taskRepository,
            ArtifactMapper artifactRepository,
            CaseResultMapper caseResultRepository,
            RunnerWorkspaceService runnerWorkspaceService,
            RunnerExecutionService runnerExecutionService,
            TaskArtifactArchiveService taskArtifactArchiveService,
            TaskCaseResultParseService taskCaseResultParseService,
            TaskCaseResultPersistenceService taskCaseResultPersistenceService,
            ObjectStorageService objectStorageService,
            TaskCommandBuilder taskCommandBuilder,
            TaskStageLogService taskStageLogService,
            DetailCacheService detailCacheService,
            String storageBucket,
            String minioEndpoint) {
        this(
                sceneRepository,
                repositoryRepository,
                taskRepository,
                artifactRepository,
                caseResultRepository,
                runnerWorkspaceService,
                runnerExecutionService,
                taskArtifactArchiveService,
                taskCaseResultParseService,
                taskCaseResultPersistenceService,
                objectStorageService,
                taskCommandBuilder,
                new TaskCreationService(sceneRepository, repositoryRepository, taskRepository, taskCommandBuilder, detailCacheService),
                detailCacheService,
                Runnable::run,
                taskStageLogService,
                new TaskExecutionProperties(),
                storageBucket,
                minioEndpoint);
    }

    public TaskServiceImpl(
            SceneMapper sceneRepository,
            TestRepositoryMapper repositoryRepository,
            TaskMapper taskRepository,
            ArtifactMapper artifactRepository,
            CaseResultMapper caseResultRepository,
            RunnerWorkspaceService runnerWorkspaceService,
            RunnerExecutionService runnerExecutionService,
            TaskArtifactArchiveService taskArtifactArchiveService,
            TaskCaseResultParseService taskCaseResultParseService,
            TaskCaseResultPersistenceService taskCaseResultPersistenceService,
            ObjectStorageService objectStorageService,
            TaskCommandBuilder taskCommandBuilder,
            TaskStageLogService taskStageLogService,
            String storageBucket,
            String minioEndpoint) {
        this(
                sceneRepository,
                repositoryRepository,
                taskRepository,
                artifactRepository,
                caseResultRepository,
                runnerWorkspaceService,
                runnerExecutionService,
                taskArtifactArchiveService,
                taskCaseResultParseService,
                taskCaseResultPersistenceService,
                objectStorageService,
                taskCommandBuilder,
                new TaskCreationService(sceneRepository, repositoryRepository, taskRepository, taskCommandBuilder),
                null,
                Runnable::run,
                taskStageLogService,
                new TaskExecutionProperties(),
                storageBucket,
                minioEndpoint);
    }

    public TaskServiceImpl(
            SceneMapper sceneRepository,
            TestRepositoryMapper repositoryRepository,
            TaskMapper taskRepository,
            ArtifactMapper artifactRepository,
            CaseResultMapper caseResultRepository,
            RunnerWorkspaceService runnerWorkspaceService,
            RunnerExecutionService runnerExecutionService,
            TaskArtifactArchiveService taskArtifactArchiveService,
            TaskCaseResultParseService taskCaseResultParseService,
            TaskCaseResultPersistenceService taskCaseResultPersistenceService,
            ObjectStorageService objectStorageService,
            TaskCommandBuilder taskCommandBuilder,
            Executor taskExecutionExecutor,
            String storageBucket,
            String minioEndpoint) {
        this(
                sceneRepository,
                repositoryRepository,
                taskRepository,
                artifactRepository,
                caseResultRepository,
                runnerWorkspaceService,
                runnerExecutionService,
                taskArtifactArchiveService,
                taskCaseResultParseService,
                taskCaseResultPersistenceService,
                objectStorageService,
                taskCommandBuilder,
                new TaskCreationService(sceneRepository, repositoryRepository, taskRepository, taskCommandBuilder),
                null,
                taskExecutionExecutor,
                new NoopTaskStageLogService(),
                new TaskExecutionProperties(),
                storageBucket,
                minioEndpoint);
    }

    public TaskServiceImpl(
            SceneMapper sceneRepository,
            TestRepositoryMapper repositoryRepository,
            TaskMapper taskRepository,
            ArtifactMapper artifactRepository,
            CaseResultMapper caseResultRepository,
            RunnerWorkspaceService runnerWorkspaceService,
            RunnerExecutionService runnerExecutionService,
            TaskArtifactArchiveService taskArtifactArchiveService,
            TaskCaseResultParseService taskCaseResultParseService,
            TaskCaseResultPersistenceService taskCaseResultPersistenceService,
            ObjectStorageService objectStorageService,
            TaskCommandBuilder taskCommandBuilder,
            Executor taskExecutionExecutor,
            DetailCacheService detailCacheService,
            String storageBucket,
            String minioEndpoint) {
        this(
                sceneRepository,
                repositoryRepository,
                taskRepository,
                artifactRepository,
                caseResultRepository,
                runnerWorkspaceService,
                runnerExecutionService,
                taskArtifactArchiveService,
                taskCaseResultParseService,
                taskCaseResultPersistenceService,
                objectStorageService,
                taskCommandBuilder,
                new TaskCreationService(sceneRepository, repositoryRepository, taskRepository, taskCommandBuilder, detailCacheService),
                detailCacheService,
                taskExecutionExecutor,
                new NoopTaskStageLogService(),
                new TaskExecutionProperties(),
                storageBucket,
                minioEndpoint);
    }

    public TaskServiceImpl(
            SceneMapper sceneRepository,
            TestRepositoryMapper repositoryRepository,
            TaskMapper taskRepository,
            ArtifactMapper artifactRepository,
            CaseResultMapper caseResultRepository,
            RunnerWorkspaceService runnerWorkspaceService,
            RunnerExecutionService runnerExecutionService,
            TaskArtifactArchiveService taskArtifactArchiveService,
            TaskCaseResultParseService taskCaseResultParseService,
            TaskCaseResultPersistenceService taskCaseResultPersistenceService,
            ObjectStorageService objectStorageService,
            TaskStageLogService taskStageLogService,
            String storageBucket,
            String minioEndpoint) {
        this(
                sceneRepository,
                repositoryRepository,
                taskRepository,
                artifactRepository,
                caseResultRepository,
                runnerWorkspaceService,
                runnerExecutionService,
                taskArtifactArchiveService,
                taskCaseResultParseService,
                taskCaseResultPersistenceService,
                objectStorageService,
                new TaskCommandBuilderImpl(),
                new TaskCreationService(sceneRepository, repositoryRepository, taskRepository, new TaskCommandBuilderImpl()),
                null,
                Runnable::run,
                taskStageLogService,
                new TaskExecutionProperties(),
                storageBucket,
                minioEndpoint);
    }

    public TaskServiceImpl(
            SceneMapper sceneRepository,
            TestRepositoryMapper repositoryRepository,
            TaskMapper taskRepository,
            ArtifactMapper artifactRepository,
            CaseResultMapper caseResultRepository,
            RunnerWorkspaceService runnerWorkspaceService,
            RunnerExecutionService runnerExecutionService,
            TaskArtifactArchiveService taskArtifactArchiveService,
            TaskCaseResultParseService taskCaseResultParseService,
            TaskCaseResultPersistenceService taskCaseResultPersistenceService,
            ObjectStorageService objectStorageService,
            TaskCommandBuilder taskCommandBuilder,
            DetailCacheService detailCacheService,
            String storageBucket,
            String minioEndpoint) {
        this(
                sceneRepository,
                repositoryRepository,
                taskRepository,
                artifactRepository,
                caseResultRepository,
                runnerWorkspaceService,
                runnerExecutionService,
                taskArtifactArchiveService,
                taskCaseResultParseService,
                taskCaseResultPersistenceService,
                objectStorageService,
                taskCommandBuilder,
                new TaskCreationService(sceneRepository, repositoryRepository, taskRepository, taskCommandBuilder, detailCacheService),
                detailCacheService,
                Runnable::run,
                new NoopTaskStageLogService(),
                new TaskExecutionProperties(),
                storageBucket,
                minioEndpoint);
    }

    public TaskServiceImpl(
            SceneMapper sceneRepository,
            TestRepositoryMapper repositoryRepository,
            TaskMapper taskRepository,
            ArtifactMapper artifactRepository,
            CaseResultMapper caseResultRepository,
            RunnerWorkspaceService runnerWorkspaceService,
            RunnerExecutionService runnerExecutionService,
            TaskArtifactArchiveService taskArtifactArchiveService,
            TaskCaseResultParseService taskCaseResultParseService,
            TaskCaseResultPersistenceService taskCaseResultPersistenceService,
            ObjectStorageService objectStorageService,
            TaskCommandBuilder taskCommandBuilder,
            String storageBucket,
            String minioEndpoint) {
        this(
                sceneRepository,
                repositoryRepository,
                taskRepository,
                artifactRepository,
                caseResultRepository,
                runnerWorkspaceService,
                runnerExecutionService,
                taskArtifactArchiveService,
                taskCaseResultParseService,
                taskCaseResultPersistenceService,
                objectStorageService,
                taskCommandBuilder,
                new TaskCreationService(sceneRepository, repositoryRepository, taskRepository, taskCommandBuilder),
                null,
                Runnable::run,
                new NoopTaskStageLogService(),
                new TaskExecutionProperties(),
                storageBucket,
                minioEndpoint);
    }

    public TaskServiceImpl(
            SceneMapper sceneRepository,
            TestRepositoryMapper repositoryRepository,
            TaskMapper taskRepository,
            ArtifactMapper artifactRepository,
            CaseResultMapper caseResultRepository,
            RunnerWorkspaceService runnerWorkspaceService,
            RunnerExecutionService runnerExecutionService,
            TaskArtifactArchiveService taskArtifactArchiveService,
            TaskCaseResultParseService taskCaseResultParseService,
            TaskCaseResultPersistenceService taskCaseResultPersistenceService,
            ObjectStorageService objectStorageService,
            String storageBucket,
            String minioEndpoint) {
        this(
                sceneRepository,
                repositoryRepository,
                taskRepository,
                artifactRepository,
                caseResultRepository,
                runnerWorkspaceService,
                runnerExecutionService,
                taskArtifactArchiveService,
                taskCaseResultParseService,
                taskCaseResultPersistenceService,
                objectStorageService,
                new TaskCommandBuilderImpl(),
                new TaskCreationService(sceneRepository, repositoryRepository, taskRepository, new TaskCommandBuilderImpl()),
                null,
                Runnable::run,
                new NoopTaskStageLogService(),
                new TaskExecutionProperties(),
                storageBucket,
                minioEndpoint);
    }

    @Override
    public TaskEntity createAndStart(Long sceneId) {
        TaskEntity createdTask = taskCreationService.createTask(sceneId, "MANUAL", "manual-run", null);
        dispatchExistingTask(createdTask.getId(), true);
        return copyTask(createdTask);
    }

    @Override
    public TaskEntity createAndRun(Long sceneId) {
        TaskEntity createdTask = taskCreationService.createTask(sceneId, "MANUAL", "manual-run", null);
        TestRepositoryEntity repository = repositoryRepository.findById(createdTask.getRepoId())
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + createdTask.getRepoId()));
        SceneEntity scene = sceneRepository.findById(createdTask.getSceneId())
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + createdTask.getSceneId()));
        return taskExecutionOrchestrator.executeTask(createdTask, repository, scene);
    }

    @Override
    public TaskEntity createScheduledTask(Long sceneId, String triggerReason) {
        TaskEntity createdTask = taskCreationService.createTask(sceneId, "SCHEDULED", triggerReason, "scheduler");
        dispatchExistingTask(createdTask.getId(), false);
        return copyTask(createdTask);
    }

    TaskEntity runCreatedTask(Long taskId) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        TestRepositoryEntity repository = repositoryRepository.findById(task.getRepoId())
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + task.getRepoId()));
        SceneEntity scene = sceneRepository.findById(task.getSceneId())
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + task.getSceneId()));
        return taskExecutionOrchestrator.executeTask(task, repository, scene);
    }

    public void dispatchExistingTask(Long taskId, boolean failFastOnReject) {
        try {
            taskExecutionExecutor.execute(() -> runCreatedTask(taskId));
        } catch (RejectedExecutionException exception) {
            markTaskAsRejected(taskId, "任务执行队列已满，请稍后再试");
            if (failFastOnReject) {
                throw new IllegalStateException("任务执行队列已满，请稍后再试", exception);
            }
            log.warn("Skip dispatch queued task because executor is full. taskId={}", taskId);
        }
    }

    private void markTaskAsRejected(Long taskId, String message) {
        TaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        task.setStatus("FAILED");
        task.setCurrentStage("FINISHED");
        task.setResultCode("SYSTEM_BUSY");
        task.setResultMessage(message);
        task.setFinishedAt(LocalDateTime.now());
        if (task.getQueuedAt() != null) {
            task.setDurationMs(java.time.Duration.between(task.getQueuedAt(), task.getFinishedAt()).toMillis());
        }
        taskRepository.update(task);
        invalidateTaskDetail(task.getId());
        SceneEntity scene = sceneRepository.findById(task.getSceneId()).orElse(null);
        if (scene == null) {
            return;
        }
        scene.setLastTaskStatus(task.getStatus());
        scene.setLastRunAt(task.getFinishedAt());
        sceneRepository.update(scene);
        invalidateSceneDetail(scene.getId());
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    @Override
    public PageResponse<SceneTaskListResponse> list(int page, int size) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = (normalizedPage - 1) * normalizedSize;
        List<TaskEntity> tasks = taskRepository.findPage(normalizedSize, offset);
        long total = taskRepository.countAll();
        return PageResponse.of(tasks, total, normalizedPage, normalizedSize)
                .map(taskQueryViewService::toSceneTaskListResponse);
    }

    @Override
    public PageResponse<SceneTaskListResponse> listByScene(Long sceneId, int page, int size) {
        if (!sceneRepository.findById(sceneId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Scene not found: " + sceneId);
        }
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = (normalizedPage - 1) * normalizedSize;
        List<TaskEntity> tasks = taskRepository.findBySceneIdPage(sceneId, normalizedSize, offset);
        long total = taskRepository.countBySceneId(sceneId);
        return PageResponse.of(tasks, total, normalizedPage, normalizedSize)
                .map(taskQueryViewService::toSceneTaskListResponse);
    }

    @Override
    public TaskEntity get(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }

    @Override
    public TaskDetailResponse getDetail(Long taskId) {
        if (detailCacheService == null) {
            return loadTaskDetail(taskId);
        }
        return detailCacheService.getOrLoad("task", taskId, TaskDetailResponse.class, () -> java.util.Optional.of(loadTaskDetail(taskId)))
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }

    private TaskDetailResponse loadTaskDetail(Long taskId) {
        TaskEntity task = get(taskId);
        List<ArtifactEntity> artifacts = listArtifacts(taskId);
        return taskQueryViewService.toTaskDetailResponse(task, artifacts.size());
    }

    @Override
    public List<ArtifactEntity> listArtifacts(Long taskId) {
        return artifactRepository.findAllByTaskIdOrderByIdAsc(taskId).stream()
                .map(taskQueryViewService::withAccessibleArtifactUrl)
                .collect(Collectors.toList());
    }

    @Override
    public List<CaseResultResponse> listCaseResultResponses(Long taskId) {
        return listCaseResults(taskId).stream()
                .map(caseResult -> taskQueryViewService.toCaseResultResponse(
                        caseResult,
                        listArtifactsByCaseResult(caseResult.getId())))
                .toList();
    }

    @Override
    public List<CaseResultEntity> listCaseResults(Long taskId) {
        return caseResultRepository.findAllByTaskIdOrderByIdAsc(taskId);
    }

    @Override
    public List<ArtifactEntity> listArtifactsByCaseResult(Long caseResultId) {
        return artifactRepository.findAllByCaseResultIdOrderByIdAsc(caseResultId).stream()
                .map(taskQueryViewService::withAccessibleArtifactUrl)
                .collect(Collectors.toList());
    }

    @Override
    public void cancelTask(Long taskId, String operatorName) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        task.setCancelRequested(true);
        task.setCancelRequestedAt(LocalDateTime.now());
        task.setCancelRequestedBy(operatorName);
        taskRepository.update(task);
        invalidateTaskDetail(taskId);
    }

    @Override
    public List<TaskStageLogResponse> listStageLogs(Long taskId) {
        return taskStageLogService.listByTaskId(taskId).stream()
                .map(taskQueryViewService::toTaskStageLogResponse)
                .toList();
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

    private TaskEntity copyTask(TaskEntity source) {
        TaskEntity copy = new TaskEntity();
        copy.setId(source.getId());
        copy.setSceneId(source.getSceneId());
        copy.setRepoId(source.getRepoId());
        copy.setStatus(source.getStatus());
        copy.setTriggerType(source.getTriggerType());
        copy.setTriggerUser(source.getTriggerUser());
        copy.setTriggerReason(source.getTriggerReason());
        copy.setQueuedAt(source.getQueuedAt());
        copy.setBranch(source.getBranch());
        copy.setCommitSha(source.getCommitSha());
        copy.setStartedAt(source.getStartedAt());
        copy.setFinishedAt(source.getFinishedAt());
        copy.setDurationMs(source.getDurationMs());
        copy.setRunnerName(source.getRunnerName());
        copy.setCurrentStage(source.getCurrentStage());
        copy.setResultCode(source.getResultCode());
        copy.setResultMessage(source.getResultMessage());
        copy.setCancelRequested(source.getCancelRequested());
        copy.setCancelRequestedAt(source.getCancelRequestedAt());
        copy.setCancelRequestedBy(source.getCancelRequestedBy());
        copy.setLogUrl(source.getLogUrl());
        copy.setResolvedBranch(source.getResolvedBranch());
        copy.setResolvedBrowser(source.getResolvedBrowser());
        copy.setResolvedEnvJson(source.getResolvedEnvJson());
        copy.setResolvedMatchValue(source.getResolvedMatchValue());
        copy.setResolvedTestRoot(source.getResolvedTestRoot());
        copy.setResolvedRunCommand(source.getResolvedRunCommand());
        copy.setPassedCount(source.getPassedCount());
        copy.setFailedCount(source.getFailedCount());
        copy.setSkippedCount(source.getSkippedCount());
        copyReadOnlyCreatedAt(source, copy);
        return copy;
    }

    private static Field initTaskCreatedAtField() {
        try {
            Field field = TaskEntity.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to access TaskEntity.createdAt", exception);
        }
    }

    private void copyReadOnlyCreatedAt(TaskEntity source, TaskEntity target) {
        try {
            TASK_CREATED_AT_FIELD.set(target, source.getCreatedAt());
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to copy TaskEntity.createdAt", exception);
        }
    }

    private static final class NoopTaskStageLogService implements TaskStageLogService {
        @Override
        public TaskStageLogEntity archiveStageLog(Long taskId, String stage, Path logFile, int lineCount) {
            return null;
        }

        @Override
        public List<TaskStageLogEntity> listByTaskId(Long taskId) {
            return List.of();
        }
    }
}
