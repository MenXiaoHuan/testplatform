package com.example.platform.task.service;

import com.example.platform.report.service.ReportArchiveService;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.model.TestRepositoryJpaRepository;
import com.example.platform.runner.service.RunnerExecutionService;
import com.example.platform.runner.service.RunnerWorkspaceService;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.model.SceneJpaRepository;
import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.parser.ParsedArtifactBinding;
import com.example.platform.task.parser.ParsedTaskResults;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.ArtifactJpaRepository;
import com.example.platform.task.model.CaseResultEntity;
import com.example.platform.task.model.CaseResultJpaRepository;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.model.TaskJpaRepository;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TaskServiceImpl implements TaskService {
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
    private final String storageBucket;
    private final String minioEndpoint;

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
        this.storageBucket = storageBucket;
        this.minioEndpoint = minioEndpoint;
    }

    @Override
    public TaskEntity createAndRun(Long sceneId) {
        SceneEntity scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + sceneId));
        TestRepositoryEntity repository = repositoryRepository.findById(scene.getRepoId())
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + scene.getRepoId()));

        TaskEntity task = new TaskEntity();
        task.setSceneId(scene.getId());
        task.setRepoId(repository.getId());
        task.setStatus("RUNNING");
        task.setTriggerType("MANUAL");
        task.setBranch(scene.getBranch());
        task.setRunnerName("centralized-runner");
        task.setStartedAt(LocalDateTime.now());
        task = taskRepository.save(task);

        int installStatus = 1;
        int runStatus = 1;
        try {
            Path workspace = runnerWorkspaceService.prepareWorkspace(repository.getGitUrl(), scene.getBranch(), task.getId());
            installStatus = runnerExecutionService.installDependencies(workspace, repository.getInstallCommand());
            if (installStatus == 0) {
                runStatus = runnerExecutionService.runTests(workspace, scene.getRunCommand());
                if (runStatus == 0) {
                    task.setReportUrl(reportArchiveService.archiveReport(workspace, task.getId(), repository.getReportRelativePath()));
                    Path resultsIndex = workspace.resolve(repository.getResultsIndexRelativePath());
                    ParsedTaskResults parsedTaskResults = taskCaseResultParseService.parse(task.getId(), resultsIndex, workspace);
                    Map<String, Long> caseResultIds = taskCaseResultPersistenceService.persist(parsedTaskResults.caseResults());
                    Map<String, TaskArtifactArchiveService.ArtifactBindingTarget> bindingTargets =
                            parsedTaskResults.artifactBindings().stream()
                                    .collect(Collectors.toMap(
                                            ParsedArtifactBinding::relativePath,
                                            binding -> new TaskArtifactArchiveService.ArtifactBindingTarget(
                                                    caseResultIds.get(binding.caseHistoryId()),
                                                    binding.artifactType()),
                                            (left, right) -> left));
                    taskArtifactArchiveService.archiveArtifacts(
                            task.getId(),
                            workspace,
                            List.of(repository.getArtifactRootRelativePath(), repository.getReportRelativePath()),
                            bindingTargets);
                }
            }
        } catch (Exception exception) {
            runStatus = 1;
            task.setLogUrl(exception.getMessage());
        }

        task.setFinishedAt(LocalDateTime.now());
        task.setDurationMs(Duration.between(task.getStartedAt(), task.getFinishedAt()).toMillis());
        task.setStatus(installStatus == 0 && runStatus == 0 ? "SUCCESS" : "FAILED");
        return taskRepository.save(task);
    }

    @Override
    public List<TaskEntity> list() {
        return taskRepository.findAll().stream()
                .map(this::withAccessibleReportUrl)
                .collect(Collectors.toList());
    }

    @Override
    public TaskEntity get(Long taskId) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        return withAccessibleReportUrl(task);
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
        copy.setReportUrl(resolveReportUrl(task.getReportUrl()));
        return copy;
    }

    private ArtifactEntity withAccessibleArtifactUrl(ArtifactEntity artifact) {
        ArtifactEntity copy = copyArtifact(artifact);
        if (artifact.getBucket() != null && artifact.getObjectKey() != null) {
            copy.setUrl(objectStorageService.createPresignedGetUrl(artifact.getBucket(), artifact.getObjectKey()));
        }
        return copy;
    }

    private String resolveReportUrl(String reportUrl) {
        if (reportUrl == null || reportUrl.isBlank()) {
            return reportUrl;
        }
        String expectedPrefix = minioEndpoint.endsWith("/") ? minioEndpoint : minioEndpoint + "/";
        String bucketPrefix = expectedPrefix + storageBucket + "/";
        if (!reportUrl.startsWith(bucketPrefix)) {
            return reportUrl;
        }
        String objectKey = reportUrl.substring(bucketPrefix.length());
        return objectStorageService.createPresignedGetUrl(storageBucket, objectKey);
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
        return copy;
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
}
