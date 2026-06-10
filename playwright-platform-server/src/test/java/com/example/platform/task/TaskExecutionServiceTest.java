package com.example.platform.task;

import com.example.platform.report.service.ReportArchiveService;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.model.TestRepositoryJpaRepository;
import com.example.platform.runner.service.RunnerExecutionService;
import com.example.platform.runner.service.RunnerWorkspaceService;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.model.SceneJpaRepository;
import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.parser.ParsedArtifactBinding;
import com.example.platform.task.parser.ParsedCaseResult;
import com.example.platform.task.parser.ParsedTaskResults;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.ArtifactJpaRepository;
import com.example.platform.task.model.CaseResultJpaRepository;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.model.TaskJpaRepository;
import com.example.platform.task.service.TaskArtifactArchiveService;
import com.example.platform.task.service.TaskCaseResultParseService;
import com.example.platform.task.service.TaskCaseResultPersistenceService;
import com.example.platform.task.service.TaskServiceImpl;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TaskExecutionServiceTest {
    @Test
    void shouldCreateTaskAndArchiveReport() {
        SceneJpaRepository sceneRepository = Mockito.mock(SceneJpaRepository.class);
        TestRepositoryJpaRepository repositoryRepository = Mockito.mock(TestRepositoryJpaRepository.class);
        TaskJpaRepository taskRepository = Mockito.mock(TaskJpaRepository.class);
        ArtifactJpaRepository artifactRepository = Mockito.mock(ArtifactJpaRepository.class);
        CaseResultJpaRepository caseResultRepository = Mockito.mock(CaseResultJpaRepository.class);
        RunnerWorkspaceService workspaceService = Mockito.mock(RunnerWorkspaceService.class);
        RunnerExecutionService executionService = Mockito.mock(RunnerExecutionService.class);
        ReportArchiveService archiveService = Mockito.mock(ReportArchiveService.class);
        TaskArtifactArchiveService taskArtifactArchiveService = Mockito.mock(TaskArtifactArchiveService.class);
        TaskCaseResultParseService taskCaseResultParseService = Mockito.mock(TaskCaseResultParseService.class);
        TaskCaseResultPersistenceService taskCaseResultPersistenceService = Mockito.mock(TaskCaseResultPersistenceService.class);
        ObjectStorageService objectStorageService = Mockito.mock(ObjectStorageService.class);

        SceneEntity scene = new SceneEntity();
        scene.setId(11L);
        scene.setRepoId(21L);
        scene.setBranch("main");
        scene.setRunCommand("npm run test:e2e");

        TestRepositoryEntity repository = new TestRepositoryEntity();
        repository.setId(21L);
        repository.setGitUrl("git@demo/repo.git");
        repository.setInstallCommand("npm install");
        repository.setReportRelativePath("reports/allure-report");
        repository.setResultsIndexRelativePath("test-results/.playwright-results.json");
        repository.setArtifactRootRelativePath(".playwright-artifacts");

        Mockito.when(sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
        Mockito.when(repositoryRepository.findById(21L)).thenReturn(Optional.of(repository));
        Mockito.when(taskRepository.save(Mockito.any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity task = invocation.getArgument(0);
            if (task.getId() == null) {
                task.setId(101L);
            }
            return task;
        });
        Mockito.when(workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L)).thenReturn(Path.of("/tmp/task-101"));
        Mockito.when(executionService.installDependencies(Path.of("/tmp/task-101"), "npm install")).thenReturn(0);
        Mockito.when(executionService.runTests(Path.of("/tmp/task-101"), "npm run test:e2e")).thenReturn(0);
        Mockito.when(archiveService.archiveReport(Path.of("/tmp/task-101"), 101L, "reports/allure-report"))
                .thenReturn("http://minio/qa-report/runs/101/report/index.html");
        Mockito.when(taskCaseResultParseService.parse(101L, Path.of("/tmp/task-101/test-results/.playwright-results.json"), Path.of("/tmp/task-101")))
                .thenReturn(new ParsedTaskResults(List.of(), List.of()));
        Mockito.when(taskCaseResultPersistenceService.persist(List.of())).thenReturn(Map.of());

        TaskServiceImpl service = new TaskServiceImpl(
                sceneRepository,
                repositoryRepository,
                taskRepository,
                artifactRepository,
                caseResultRepository,
                workspaceService,
                executionService,
                archiveService,
                taskArtifactArchiveService,
                taskCaseResultParseService,
                taskCaseResultPersistenceService,
                objectStorageService,
                "qa-report",
                "http://minio");

        TaskEntity result = service.createAndRun(11L);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getReportUrl()).isEqualTo("http://minio/qa-report/runs/101/report/index.html");
        assertThat(result.getRunnerName()).isEqualTo("centralized-runner");

        ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
        Mockito.verify(taskRepository, Mockito.atLeastOnce()).save(captor.capture());
        List<TaskEntity> saved = captor.getAllValues();
        assertThat(saved.get(saved.size() - 1).getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldReturnPresignedUrlsForReportAndArtifacts() {
        SceneJpaRepository sceneRepository = Mockito.mock(SceneJpaRepository.class);
        TestRepositoryJpaRepository repositoryRepository = Mockito.mock(TestRepositoryJpaRepository.class);
        TaskJpaRepository taskRepository = Mockito.mock(TaskJpaRepository.class);
        ArtifactJpaRepository artifactRepository = Mockito.mock(ArtifactJpaRepository.class);
        CaseResultJpaRepository caseResultRepository = Mockito.mock(CaseResultJpaRepository.class);
        RunnerWorkspaceService workspaceService = Mockito.mock(RunnerWorkspaceService.class);
        RunnerExecutionService executionService = Mockito.mock(RunnerExecutionService.class);
        ReportArchiveService archiveService = Mockito.mock(ReportArchiveService.class);
        TaskArtifactArchiveService taskArtifactArchiveService = Mockito.mock(TaskArtifactArchiveService.class);
        TaskCaseResultParseService taskCaseResultParseService = Mockito.mock(TaskCaseResultParseService.class);
        TaskCaseResultPersistenceService taskCaseResultPersistenceService = Mockito.mock(TaskCaseResultPersistenceService.class);
        ObjectStorageService objectStorageService = Mockito.mock(ObjectStorageService.class);

        TaskEntity task = new TaskEntity();
        task.setId(101L);
        task.setReportUrl("http://minio/qa-report/runs/101/report/index.html");

        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setId(1L);
        artifact.setTaskId(101L);
        artifact.setBucket("qa-report");
        artifact.setObjectKey("runs/101/artifacts/trace.zip");
        artifact.setUrl("http://minio/qa-report/runs/101/artifacts/trace.zip");

        Mockito.when(taskRepository.findById(101L)).thenReturn(Optional.of(task));
        Mockito.when(taskRepository.findAll()).thenReturn(List.of(task));
        Mockito.when(artifactRepository.findAllByTaskIdOrderByIdAsc(101L)).thenReturn(List.of(artifact));
        Mockito.when(objectStorageService.createPresignedGetUrl("qa-report", "runs/101/report/index.html"))
                .thenReturn("http://minio/presigned/report");
        Mockito.when(objectStorageService.createPresignedGetUrl("qa-report", "runs/101/artifacts/trace.zip"))
                .thenReturn("http://minio/presigned/artifact");

        TaskServiceImpl service = new TaskServiceImpl(
                sceneRepository,
                repositoryRepository,
                taskRepository,
                artifactRepository,
                caseResultRepository,
                workspaceService,
                executionService,
                archiveService,
                taskArtifactArchiveService,
                taskCaseResultParseService,
                taskCaseResultPersistenceService,
                objectStorageService,
                "qa-report",
                "http://minio");

        TaskEntity taskDetail = service.get(101L);
        List<TaskEntity> tasks = service.list();
        String reportUrl = service.getReportUrl(101L);
        List<ArtifactEntity> artifacts = service.listArtifacts(101L);

        assertThat(taskDetail.getReportUrl()).isEqualTo("http://minio/presigned/report");
        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().getReportUrl()).isEqualTo("http://minio/presigned/report");
        assertThat(reportUrl).isEqualTo("http://minio/presigned/report");
        assertThat(artifacts).hasSize(1);
        assertThat(artifacts.getFirst().getUrl()).isEqualTo("http://minio/presigned/artifact");
        assertThat(artifacts.getFirst().getObjectKey()).isEqualTo("runs/101/artifacts/trace.zip");
    }

    @Test
    void shouldPersistCaseResultsAndBindArtifactsToCaseResults() {
        SceneJpaRepository sceneRepository = Mockito.mock(SceneJpaRepository.class);
        TestRepositoryJpaRepository repositoryRepository = Mockito.mock(TestRepositoryJpaRepository.class);
        TaskJpaRepository taskRepository = Mockito.mock(TaskJpaRepository.class);
        ArtifactJpaRepository artifactRepository = Mockito.mock(ArtifactJpaRepository.class);
        CaseResultJpaRepository caseResultRepository = Mockito.mock(CaseResultJpaRepository.class);
        RunnerWorkspaceService workspaceService = Mockito.mock(RunnerWorkspaceService.class);
        RunnerExecutionService executionService = Mockito.mock(RunnerExecutionService.class);
        ReportArchiveService archiveService = Mockito.mock(ReportArchiveService.class);
        TaskArtifactArchiveService taskArtifactArchiveService = Mockito.mock(TaskArtifactArchiveService.class);
        TaskCaseResultParseService taskCaseResultParseService = Mockito.mock(TaskCaseResultParseService.class);
        TaskCaseResultPersistenceService taskCaseResultPersistenceService = Mockito.mock(TaskCaseResultPersistenceService.class);
        ObjectStorageService objectStorageService = Mockito.mock(ObjectStorageService.class);

        SceneEntity scene = new SceneEntity();
        scene.setId(11L);
        scene.setRepoId(21L);
        scene.setBranch("main");
        scene.setRunCommand("npm run test:e2e");

        TestRepositoryEntity repository = new TestRepositoryEntity();
        repository.setId(21L);
        repository.setGitUrl("git@demo/repo.git");
        repository.setInstallCommand("npm install");
        repository.setReportRelativePath("playwright-report");
        repository.setResultsIndexRelativePath("test-results/.playwright-results.json");
        repository.setArtifactRootRelativePath(".playwright-artifacts");

        Mockito.when(sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
        Mockito.when(repositoryRepository.findById(21L)).thenReturn(Optional.of(repository));
        Mockito.when(taskRepository.save(Mockito.any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity task = invocation.getArgument(0);
            if (task.getId() == null) {
                task.setId(101L);
            }
            return task;
        });
        Mockito.when(workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L)).thenReturn(Path.of("/tmp/task-101"));
        Mockito.when(executionService.installDependencies(Path.of("/tmp/task-101"), "npm install")).thenReturn(0);
        Mockito.when(executionService.runTests(Path.of("/tmp/task-101"), "npm run test:e2e")).thenReturn(0);
        Mockito.when(archiveService.archiveReport(Path.of("/tmp/task-101"), 101L, "playwright-report"))
                .thenReturn("http://minio/qa-report/runs/101/report/index.html");
        Mockito.when(taskCaseResultParseService.parse(101L, Path.of("/tmp/task-101/test-results/.playwright-results.json"), Path.of("/tmp/task-101")))
                .thenReturn(new ParsedTaskResults(
                        List.of(new ParsedCaseResult(
                                101L,
                                "chromium::checkout::should pay successfully",
                                "checkout :: should pay successfully",
                                "checkout",
                                "should pay successfully",
                                "PASSED",
                                321L,
                                "chromium")),
                        List.of(new ParsedArtifactBinding(
                                ".playwright-artifacts/checkout/trace.zip",
                                "TRACE",
                                "chromium::checkout::should pay successfully"))));
        Mockito.when(taskCaseResultPersistenceService.persist(Mockito.anyList()))
                .thenReturn(Map.of("chromium::checkout::should pay successfully", 1001L));

        TaskServiceImpl service = new TaskServiceImpl(
                sceneRepository,
                repositoryRepository,
                taskRepository,
                artifactRepository,
                caseResultRepository,
                workspaceService,
                executionService,
                archiveService,
                taskArtifactArchiveService,
                taskCaseResultParseService,
                taskCaseResultPersistenceService,
                objectStorageService,
                "qa-report",
                "http://minio");

        TaskEntity result = service.createAndRun(11L);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        Mockito.verify(taskArtifactArchiveService)
                .archiveArtifacts(Mockito.eq(101L), Mockito.eq(Path.of("/tmp/task-101")), Mockito.anyList(), Mockito.anyMap());
        Mockito.verify(taskCaseResultPersistenceService).persist(Mockito.anyList());
    }

    @Test
    void shouldMarkTaskFailedWhenCaseResultParsingFails() {
        SceneJpaRepository sceneRepository = Mockito.mock(SceneJpaRepository.class);
        TestRepositoryJpaRepository repositoryRepository = Mockito.mock(TestRepositoryJpaRepository.class);
        TaskJpaRepository taskRepository = Mockito.mock(TaskJpaRepository.class);
        ArtifactJpaRepository artifactRepository = Mockito.mock(ArtifactJpaRepository.class);
        CaseResultJpaRepository caseResultRepository = Mockito.mock(CaseResultJpaRepository.class);
        RunnerWorkspaceService workspaceService = Mockito.mock(RunnerWorkspaceService.class);
        RunnerExecutionService executionService = Mockito.mock(RunnerExecutionService.class);
        ReportArchiveService archiveService = Mockito.mock(ReportArchiveService.class);
        TaskArtifactArchiveService taskArtifactArchiveService = Mockito.mock(TaskArtifactArchiveService.class);
        TaskCaseResultParseService taskCaseResultParseService = Mockito.mock(TaskCaseResultParseService.class);
        TaskCaseResultPersistenceService taskCaseResultPersistenceService = Mockito.mock(TaskCaseResultPersistenceService.class);
        ObjectStorageService objectStorageService = Mockito.mock(ObjectStorageService.class);

        SceneEntity scene = new SceneEntity();
        scene.setId(11L);
        scene.setRepoId(21L);
        scene.setBranch("main");
        scene.setRunCommand("npm run test:e2e");

        TestRepositoryEntity repository = new TestRepositoryEntity();
        repository.setId(21L);
        repository.setGitUrl("git@demo/repo.git");
        repository.setInstallCommand("npm install");
        repository.setReportRelativePath("playwright-report");
        repository.setResultsIndexRelativePath("test-results/.playwright-results.json");
        repository.setArtifactRootRelativePath(".playwright-artifacts");

        Mockito.when(sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
        Mockito.when(repositoryRepository.findById(21L)).thenReturn(Optional.of(repository));
        Mockito.when(taskRepository.save(Mockito.any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity task = invocation.getArgument(0);
            if (task.getId() == null) {
                task.setId(101L);
            }
            return task;
        });
        Mockito.when(workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L)).thenReturn(Path.of("/tmp/task-101"));
        Mockito.when(executionService.installDependencies(Path.of("/tmp/task-101"), "npm install")).thenReturn(0);
        Mockito.when(executionService.runTests(Path.of("/tmp/task-101"), "npm run test:e2e")).thenReturn(0);
        Mockito.when(archiveService.archiveReport(Path.of("/tmp/task-101"), 101L, "playwright-report"))
                .thenReturn("http://minio/qa-report/runs/101/report/index.html");
        Mockito.when(taskCaseResultParseService.parse(101L, Path.of("/tmp/task-101/test-results/.playwright-results.json"), Path.of("/tmp/task-101")))
                .thenThrow(new IllegalStateException("results index missing"));

        TaskServiceImpl service = new TaskServiceImpl(
                sceneRepository,
                repositoryRepository,
                taskRepository,
                artifactRepository,
                caseResultRepository,
                workspaceService,
                executionService,
                archiveService,
                taskArtifactArchiveService,
                taskCaseResultParseService,
                taskCaseResultPersistenceService,
                objectStorageService,
                "qa-report",
                "http://minio");

        TaskEntity result = service.createAndRun(11L);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getLogUrl()).contains("results index missing");
    }
}
