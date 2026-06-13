package com.example.platform.task;

import com.example.platform.common.PageResponse;
import com.example.platform.report.service.ReportArchiveService;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.model.TestRepositoryJpaRepository;
import com.example.platform.runner.service.RunnerExecutionService;
import com.example.platform.runner.service.RunnerExecutionServiceImpl;
import com.example.platform.runner.service.RunnerWorkspaceService;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.model.SceneJpaRepository;
import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.dto.SceneTaskListResponse;
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
import com.example.platform.task.service.TaskCommandBuilder;
import com.example.platform.task.service.TaskCommandBuilderImpl;
import com.example.platform.task.service.TaskServiceImpl;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class TaskExecutionServiceTest {
    @TempDir
    Path tempDir;

    private static final String ALLURE_AWESOME_COMMAND =
            "npx allure awesome ./.allure-results --output ./reports/allure-report "
                    + "--report-name \"Allure 自动化测试报告\" --report-language zh-CN "
                    + "--hide-labels package --hide-labels feature --hide-labels titlePath "
                    + "--hide-labels parentSuite --hide-labels subSuite --hide-labels host "
                    + "--hide-labels thread";

    @Test
    void shouldBuildRunCommandFromRepositoryTemplateAndMatchValue() {
        TestRepositoryEntity repository = new TestRepositoryEntity();
        repository.setRunCommandTemplate("node ./scripts/run-e2e.cjs");
        repository.setTestRoot("tests");

        SceneEntity scene = new SceneEntity();
        scene.setBrowser("chromium");
        scene.setEnvJson("{\"BASE_URL\":\"https://example.com\"}");
        scene.setMatchValue("login.spec.ts");

        TaskCommandBuilder taskCommandBuilder = new TaskCommandBuilderImpl();
        String command = taskCommandBuilder.buildRunCommand(repository, scene);

        assertThat(command).contains("node ./scripts/run-e2e.cjs");
        assertThat(command).contains("--project chromium");
        assertThat(command).contains("--target tests/login.spec.ts");
    }

    @Test
    void shouldAppendPlaywrightSpecPathAsPositionalArgumentForNpxCommand() {
        TestRepositoryEntity repository = new TestRepositoryEntity();
        repository.setRunCommandTemplate("npx playwright test");
        repository.setTestRoot("tests/e2e");

        SceneEntity scene = new SceneEntity();
        scene.setBrowser("webkit");
        scene.setMatchValue("checkout/cart.spec.ts");

        TaskCommandBuilder taskCommandBuilder = new TaskCommandBuilderImpl();
        String command = taskCommandBuilder.buildRunCommand(repository, scene);

        assertThat(command).isEqualTo("npx playwright test tests/e2e/checkout/cart.spec.ts --project webkit");
        assertThat(command).doesNotContain("--target");
    }

    @Test
    void shouldKeepFullTestRootExecutionWhenMatchValueIsBlank() {
        TestRepositoryEntity repository = new TestRepositoryEntity();
        repository.setRunCommandTemplate("node ./scripts/run-e2e.cjs");
        repository.setTestRoot("tests");

        SceneEntity scene = new SceneEntity();
        scene.setBrowser("chromium");
        scene.setMatchValue("");

        TaskCommandBuilder taskCommandBuilder = new TaskCommandBuilderImpl();
        String command = taskCommandBuilder.buildRunCommand(repository, scene);

        assertThat(command).doesNotContain("--target");
    }

    @Test
    void shouldPersistResolvedExecutionSnapshotBeforeRunningTests() {
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
        TaskCommandBuilder taskCommandBuilder = Mockito.mock(TaskCommandBuilder.class);

        SceneEntity scene = new SceneEntity();
        scene.setId(11L);
        scene.setRepoId(21L);
        scene.setBranch("main");
        scene.setBrowser("chromium");
        scene.setEnvJson("{\"BASE_URL\":\"https://example.com\"}");
        scene.setMatchValue("login.spec.ts");
        scene.setRunCommand("npm run legacy:e2e");

        TestRepositoryEntity repository = new TestRepositoryEntity();
        repository.setId(21L);
        repository.setGitUrl("git@demo/repo.git");
        repository.setDefaultBranch("main");
        repository.setWorkingDirectory("playwright_framework");
        repository.setInstallCommand("npm install");
        repository.setRunCommandTemplate("node ./scripts/run-e2e.cjs");
        repository.setTestRoot("tests");
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
        Mockito.when(taskCommandBuilder.buildRunCommand(repository, scene))
                .thenReturn("node ./scripts/run-e2e.cjs --project chromium --target tests/login.spec.ts");
        Mockito.when(workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L)).thenReturn(Path.of("/tmp/task-101"));
        Mockito.when(executionService.installDependencies(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(executionService.runTests(
                Path.of("/tmp/task-101/playwright_framework"),
                "node ./scripts/run-e2e.cjs --project chromium --target tests/login.spec.ts",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(executionService.generateReport(
                Path.of("/tmp/task-101/playwright_framework"),
                ALLURE_AWESOME_COMMAND,
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(archiveService.archiveReport(
                Path.of("/tmp/task-101/playwright_framework"),
                101L,
                "reports/allure-report"))
                .thenReturn("http://minio/qa-report/runs/101/report/index.html");
        Mockito.when(taskCaseResultParseService.parse(
                101L,
                Path.of("/tmp/task-101/playwright_framework/test-results/.playwright-results.json"),
                Path.of("/tmp/task-101/playwright_framework")))
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
                taskCommandBuilder,
                "qa-report",
                "http://minio");

        TaskEntity result = service.createAndRun(11L);

        assertThat(result.getResolvedBranch()).isEqualTo("main");
        assertThat(result.getResolvedBrowser()).isEqualTo("chromium");
        assertThat(result.getResolvedEnvJson()).contains("BASE_URL");
        assertThat(result.getResolvedMatchValue()).isEqualTo("login.spec.ts");
        assertThat(result.getResolvedTestRoot()).isEqualTo("tests");
        assertThat(result.getResolvedRunCommand()).contains("--target tests/login.spec.ts");
        Mockito.verify(taskCommandBuilder).buildRunCommand(repository, scene);
        Mockito.verify(executionService).installDependencies(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"));
        Mockito.verify(executionService).runTests(
                Path.of("/tmp/task-101/playwright_framework"),
                "node ./scripts/run-e2e.cjs --project chromium --target tests/login.spec.ts",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"));
        Mockito.verify(executionService).generateReport(
                Path.of("/tmp/task-101/playwright_framework"),
                ALLURE_AWESOME_COMMAND,
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"));
    }

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
        repository.setWorkingDirectory("playwright_framework");
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
        Mockito.when(executionService.installDependencies(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(executionService.runTests(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm run test:e2e",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(executionService.generateReport(
                Path.of("/tmp/task-101/playwright_framework"),
                ALLURE_AWESOME_COMMAND,
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(archiveService.archiveReport(
                Path.of("/tmp/task-101/playwright_framework"),
                101L,
                "reports/allure-report"))
                .thenReturn("http://minio/qa-report/runs/101/report/index.html");
        Mockito.when(taskCaseResultParseService.parse(
                101L,
                Path.of("/tmp/task-101/playwright_framework/test-results/.playwright-results.json"),
                Path.of("/tmp/task-101/playwright_framework")))
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
        assertThat(scene.getLastTaskStatus()).isEqualTo("SUCCESS");
        assertThat(scene.getLastRunAt()).isEqualTo(result.getFinishedAt());

        ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
        Mockito.verify(taskRepository, Mockito.atLeastOnce()).save(captor.capture());
        List<TaskEntity> saved = captor.getAllValues();
        assertThat(saved.get(saved.size() - 1).getStatus()).isEqualTo("SUCCESS");
        Mockito.verify(sceneRepository, Mockito.atLeastOnce()).save(scene);
    }

    @Test
    void shouldGenerateReportAfterRunningTests() {
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
        repository.setWorkingDirectory("playwright_framework");
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
        Mockito.when(executionService.installDependencies(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(executionService.runTests(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm run test:e2e",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(executionService.generateReport(
                Path.of("/tmp/task-101/playwright_framework"),
                ALLURE_AWESOME_COMMAND,
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(archiveService.archiveReport(
                Path.of("/tmp/task-101/playwright_framework"),
                101L,
                "reports/allure-report"))
                .thenReturn("http://minio/qa-report/runs/101/report/index.html");
        Mockito.when(taskCaseResultParseService.parse(
                101L,
                Path.of("/tmp/task-101/playwright_framework/test-results/.playwright-results.json"),
                Path.of("/tmp/task-101/playwright_framework")))
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
        org.mockito.InOrder inOrder = Mockito.inOrder(executionService);
        inOrder.verify(executionService).runTests(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm run test:e2e",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"));
        inOrder.verify(executionService).generateReport(
                Path.of("/tmp/task-101/playwright_framework"),
                ALLURE_AWESOME_COMMAND,
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"));
    }

    @Test
    void shouldKeepTaskSuccessfulWhenReportGenerationFails() {
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
        repository.setWorkingDirectory("playwright_framework");
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
        Mockito.when(executionService.installDependencies(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(executionService.runTests(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm run test:e2e",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(executionService.generateReport(
                Path.of("/tmp/task-101/playwright_framework"),
                ALLURE_AWESOME_COMMAND,
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(2);

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
        assertThat(result.getReportUrl()).isNull();
        assertThat(scene.getLastTaskStatus()).isEqualTo("SUCCESS");
        Mockito.verify(archiveService, Mockito.never()).archiveReport(Mockito.any(), Mockito.anyLong(), Mockito.anyString());
    }

    @Test
    void shouldContinuePostProcessingWhenTestsFailButExecutionOutputsExist() throws Exception {
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
        repository.setWorkingDirectory("playwright_framework");
        repository.setInstallCommand("npm install");
        repository.setReportRelativePath("reports/allure-report");
        repository.setResultsIndexRelativePath("test-results/.playwright-results.json");
        repository.setArtifactRootRelativePath(".playwright-artifacts");

        Path workspace = tempDir.resolve("task-101");
        Path executionDirectory = workspace.resolve("playwright_framework");
        Files.createDirectories(executionDirectory.resolve(".allure-results"));
        Files.createDirectories(executionDirectory.resolve("reports/allure-report"));
        Files.createDirectories(executionDirectory.resolve(".playwright-artifacts/checkout"));
        Files.createDirectories(executionDirectory.resolve("test-results"));
        Files.writeString(executionDirectory.resolve("reports/allure-report/index.html"), "<html>report</html>");
        Files.writeString(executionDirectory.resolve(".playwright-artifacts/checkout/trace.zip"), "trace");
        Files.writeString(executionDirectory.resolve("test-results/.playwright-results.json"), "{\"suites\":[]}");

        Mockito.when(sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
        Mockito.when(repositoryRepository.findById(21L)).thenReturn(Optional.of(repository));
        Mockito.when(taskRepository.save(Mockito.any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity task = invocation.getArgument(0);
            if (task.getId() == null) {
                task.setId(101L);
            }
            return task;
        });
        Mockito.when(workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L)).thenReturn(workspace);
        Mockito.when(executionService.installDependencies(
                executionDirectory,
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(executionService.runTests(
                executionDirectory,
                "npm run test:e2e",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(1);
        Mockito.when(executionService.generateReport(
                executionDirectory,
                ALLURE_AWESOME_COMMAND,
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(2);
        Mockito.when(archiveService.archiveReport(
                executionDirectory,
                101L,
                "reports/allure-report"))
                .thenReturn("http://minio/qa-report/runs/101/report/index.html");
        Mockito.when(taskCaseResultParseService.parse(
                101L,
                executionDirectory.resolve("test-results/.playwright-results.json"),
                executionDirectory))
                .thenReturn(new ParsedTaskResults(
                        List.of(new ParsedCaseResult(
                                101L,
                                "chromium::checkout::should pay successfully",
                                "checkout :: should pay successfully",
                                "checkout",
                                "should pay successfully",
                                "FAILED",
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

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getReportUrl()).isEqualTo("http://minio/qa-report/runs/101/report/index.html");
        Mockito.verify(executionService).generateReport(
                executionDirectory,
                ALLURE_AWESOME_COMMAND,
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"));
        Mockito.verify(archiveService).archiveReport(executionDirectory, 101L, "reports/allure-report");
        Mockito.verify(taskCaseResultParseService).parse(
                101L,
                executionDirectory.resolve("test-results/.playwright-results.json"),
                executionDirectory);
        Mockito.verify(taskCaseResultPersistenceService).persist(Mockito.anyList());
        Mockito.verify(taskArtifactArchiveService).archiveArtifacts(
                Mockito.eq(101L),
                Mockito.eq(executionDirectory),
                Mockito.eq(List.of(".playwright-artifacts", "reports/allure-report")),
                Mockito.anyMap());
    }

    @Test
    void shouldKeepSceneSummaryAlignedWithNewestTask() {
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
        repository.setWorkingDirectory("playwright_framework");
        repository.setInstallCommand("npm install");
        repository.setReportRelativePath("reports/allure-report");
        repository.setResultsIndexRelativePath("test-results/.playwright-results.json");
        repository.setArtifactRootRelativePath(".playwright-artifacts");

        TaskEntity latestTask = new TaskEntity();
        latestTask.setId(202L);
        latestTask.setSceneId(11L);
        latestTask.setStatus("FAILED");
        latestTask.setFinishedAt(java.time.LocalDateTime.of(2026, 6, 10, 10, 30));

        Mockito.when(sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
        Mockito.when(repositoryRepository.findById(21L)).thenReturn(Optional.of(repository));
        Mockito.when(taskRepository.save(Mockito.any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity task = invocation.getArgument(0);
            if (task.getId() == null) {
                task.setId(101L);
            }
            return task;
        });
        Mockito.when(taskRepository.findFirstBySceneIdOrderByCreatedAtDescIdDesc(11L)).thenReturn(Optional.of(latestTask));
        Mockito.when(workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L)).thenReturn(Path.of("/tmp/task-101"));
        Mockito.when(executionService.installDependencies(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(executionService.runTests(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm run test:e2e",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(archiveService.archiveReport(
                Path.of("/tmp/task-101/playwright_framework"),
                101L,
                "reports/allure-report"))
                .thenReturn("http://minio/qa-report/runs/101/report/index.html");
        Mockito.when(taskCaseResultParseService.parse(
                101L,
                Path.of("/tmp/task-101/playwright_framework/test-results/.playwright-results.json"),
                Path.of("/tmp/task-101/playwright_framework")))
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

        service.createAndRun(11L);

        assertThat(scene.getLastTaskStatus()).isEqualTo("FAILED");
        assertThat(scene.getLastRunAt()).isEqualTo(latestTask.getFinishedAt());
    }

    @Test
    void shouldCreateRunningTaskImmediatelyAndDeferExecutionUntilBackgroundWorkerRuns() {
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
        TaskCommandBuilder taskCommandBuilder = Mockito.mock(TaskCommandBuilder.class);

        SceneEntity scene = new SceneEntity();
        scene.setId(11L);
        scene.setRepoId(21L);
        scene.setBranch("main");
        scene.setBrowser("chromium");

        TestRepositoryEntity repository = new TestRepositoryEntity();
        repository.setId(21L);
        repository.setGitUrl("git@demo/repo.git");
        repository.setWorkingDirectory("playwright_framework");
        repository.setInstallCommand("npm install");
        repository.setTestRoot("tests");
        repository.setReportRelativePath("reports/allure-report");
        repository.setResultsIndexRelativePath("test-results/.playwright-results.json");
        repository.setArtifactRootRelativePath(".playwright-artifacts");

        AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
        Executor executor = scheduledTask::set;

        Mockito.when(sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
        Mockito.when(repositoryRepository.findById(21L)).thenReturn(Optional.of(repository));
        Mockito.when(taskRepository.save(Mockito.any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity task = invocation.getArgument(0);
            if (task.getId() == null) {
                task.setId(101L);
            }
            return task;
        });
        Mockito.when(taskRepository.findById(101L)).thenAnswer(invocation -> {
            TaskEntity task = new TaskEntity();
            task.setId(101L);
            task.setSceneId(11L);
            task.setRepoId(21L);
            task.setStatus("RUNNING");
            task.setTriggerType("MANUAL");
            task.setBranch("main");
            task.setRunnerName("centralized-runner");
            task.setResolvedBranch("main");
            task.setResolvedBrowser("chromium");
            task.setResolvedTestRoot("tests");
            task.setResolvedRunCommand("npx playwright test tests/login.spec.ts --project chromium");
            task.setStartedAt(LocalDateTime.of(2026, 6, 12, 10, 0, 0));
            return Optional.of(task);
        });
        Mockito.when(taskCommandBuilder.buildRunCommand(repository, scene))
                .thenReturn("npx playwright test tests/login.spec.ts --project chromium");
        Mockito.when(workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L)).thenReturn(Path.of("/tmp/task-101"));
        Mockito.when(executionService.installDependencies(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(executionService.runTests(
                Path.of("/tmp/task-101/playwright_framework"),
                "npx playwright test tests/login.spec.ts --project chromium",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(executionService.generateReport(
                Path.of("/tmp/task-101/playwright_framework"),
                ALLURE_AWESOME_COMMAND,
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(archiveService.archiveReport(
                Path.of("/tmp/task-101/playwright_framework"),
                101L,
                "reports/allure-report"))
                .thenReturn("http://minio/qa-report/runs/101/report/index.html");
        Mockito.when(taskCaseResultParseService.parse(
                101L,
                Path.of("/tmp/task-101/playwright_framework/test-results/.playwright-results.json"),
                Path.of("/tmp/task-101/playwright_framework")))
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
                taskCommandBuilder,
                executor,
                "qa-report",
                "http://minio");

        TaskEntity createdTask = service.createAndStart(11L);

        assertThat(createdTask.getStatus()).isEqualTo("RUNNING");
        assertThat(scene.getLastTaskStatus()).isEqualTo("RUNNING");
        assertThat(scene.getLastRunAt()).isEqualTo(createdTask.getStartedAt());
        assertThat(scheduledTask.get()).isNotNull();
        Mockito.verify(executionService, Mockito.never()).installDependencies(Mockito.any(), Mockito.anyString(), Mockito.anyMap());

        scheduledTask.get().run();

        Mockito.verify(executionService).installDependencies(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"));
    }

    @Test
    void shouldRestoreInterruptFlagWhenRunnerExecutionIsInterrupted() throws Exception {
        RunnerExecutionServiceImpl service = new RunnerExecutionServiceImpl();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            try {
                service.runTests(Path.of(System.getProperty("java.io.tmpdir")), "sleep 5", Map.of());
            } catch (Throwable throwable) {
                failure.set(throwable);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });

        worker.start();
        Thread.sleep(200);
        worker.interrupt();
        worker.join(3000);

        assertThat(worker.isAlive()).isFalse();
        assertThat(failure.get()).isInstanceOf(IllegalStateException.class);
        assertThat(failure.get()).hasCauseInstanceOf(InterruptedException.class);
        assertThat(interrupted.get()).isTrue();
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
        Mockito.when(taskRepository.findAll(Mockito.any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(task), PageRequest.of(0, 10), 1));
        Mockito.when(artifactRepository.findAllByTaskIdOrderByIdAsc(101L)).thenReturn(List.of(artifact));
        Mockito.when(objectStorageService.createPresignedGetUrl(
                "qa-report",
                "http://minio/qa-report/runs/101/report/index.html"))
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
        PageResponse<SceneTaskListResponse> tasks = service.list(1, 10);
        String reportUrl = service.getReportUrl(101L);
        List<ArtifactEntity> artifacts = service.listArtifacts(101L);

        assertThat(taskDetail.getReportUrl()).isEqualTo("http://minio/presigned/report");
        assertThat(tasks.items()).hasSize(1);
        assertThat(tasks.items().getFirst().reportUrl()).isEqualTo("http://minio/presigned/report");
        assertThat(reportUrl).isEqualTo("http://minio/presigned/report");
        assertThat(artifacts).hasSize(1);
        assertThat(artifacts.getFirst().getUrl()).isEqualTo("http://minio/presigned/artifact");
        assertThat(artifacts.getFirst().getObjectKey()).isEqualTo("runs/101/artifacts/trace.zip");
    }

    @Test
    void shouldFallbackToStoredUrlsWhenPresignedUrlCreationFails() {
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
        task.setReportUrl("http://127.0.0.1:9000/qa-report/runs/101/report/index.html");

        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setId(1L);
        artifact.setTaskId(101L);
        artifact.setBucket("qa-report");
        artifact.setObjectKey("runs/101/artifacts/trace.zip");
        artifact.setUrl("http://127.0.0.1:9000/qa-report/runs/101/artifacts/trace.zip");

        Mockito.when(taskRepository.findById(101L)).thenReturn(Optional.of(task));
        Mockito.when(taskRepository.findAll(Mockito.any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(task), PageRequest.of(0, 10), 1));
        Mockito.when(artifactRepository.findAllByTaskIdOrderByIdAsc(101L)).thenReturn(List.of(artifact));
        Mockito.when(objectStorageService.createPresignedGetUrl("qa-report", "http://127.0.0.1:9000/qa-report/runs/101/report/index.html"))
                .thenThrow(new IllegalStateException("Failed to create presigned url"));
        Mockito.when(objectStorageService.createPresignedGetUrl("qa-report", "runs/101/artifacts/trace.zip"))
                .thenThrow(new IllegalStateException("Failed to create presigned url"));

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
                "http://127.0.0.1:9000");

        TaskEntity taskDetail = service.get(101L);
        PageResponse<SceneTaskListResponse> tasks = service.list(1, 10);
        List<ArtifactEntity> artifacts = service.listArtifacts(101L);

        assertThat(taskDetail.getReportUrl()).isEqualTo("http://127.0.0.1:9000/qa-report/runs/101/report/index.html");
        assertThat(tasks.items().getFirst().reportUrl()).isEqualTo("http://127.0.0.1:9000/qa-report/runs/101/report/index.html");
        assertThat(artifacts.getFirst().getUrl()).isEqualTo("http://127.0.0.1:9000/qa-report/runs/101/artifacts/trace.zip");
    }

    @Test
    void shouldPreserveCreatedAtWhenListingTasks() {
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

        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 11, 10, 30, 0);
        TaskEntity task = new TaskEntity();
        task.setId(101L);
        task.setSceneId(11L);
        setField(task, "createdAt", createdAt);

        Mockito.when(taskRepository.findAll(Mockito.any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(task), PageRequest.of(0, 10), 1));
        Mockito.when(sceneRepository.existsById(11L)).thenReturn(true);
        Mockito.when(taskRepository.findAllBySceneId(Mockito.eq(11L), Mockito.any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(task), PageRequest.of(0, 10), 1));
        Mockito.when(caseResultRepository.findAllByTaskIdOrderByIdAsc(101L)).thenReturn(List.of());

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

        PageResponse<SceneTaskListResponse> tasks = service.list(1, 10);
        assertThat(tasks.items()).hasSize(1);
        assertThat(tasks.items().getFirst().createdAt()).isEqualTo(createdAt);

        assertThat(service.listByScene(11L, 1, 10).items())
                .singleElement()
                .extracting(sceneTask -> sceneTask.createdAt())
                .isEqualTo(createdAt);
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
        repository.setWorkingDirectory("playwright_framework");
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
        Mockito.when(executionService.installDependencies(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(executionService.runTests(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm run test:e2e",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(archiveService.archiveReport(
                Path.of("/tmp/task-101/playwright_framework"),
                101L,
                "playwright-report"))
                .thenReturn("http://minio/qa-report/runs/101/report/index.html");
        Mockito.when(taskCaseResultParseService.parse(
                101L,
                Path.of("/tmp/task-101/playwright_framework/test-results/.playwright-results.json"),
                Path.of("/tmp/task-101/playwright_framework")))
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
                .archiveArtifacts(
                        Mockito.eq(101L),
                        Mockito.eq(Path.of("/tmp/task-101/playwright_framework")),
                        Mockito.anyList(),
                        Mockito.anyMap());
        Mockito.verify(taskCaseResultPersistenceService).persist(Mockito.anyList());
    }

    @Test
    void shouldDefaultToWorkspaceRootWhenWorkingDirectoryIsBlank() {
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
        repository.setWorkingDirectory("");
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
        Mockito.when(executionService.installDependencies(
                Path.of("/tmp/task-101"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(executionService.runTests(
                Path.of("/tmp/task-101"),
                "npm run test:e2e",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(archiveService.archiveReport(Path.of("/tmp/task-101"), 101L, "playwright-report"))
                .thenReturn("http://minio/qa-report/runs/101/report/index.html");
        Mockito.when(taskCaseResultParseService.parse(
                101L,
                Path.of("/tmp/task-101/test-results/.playwright-results.json"),
                Path.of("/tmp/task-101")))
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
        Mockito.verify(executionService).installDependencies(
                Path.of("/tmp/task-101"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"));
    }

    @Test
    void shouldFailTaskWhenReportPathEscapesExecutionDirectory() {
        TaskEntity result = createTaskWithEscapedExecutionPath("../reports/allure-report", "test-results/.playwright-results.json",
                ".playwright-artifacts");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getLogUrl()).contains("Report relative path escapes execution directory");
    }

    @Test
    void shouldFailTaskWhenResultsPathEscapesExecutionDirectory() {
        TaskEntity result = createTaskWithEscapedExecutionPath("reports/allure-report", "../test-results/.playwright-results.json",
                ".playwright-artifacts");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getLogUrl()).contains("Results relative path escapes execution directory");
    }

    @Test
    void shouldFailTaskWhenArtifactPathEscapesExecutionDirectory() {
        TaskEntity result = createTaskWithEscapedExecutionPath("reports/allure-report", "test-results/.playwright-results.json",
                "../.playwright-artifacts");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getLogUrl()).contains("Artifact relative path escapes execution directory");
    }

    @Test
    void shouldKeepTaskSuccessfulWhenCaseResultParsingFails() {
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
        repository.setWorkingDirectory("playwright_framework");
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
        Mockito.when(executionService.installDependencies(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(executionService.runTests(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm run test:e2e",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(archiveService.archiveReport(
                Path.of("/tmp/task-101/playwright_framework"),
                101L,
                "playwright-report"))
                .thenReturn("http://minio/qa-report/runs/101/report/index.html");
        Mockito.when(taskCaseResultParseService.parse(
                101L,
                Path.of("/tmp/task-101/playwright_framework/test-results/.playwright-results.json"),
                Path.of("/tmp/task-101/playwright_framework")))
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

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getReportUrl()).isEqualTo("http://minio/qa-report/runs/101/report/index.html");
        assertThat(result.getLogUrl()).contains("results index missing");
    }

    @Test
    void shouldExposeNotFoundWhenListingTasksBySceneForMissingScene() {
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

        Mockito.when(sceneRepository.existsById(999L)).thenReturn(false);

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

        org.springframework.web.server.ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.listByScene(999L, 1, 10));
        assertThat(exception.getStatusCode().value()).isEqualTo(404);
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to set field: " + fieldName, exception);
        }
    }

    private TaskEntity createTaskWithEscapedExecutionPath(
            String reportRelativePath,
            String resultsIndexRelativePath,
            String artifactRootRelativePath) {
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
        repository.setWorkingDirectory("playwright_framework");
        repository.setInstallCommand("npm install");
        repository.setReportRelativePath(reportRelativePath);
        repository.setResultsIndexRelativePath(resultsIndexRelativePath);
        repository.setArtifactRootRelativePath(artifactRootRelativePath);

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

        Mockito.verify(executionService, Mockito.never()).installDependencies(Mockito.any(), Mockito.anyString(), Mockito.anyMap());
        Mockito.verify(executionService, Mockito.never()).runTests(Mockito.any(), Mockito.anyString(), Mockito.anyMap());
        Mockito.verifyNoInteractions(archiveService, taskArtifactArchiveService, taskCaseResultParseService,
                taskCaseResultPersistenceService);
        return result;
    }
}
