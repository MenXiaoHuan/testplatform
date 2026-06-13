package com.example.platform.task.service;

import com.example.platform.common.PageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.platform.report.service.ReportArchiveService;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.model.TestRepositoryJpaRepository;
import com.example.platform.runner.service.RunnerExecutionService;
import com.example.platform.runner.service.RunnerWorkspaceService;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.model.SceneJpaRepository;
import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.dto.SceneTaskListResponse;
import com.example.platform.task.dto.CaseArtifactLinkResponse;
import com.example.platform.task.dto.CaseResultResponse;
import com.example.platform.task.dto.TaskDetailResponse;
import com.example.platform.task.dto.TaskArtifactSummaryResponse;
import com.example.platform.task.dto.TaskCaseSummaryResponse;
import com.example.platform.task.dto.TaskProjectStatResponse;
import com.example.platform.task.dto.TaskReportSummaryResponse;
import com.example.platform.task.parser.ParsedArtifactBinding;
import com.example.platform.task.parser.ParsedTaskResults;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.ArtifactJpaRepository;
import com.example.platform.task.model.CaseResultEntity;
import com.example.platform.task.model.CaseResultJpaRepository;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.model.TaskJpaRepository;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class TaskServiceImpl implements TaskService {
    private static final Logger log = LoggerFactory.getLogger(TaskServiceImpl.class);
    private static final String ALLURE_REPORT_NAME = "Allure 自动化测试报告";
    private static final String ALLURE_REPORT_LANGUAGE = "zh-CN";
    private static final Field TASK_CREATED_AT_FIELD = initTaskCreatedAtField();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SceneJpaRepository sceneRepository;
    private final TestRepositoryJpaRepository repositoryRepository;
    private final TaskJpaRepository taskRepository;
    private final ArtifactJpaRepository artifactRepository;
    private final CaseResultJpaRepository caseResultRepository;
    private final RunnerWorkspaceService runnerWorkspaceService;
    private final RunnerExecutionService runnerExecutionService;
    private final ReportArchiveService reportArchiveService;
    private final TaskArtifactArchiveService taskArtifactArchiveService;
    private final TaskCaseResultParseService taskCaseResultParseService;
    private final TaskCaseResultPersistenceService taskCaseResultPersistenceService;
    private final ObjectStorageService objectStorageService;
    private final TaskCommandBuilder taskCommandBuilder;
    private final Executor taskExecutionExecutor;
    private final String storageBucket;

    @Autowired
    public TaskServiceImpl(
            SceneJpaRepository sceneRepository,
            TestRepositoryJpaRepository repositoryRepository,
            TaskJpaRepository taskRepository,
            ArtifactJpaRepository artifactRepository,
            CaseResultJpaRepository caseResultRepository,
            RunnerWorkspaceService runnerWorkspaceService,
            RunnerExecutionService runnerExecutionService,
            ReportArchiveService reportArchiveService,
            TaskArtifactArchiveService taskArtifactArchiveService,
            TaskCaseResultParseService taskCaseResultParseService,
            TaskCaseResultPersistenceService taskCaseResultPersistenceService,
            ObjectStorageService objectStorageService,
            TaskCommandBuilder taskCommandBuilder,
            @Qualifier("taskExecutionExecutor") Executor taskExecutionExecutor,
            @Value("${platform.storage.bucket}") String storageBucket,
            @Value("${platform.storage.minio.endpoint}") String minioEndpoint) {
        this.sceneRepository = sceneRepository;
        this.repositoryRepository = repositoryRepository;
        this.taskRepository = taskRepository;
        this.artifactRepository = artifactRepository;
        this.caseResultRepository = caseResultRepository;
        this.runnerWorkspaceService = runnerWorkspaceService;
        this.runnerExecutionService = runnerExecutionService;
        this.reportArchiveService = reportArchiveService;
        this.taskArtifactArchiveService = taskArtifactArchiveService;
        this.taskCaseResultParseService = taskCaseResultParseService;
        this.taskCaseResultPersistenceService = taskCaseResultPersistenceService;
        this.objectStorageService = objectStorageService;
        this.taskCommandBuilder = taskCommandBuilder;
        this.taskExecutionExecutor = taskExecutionExecutor;
        this.storageBucket = storageBucket;
    }

    public TaskServiceImpl(
            SceneJpaRepository sceneRepository,
            TestRepositoryJpaRepository repositoryRepository,
            TaskJpaRepository taskRepository,
            ArtifactJpaRepository artifactRepository,
            CaseResultJpaRepository caseResultRepository,
            RunnerWorkspaceService runnerWorkspaceService,
            RunnerExecutionService runnerExecutionService,
            ReportArchiveService reportArchiveService,
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
                reportArchiveService,
                taskArtifactArchiveService,
                taskCaseResultParseService,
                taskCaseResultPersistenceService,
                objectStorageService,
                taskCommandBuilder,
                Runnable::run,
                storageBucket,
                minioEndpoint);
    }

    public TaskServiceImpl(
            SceneJpaRepository sceneRepository,
            TestRepositoryJpaRepository repositoryRepository,
            TaskJpaRepository taskRepository,
            ArtifactJpaRepository artifactRepository,
            CaseResultJpaRepository caseResultRepository,
            RunnerWorkspaceService runnerWorkspaceService,
            RunnerExecutionService runnerExecutionService,
            ReportArchiveService reportArchiveService,
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
                reportArchiveService,
                taskArtifactArchiveService,
                taskCaseResultParseService,
                taskCaseResultPersistenceService,
                objectStorageService,
                new TaskCommandBuilderImpl(),
                Runnable::run,
                storageBucket,
                minioEndpoint);
    }

    @Override
    public TaskEntity createAndStart(Long sceneId) {
        TaskEntity createdTask = createTask(sceneId);
        taskExecutionExecutor.execute(() -> runCreatedTask(createdTask.getId()));
        return copyTask(createdTask);
    }

    @Override
    public TaskEntity createAndRun(Long sceneId) {
        TaskEntity createdTask = createTask(sceneId);
        TestRepositoryEntity repository = repositoryRepository.findById(createdTask.getRepoId())
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + createdTask.getRepoId()));
        SceneEntity scene = sceneRepository.findById(createdTask.getSceneId())
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + createdTask.getSceneId()));
        return executeTask(createdTask, repository, scene);
    }

    private TaskEntity createTask(Long sceneId) {
        SceneEntity scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + sceneId));
        TestRepositoryEntity repository = repositoryRepository.findById(scene.getRepoId())
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + scene.getRepoId()));
        if (!Boolean.TRUE.equals(repository.getEnabled())) {
            throw new IllegalArgumentException("所属仓库已停用，请先启用仓库");
        }

        TaskEntity task = new TaskEntity();
        String resolvedBranch = scene.getBranch() != null && !scene.getBranch().isBlank()
                ? scene.getBranch()
                : repository.getDefaultBranch();
        String resolvedRunCommand = taskCommandBuilder.buildRunCommand(repository, scene);
        task.setSceneId(scene.getId());
        task.setRepoId(repository.getId());
        task.setStatus("RUNNING");
        task.setTriggerType("MANUAL");
        task.setBranch(resolvedBranch);
        task.setRunnerName("centralized-runner");
        task.setResolvedBranch(resolvedBranch);
        task.setResolvedBrowser(scene.getBrowser());
        task.setResolvedEnvJson(scene.getEnvJson());
        task.setResolvedMatchValue(scene.getMatchValue());
        task.setResolvedTestRoot(repository.getTestRoot());
        task.setResolvedRunCommand(resolvedRunCommand);
        task.setStartedAt(LocalDateTime.now());
        task = taskRepository.save(task);
        markSceneRunning(scene, task);
        return task;
    }

    private TaskEntity runCreatedTask(Long taskId) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        TestRepositoryEntity repository = repositoryRepository.findById(task.getRepoId())
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + task.getRepoId()));
        SceneEntity scene = sceneRepository.findById(task.getSceneId())
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + task.getSceneId()));
        return executeTask(task, repository, scene);
    }

    private TaskEntity executeTask(TaskEntity task, TestRepositoryEntity repository, SceneEntity scene) {
        int installStatus = 1;
        int runStatus = 1;
        try {
            Path workspace = runnerWorkspaceService.prepareWorkspace(
                    repository.getGitUrl(),
                    task.getResolvedBranch(),
                    task.getId());
            Path executionDirectory = resolveExecutionDirectory(workspace, repository);
            Map<String, String> platformEnv = platformEnv();
            ResolvedExecutionPaths executionPaths = resolveExecutionPaths(executionDirectory, repository);
            String reportCommand = buildReportCommand(executionPaths.reportRelativePath());
            installStatus = runnerExecutionService.installDependencies(
                    executionDirectory,
                    repository.getInstallCommand(),
                    platformEnv);
            if (installStatus == 0) {
                runStatus = runnerExecutionService.runTests(
                        executionDirectory,
                        task.getResolvedRunCommand(),
                        platformEnv);
                continuePostProcessing(
                        task,
                        repository,
                        executionDirectory,
                        executionPaths,
                        platformEnv,
                        reportCommand,
                        runStatus == 0);
            }
        } catch (Exception exception) {
            runStatus = 1;
            task.setLogUrl(exception.getMessage());
        }

        task.setFinishedAt(LocalDateTime.now());
        task.setDurationMs(Duration.between(task.getStartedAt(), task.getFinishedAt()).toMillis());
        task.setStatus(installStatus == 0 && runStatus == 0 ? "SUCCESS" : "FAILED");
        task = taskRepository.save(task);
        refreshSceneSummary(scene, task);
        return task;
    }

    private void markSceneRunning(SceneEntity scene, TaskEntity task) {
        scene.setLastTaskStatus(task.getStatus());
        scene.setLastRunAt(task.getStartedAt());
        sceneRepository.save(scene);
    }

    private int continuePostProcessing(
            TaskEntity task,
            TestRepositoryEntity repository,
            Path executionDirectory,
            ResolvedExecutionPaths executionPaths,
            Map<String, String> platformEnv,
            String reportCommand,
            boolean testsPassed) {
        int postProcessStatus = 0;
        List<String> errors = new ArrayList<>();
        Path allureResultsDirectory = executionDirectory.resolve(".allure-results");
        Path reportDirectory = executionPaths.reportDirectory();
        Path resultsIndex = executionPaths.resultsIndex();
        Path artifactDirectory = executionPaths.artifactDirectory();
        Integer generateReportStatus = null;

        if (testsPassed || Files.exists(allureResultsDirectory)) {
            generateReportStatus = runnerExecutionService.generateReport(
                    executionDirectory,
                    reportCommand,
                    platformEnv);
            if (generateReportStatus != 0) {
                postProcessStatus = generateReportStatus;
                errors.add("generate report failed with exit code " + generateReportStatus);
            }
        }

        if (Files.exists(reportDirectory) || Integer.valueOf(0).equals(generateReportStatus)) {
            try {
                task.setReportUrl(reportArchiveService.archiveReport(
                        executionDirectory,
                        task.getId(),
                        executionPaths.reportRelativePath()));
            } catch (Exception exception) {
                postProcessStatus = 1;
                errors.add("archive report failed: " + exception.getMessage());
            }
        }

        Map<String, TaskArtifactArchiveService.ArtifactBindingTarget> bindingTargets = Map.of();
        if (testsPassed || Files.exists(resultsIndex)) {
            try {
                ParsedTaskResults parsedTaskResults = taskCaseResultParseService.parse(
                        task.getId(),
                        resultsIndex,
                        executionDirectory);
                Map<String, Long> caseResultIds = taskCaseResultPersistenceService.persist(parsedTaskResults.caseResults());
                bindingTargets = parsedTaskResults.artifactBindings().stream()
                        .collect(Collectors.toMap(
                                ParsedArtifactBinding::relativePath,
                                binding -> new TaskArtifactArchiveService.ArtifactBindingTarget(
                                        caseResultIds.get(binding.caseHistoryId()),
                                        binding.artifactType()),
                                (left, right) -> left));
            } catch (Exception exception) {
                postProcessStatus = 1;
                errors.add("parse case results failed: " + exception.getMessage());
            }
        }

        if (testsPassed || Files.exists(artifactDirectory) || Files.exists(reportDirectory)) {
            try {
                taskArtifactArchiveService.archiveArtifacts(
                        task.getId(),
                        executionDirectory,
                        List.of(executionPaths.artifactRootRelativePath(), executionPaths.reportRelativePath()),
                        bindingTargets);
            } catch (Exception exception) {
                postProcessStatus = 1;
                errors.add("archive artifacts failed: " + exception.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            appendLogMessage(task, String.join("; ", errors));
        }
        return postProcessStatus;
    }

    private Path resolveExecutionDirectory(Path workspace, TestRepositoryEntity repository) {
        String workingDirectory = repository.getWorkingDirectory();
        if (workingDirectory == null || workingDirectory.isBlank()) {
            return workspace;
        }
        Path normalizedWorkspace = workspace.normalize();
        Path resolved = normalizedWorkspace.resolve(workingDirectory).normalize();
        if (!resolved.startsWith(normalizedWorkspace)) {
            throw new IllegalArgumentException("Working directory escapes repository workspace: " + workingDirectory);
        }
        return resolved;
    }

    private Map<String, String> platformEnv() {
        return Map.of("PLAYWRIGHT_PLATFORM_MODE", "true");
    }

    private ResolvedExecutionPaths resolveExecutionPaths(Path executionDirectory, TestRepositoryEntity repository) {
        Path reportDirectory = resolveExecutionSubPath(
                executionDirectory,
                repository.getReportRelativePath(),
                "Report relative path",
                true);
        Path resultsIndex = resolveExecutionSubPath(
                executionDirectory,
                repository.getResultsIndexRelativePath(),
                "Results relative path",
                false);
        Path artifactDirectory = resolveExecutionSubPath(
                executionDirectory,
                repository.getArtifactRootRelativePath(),
                "Artifact relative path",
                false);
        return new ResolvedExecutionPaths(
                reportDirectory,
                toUnixRelativePath(executionDirectory, reportDirectory),
                resultsIndex,
                toUnixRelativePath(executionDirectory, resultsIndex),
                artifactDirectory,
                toUnixRelativePath(executionDirectory, artifactDirectory));
    }

    private Path resolveExecutionSubPath(
            Path executionDirectory,
            String relativePath,
            String label,
            boolean requireNonBlank) {
        if (relativePath == null || relativePath.isBlank()) {
            if (requireNonBlank) {
                throw new IllegalArgumentException(label + " must not be blank");
            }
            return executionDirectory.normalize();
        }
        Path normalizedExecutionDirectory = executionDirectory.normalize();
        Path resolved = normalizedExecutionDirectory.resolve(relativePath).normalize();
        if (!resolved.startsWith(normalizedExecutionDirectory)) {
            throw new IllegalArgumentException(label + " escapes execution directory: " + relativePath);
        }
        return resolved;
    }

    private String toUnixRelativePath(Path executionDirectory, Path resolvedPath) {
        Path relativePath = executionDirectory.normalize().relativize(resolvedPath.normalize());
        String normalized = relativePath.toString().replace('\\', '/');
        return normalized.isBlank() ? "." : normalized;
    }

    private String buildReportCommand(String reportRelativePath) {
        String reportOutputPath = normalizeCliRelativePath(reportRelativePath);
        return "npx allure awesome ./.allure-results --output " + reportOutputPath
                + " --report-name \"" + ALLURE_REPORT_NAME + "\""
                + " --report-language " + ALLURE_REPORT_LANGUAGE
                + " --hide-labels package --hide-labels feature --hide-labels titlePath"
                + " --hide-labels parentSuite --hide-labels subSuite --hide-labels host"
                + " --hide-labels thread";
    }

    private String normalizeCliRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Report relative path must not be blank");
        }
        return relativePath.startsWith("./") ? relativePath : "./" + relativePath;
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
        var pageData = taskRepository.findAll(PageRequest.of(
                normalizedPage - 1,
                normalizedSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return PageResponse.from(pageData, normalizedPage, normalizedSize)
                .map(this::toSceneTaskListResponse);
    }

    @Override
    public PageResponse<SceneTaskListResponse> listByScene(Long sceneId, int page, int size) {
        if (!sceneRepository.existsById(sceneId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Scene not found: " + sceneId);
        }
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        var pageData = taskRepository.findAllBySceneId(
                sceneId,
                PageRequest.of(
                        normalizedPage - 1,
                        normalizedSize,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return PageResponse.from(pageData, normalizedPage, normalizedSize)
                .map(this::toSceneTaskListResponse);
    }

    @Override
    public TaskEntity get(Long taskId) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        return withAccessibleReportUrl(task);
    }

    @Override
    public TaskDetailResponse getDetail(Long taskId) {
        TaskEntity task = get(taskId);
        List<ArtifactEntity> artifacts = listArtifacts(taskId);
        return toTaskDetailResponse(task, artifacts.size());
    }

    @Override
    public TaskReportSummaryResponse getReportSummary(Long taskId) {
        TaskEntity task = withTaskSummary(get(taskId));
        List<ArtifactEntity> artifacts = listArtifacts(taskId);
        List<CaseResultResponse> caseResults = listCaseResultResponses(taskId);
        int passed = (int) caseResults.stream().filter(result -> "PASSED".equalsIgnoreCase(result.status())).count();
        int failed = (int) caseResults.stream().filter(result -> "FAILED".equalsIgnoreCase(result.status())).count();
        int skipped = (int) caseResults.stream().filter(result -> "SKIPPED".equalsIgnoreCase(result.status())).count();
        boolean reportReady = task.getReportUrl() != null && !task.getReportUrl().isBlank();
        return new TaskReportSummaryResponse(
                toTaskDetailResponse(task, artifacts.size()),
                resolveReportStatus(task, artifacts),
                task.getReportUrl(),
                artifacts,
                caseResults,
                new TaskCaseSummaryResponse(passed, failed, skipped, caseResults.size()),
                toArtifactSummary(artifacts),
                toProjectStats(caseResults),
                artifacts.size(),
                reportReady);
    }

    @Override
    public String getReportUrl(Long taskId) {
        return get(taskId).getReportUrl();
    }

    @Override
    public List<ArtifactEntity> listArtifacts(Long taskId) {
        return artifactRepository.findAllByTaskIdOrderByIdAsc(taskId).stream()
                .map(this::withAccessibleArtifactUrl)
                .collect(Collectors.toList());
    }

    @Override
    public List<CaseResultResponse> listCaseResultResponses(Long taskId) {
        return listCaseResults(taskId).stream()
                .map(this::toCaseResultResponse)
                .toList();
    }

    @Override
    public List<CaseResultEntity> listCaseResults(Long taskId) {
        return caseResultRepository.findAllByTaskIdOrderByIdAsc(taskId);
    }

    @Override
    public List<ArtifactEntity> listArtifactsByCaseResult(Long caseResultId) {
        return artifactRepository.findAllByCaseResultIdOrderByIdAsc(caseResultId).stream()
                .map(this::withAccessibleArtifactUrl)
                .collect(Collectors.toList());
    }

    private TaskEntity withAccessibleReportUrl(TaskEntity task) {
        TaskEntity copy = copyTask(task);
        copy.setReportUrl(safePresignedUrl(storageBucket, task.getReportUrl(), task.getReportUrl()));
        return copy;
    }

    private ArtifactEntity withAccessibleArtifactUrl(ArtifactEntity artifact) {
        ArtifactEntity copy = copyArtifact(artifact);
        if (artifact.getBucket() != null && artifact.getObjectKey() != null) {
            copy.setUrl(safePresignedUrl(artifact.getBucket(), artifact.getObjectKey(), artifact.getUrl()));
        }
        return copy;
    }

    private String safePresignedUrl(String bucket, String objectKeyOrUrl, String fallbackUrl) {
        if (objectKeyOrUrl == null || objectKeyOrUrl.isBlank()) {
            return fallbackUrl;
        }
        try {
            return objectStorageService.createPresignedGetUrl(bucket, objectKeyOrUrl);
        } catch (IllegalStateException exception) {
            log.warn(
                    "Failed to create presigned url, fallback to stored url. bucket={}, source={}, reason={}",
                    bucket,
                    objectKeyOrUrl,
                    exception.getMessage());
            return fallbackUrl != null && !fallbackUrl.isBlank() ? fallbackUrl : objectKeyOrUrl;
        }
    }

    private void appendLogMessage(TaskEntity task, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (task.getLogUrl() == null || task.getLogUrl().isBlank()) {
            task.setLogUrl(message);
            return;
        }
        task.setLogUrl(task.getLogUrl() + "; " + message);
    }

    private void refreshSceneSummary(SceneEntity scene, TaskEntity task) {
        TaskEntity summarySource = taskRepository.findFirstBySceneIdOrderByCreatedAtDescIdDesc(scene.getId())
                .orElse(task);
        scene.setLastRunAt(summarySource.getFinishedAt());
        scene.setLastTaskStatus(summarySource.getStatus());
        sceneRepository.save(scene);
    }

    private SceneTaskListResponse toSceneTaskListResponse(TaskEntity task) {
        TaskEntity summarizedTask = withTaskSummary(withAccessibleReportUrl(task));
        return SceneTaskListResponse.from(summarizedTask);
    }

    private TaskDetailResponse toTaskDetailResponse(TaskEntity task, int artifactCount) {
        String sceneName = sceneRepository.findById(task.getSceneId())
                .map(SceneEntity::getName)
                .orElse(null);
        String repositoryName = repositoryRepository.findById(task.getRepoId())
                .map(TestRepositoryEntity::getName)
                .orElse(null);
        return TaskDetailResponse.from(
                task,
                sceneName,
                repositoryName,
                countEnvironmentVariables(task.getResolvedEnvJson()),
                artifactCount);
    }

    private CaseResultResponse toCaseResultResponse(CaseResultEntity caseResult) {
        List<ArtifactEntity> caseArtifacts = listArtifactsByCaseResult(caseResult.getId());
        List<CaseArtifactLinkResponse> artifactLinks = toCaseArtifactLinks(caseArtifacts);
        return CaseResultResponse.from(
                caseResult,
                null,
                firstArtifactUrl(caseArtifacts, "VIDEO"),
                firstArtifactUrl(caseArtifacts, "TRACE"),
                artifactUrls(caseArtifacts, "SCREENSHOT"),
                firstArtifactUrl(caseArtifacts, "LOG"),
                artifactLinks.size(),
                artifactLinks);
    }

    private TaskEntity copyTask(TaskEntity source) {
        TaskEntity copy = new TaskEntity();
        copy.setId(source.getId());
        copy.setSceneId(source.getSceneId());
        copy.setRepoId(source.getRepoId());
        copy.setStatus(source.getStatus());
        copy.setTriggerType(source.getTriggerType());
        copy.setTriggerUser(source.getTriggerUser());
        copy.setBranch(source.getBranch());
        copy.setCommitSha(source.getCommitSha());
        copy.setStartedAt(source.getStartedAt());
        copy.setFinishedAt(source.getFinishedAt());
        copy.setDurationMs(source.getDurationMs());
        copy.setRunnerName(source.getRunnerName());
        copy.setReportUrl(source.getReportUrl());
        copy.setLogUrl(source.getLogUrl());
        copy.setResolvedBranch(source.getResolvedBranch());
        copy.setResolvedBrowser(source.getResolvedBrowser());
        copy.setResolvedEnvJson(source.getResolvedEnvJson());
        copy.setResolvedMatchValue(source.getResolvedMatchValue());
        copy.setResolvedTestRoot(source.getResolvedTestRoot());
        copy.setResolvedRunCommand(source.getResolvedRunCommand());
        copy.setReportReady(source.isReportReady());
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

    private TaskEntity withTaskSummary(TaskEntity task) {
        TaskEntity copy = copyTask(task);
        List<CaseResultEntity> caseResults = caseResultRepository.findAllByTaskIdOrderByIdAsc(task.getId());
        copy.setReportReady(copy.getReportUrl() != null && !copy.getReportUrl().isBlank());
        copy.setPassedCount((int) caseResults.stream()
                .filter(result -> "PASSED".equalsIgnoreCase(result.getStatus()))
                .count());
        copy.setFailedCount((int) caseResults.stream()
                .filter(result -> "FAILED".equalsIgnoreCase(result.getStatus()))
                .count());
        copy.setSkippedCount((int) caseResults.stream()
                .filter(result -> "SKIPPED".equalsIgnoreCase(result.getStatus()))
                .count());
        return copy;
    }

    private List<CaseArtifactLinkResponse> toCaseArtifactLinks(List<ArtifactEntity> artifacts) {
        return artifacts.stream()
                .map(artifact -> {
                    String normalizedType = normalizeArtifactType(artifact.getArtifactType());
                    if (normalizedType == null || artifact.getUrl() == null || artifact.getUrl().isBlank()) {
                        return null;
                    }
                    return new CaseArtifactLinkResponse(
                            normalizedType,
                            normalizedType.toLowerCase(Locale.ROOT),
                            artifact.getCaseResultId() == null ? "TASK" : "CASE",
                            artifact.getUrl());
                })
                .filter(link -> link != null)
                .collect(Collectors.toMap(
                        CaseArtifactLinkResponse::artifactType,
                        link -> link,
                        (left, right) -> left))
                .values()
                .stream()
                .toList();
    }

    private String normalizeArtifactType(String artifactType) {
        if (artifactType == null) {
            return null;
        }
        String normalized = artifactType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "TRACE", "VIDEO", "SCREENSHOT", "LOG" -> normalized;
            case "REPORT", "REPORT_FILE" -> "REPORT";
            default -> "OTHER";
        };
    }

    private String firstArtifactUrl(List<ArtifactEntity> artifacts, String artifactType) {
        return artifacts.stream()
                .filter(artifact -> artifactType.equals(normalizeArtifactType(artifact.getArtifactType())))
                .map(ArtifactEntity::getUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);
    }

    private List<String> artifactUrls(List<ArtifactEntity> artifacts, String artifactType) {
        return artifacts.stream()
                .filter(artifact -> artifactType.equals(normalizeArtifactType(artifact.getArtifactType())))
                .map(ArtifactEntity::getUrl)
                .filter(url -> url != null && !url.isBlank())
                .toList();
    }

    private String resolveReportStatus(TaskEntity task, List<ArtifactEntity> artifacts) {
        if (task.getReportUrl() != null && !task.getReportUrl().isBlank()) {
            return "READY";
        }
        if (!artifacts.isEmpty()) {
            return "NOT_READY";
        }
        return "MISSING";
    }

    private TaskArtifactSummaryResponse toArtifactSummary(List<ArtifactEntity> artifacts) {
        int videoCount = 0;
        int traceCount = 0;
        int screenshotCount = 0;
        int logCount = 0;
        int otherCount = 0;
        for (ArtifactEntity artifact : artifacts) {
            String normalizedType = normalizeArtifactType(artifact.getArtifactType());
            switch (normalizedType) {
                case "VIDEO" -> videoCount += 1;
                case "TRACE" -> traceCount += 1;
                case "SCREENSHOT" -> screenshotCount += 1;
                case "LOG" -> logCount += 1;
                default -> otherCount += 1;
            }
        }
        return new TaskArtifactSummaryResponse(videoCount, traceCount, screenshotCount, logCount, otherCount);
    }

    private List<TaskProjectStatResponse> toProjectStats(List<CaseResultResponse> caseResults) {
        return caseResults.stream()
                .collect(Collectors.groupingBy(
                        result -> result.projectName() == null || result.projectName().isBlank()
                                ? "unknown"
                                : result.projectName(),
                        Collectors.summingInt(result -> 1)))
                .entrySet()
                .stream()
                .map(entry -> new TaskProjectStatResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private int countEnvironmentVariables(String envJson) {
        if (envJson == null || envJson.isBlank()) {
            return 0;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(envJson);
            return root.isObject() ? root.size() : 0;
        } catch (Exception exception) {
            return 0;
        }
    }

    private ArtifactEntity copyArtifact(ArtifactEntity source) {
        ArtifactEntity copy = new ArtifactEntity();
        copy.setId(source.getId());
        copy.setTaskId(source.getTaskId());
        copy.setCaseResultId(source.getCaseResultId());
        copy.setArtifactType(source.getArtifactType());
        copy.setBucket(source.getBucket());
        copy.setObjectKey(source.getObjectKey());
        copy.setContentType(source.getContentType());
        copy.setSize(source.getSize());
        copy.setUrl(source.getUrl());
        return copy;
    }

    private record ResolvedExecutionPaths(
            Path reportDirectory,
            String reportRelativePath,
            Path resultsIndex,
            String resultsIndexRelativePath,
            Path artifactDirectory,
            String artifactRootRelativePath) {
    }
}
