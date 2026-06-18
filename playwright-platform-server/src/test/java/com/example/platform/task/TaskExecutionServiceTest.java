package com.example.platform.task;

import com.example.platform.common.PageResponse;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.model.TestRepositoryJpaRepository;
import com.example.platform.runner.service.DockerRunnerCommandExecutor;
import com.example.platform.runner.service.DockerRunnerProperties;
import com.example.platform.runner.service.LocalRunnerCommandExecutor;
import com.example.platform.runner.service.RunnerCommandExecutor;
import com.example.platform.runner.service.RunnerCommandExecutorConfig;
import com.example.platform.runner.service.RunnerCommandRequest;
import com.example.platform.runner.service.RunnerCommandResult;
import com.example.platform.runner.service.RunnerExecutionService;
import com.example.platform.runner.service.RunnerExecutionServiceImpl;
import com.example.platform.runner.service.RunnerMode;
import com.example.platform.runner.service.RunnerProperties;
import com.example.platform.runner.service.RunnerWorkspaceService;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.model.SceneJpaRepository;
import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.dto.SceneTaskListResponse;
import com.example.platform.task.dto.TaskStageLogResponse;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.ArtifactJpaRepository;
import com.example.platform.task.model.CaseResultJpaRepository;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.model.TaskJpaRepository;
import com.example.platform.task.model.TaskStageLogEntity;
import com.example.platform.task.parser.ParsedArtifactBinding;
import com.example.platform.task.parser.ParsedCaseResult;
import com.example.platform.task.parser.ParsedTaskResults;
import com.example.platform.task.service.TaskArtifactArchiveService;
import com.example.platform.task.service.TaskCaseResultParseService;
import com.example.platform.task.service.TaskCaseResultPersistenceService;
import com.example.platform.task.service.TaskCommandBuilder;
import com.example.platform.task.service.TaskCommandBuilderImpl;
import com.example.platform.task.service.TaskStageLogService;
import com.example.platform.task.service.TaskServiceImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskExecutionServiceTest {
    @TempDir
    Path tempDir;

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
        TestContext context = new TestContext();
        SceneEntity scene = context.scene();
        scene.setBrowser("chromium");
        scene.setEnvJson("{\"BASE_URL\":\"https://example.com\"}");
        scene.setMatchValue("login.spec.ts");

        TestRepositoryEntity repository = context.repository();
        repository.setRunCommandTemplate("node ./scripts/run-e2e.cjs");
        repository.setTestRoot("tests");

        Mockito.when(context.taskCommandBuilder.buildRunCommand(repository, scene))
                .thenReturn("node ./scripts/run-e2e.cjs --project chromium --target tests/login.spec.ts");
        Mockito.when(context.sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
        Mockito.when(context.repositoryRepository.findById(21L)).thenReturn(Optional.of(repository));
        context.mockSaveWithGeneratedId();
        Mockito.when(context.workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L))
                .thenReturn(Path.of("/tmp/task-101"));
        Mockito.when(context.executionService.installDependencies(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(context.executionService.runTests(
                Path.of("/tmp/task-101/playwright_framework"),
                "node ./scripts/run-e2e.cjs --project chromium --target tests/login.spec.ts",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(context.taskCaseResultParseService.parse(
                101L,
                Path.of("/tmp/task-101/playwright_framework/test-results/.playwright-results.json"),
                Path.of("/tmp/task-101/playwright_framework")))
                .thenReturn(new ParsedTaskResults(List.of(), List.of()));
        Mockito.when(context.taskCaseResultPersistenceService.persist(Mockito.anyList())).thenReturn(Map.of());

        TaskEntity result = context.service().createAndRun(11L);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getCurrentStage()).isEqualTo("FINISHED");
        assertThat(result.getResolvedBranch()).isEqualTo("main");
        assertThat(result.getResolvedBrowser()).isEqualTo("chromium");
        assertThat(result.getResolvedEnvJson()).contains("BASE_URL");
        assertThat(result.getResolvedMatchValue()).isEqualTo("login.spec.ts");
        assertThat(result.getResolvedTestRoot()).isEqualTo("tests");
        assertThat(result.getResolvedRunCommand()).contains("--target tests/login.spec.ts");
        Mockito.verify(context.taskArtifactArchiveService).archiveArtifacts(
                101L,
                Path.of("/tmp/task-101/playwright_framework"),
                List.of(".playwright-artifacts"),
                Map.of());
    }

    @Test
    void shouldContinuePostProcessingAfterFailedTestsWhenFilesExist() throws Exception {
        TestContext context = new TestContext();
        SceneEntity scene = context.scene();
        TestRepositoryEntity repository = context.repository();

        Path workspace = tempDir.resolve("task-101");
        Path executionDirectory = workspace.resolve("playwright_framework");
        Files.createDirectories(executionDirectory.resolve(".playwright-artifacts/checkout"));
        Files.createDirectories(executionDirectory.resolve("test-results"));
        Files.writeString(executionDirectory.resolve(".playwright-artifacts/checkout/trace.zip"), "trace");
        Files.writeString(executionDirectory.resolve("test-results/.playwright-results.json"), "{\"suites\":[]}");

        Mockito.when(context.sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
        Mockito.when(context.repositoryRepository.findById(21L)).thenReturn(Optional.of(repository));
        context.mockSaveWithGeneratedId();
        Mockito.when(context.workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L)).thenReturn(workspace);
        Mockito.when(context.executionService.installDependencies(
                executionDirectory,
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(context.executionService.runTests(
                executionDirectory,
                "npm run test:e2e",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(1);
        Mockito.when(context.taskCaseResultParseService.parse(
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
        Mockito.when(context.taskCaseResultPersistenceService.persist(Mockito.anyList()))
                .thenReturn(Map.of("chromium::checkout::should pay successfully", 1001L));

        TaskEntity result = context.service().createAndRun(11L);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getResultCode()).isEqualTo("TEST_FAILED");
        Mockito.verify(context.taskCaseResultPersistenceService).persist(Mockito.anyList());
        Mockito.verify(context.taskArtifactArchiveService).archiveArtifacts(
                Mockito.eq(101L),
                Mockito.eq(executionDirectory),
                Mockito.eq(List.of(".playwright-artifacts")),
                Mockito.anyMap());
    }

    @Test
    void shouldPassWorkspaceRootAndStageNamesToRunnerStages() {
        TestContext context = new TestContext();
        SceneEntity scene = context.scene();
        TestRepositoryEntity repository = context.repository();
        Path workspace = Path.of("/tmp/task-101");
        Path executionDirectory = workspace.resolve("playwright_framework");
        Map<String, String> platformEnv = Map.of("PLAYWRIGHT_PLATFORM_MODE", "true");

        Mockito.when(context.sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
        Mockito.when(context.repositoryRepository.findById(21L)).thenReturn(Optional.of(repository));
        context.mockSaveWithGeneratedId();
        Mockito.when(context.workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L)).thenReturn(workspace);
        Mockito.when(context.executionService.runStage(
                Mockito.eq(workspace),
                Mockito.eq(executionDirectory),
                Mockito.eq("INSTALL"),
                Mockito.eq("npm install"),
                Mockito.eq(platformEnv),
                Mockito.any(),
                Mockito.any()))
                .thenReturn(new RunnerCommandResult(0, false, false, 10L, null, 0));
        Mockito.when(context.executionService.runStage(
                Mockito.eq(workspace),
                Mockito.eq(executionDirectory),
                Mockito.eq("TEST"),
                Mockito.eq("npm run test:e2e"),
                Mockito.eq(platformEnv),
                Mockito.any(),
                Mockito.any()))
                .thenReturn(new RunnerCommandResult(0, false, false, 10L, null, 0));
        Mockito.when(context.taskCaseResultParseService.parse(
                101L,
                executionDirectory.resolve("test-results/.playwright-results.json"),
                executionDirectory))
                .thenReturn(new ParsedTaskResults(List.of(), List.of()));
        Mockito.when(context.taskCaseResultPersistenceService.persist(Mockito.anyList())).thenReturn(Map.of());

        TaskEntity result = context.service().createAndRun(11L);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        Mockito.verify(context.executionService).runStage(
                Mockito.eq(workspace),
                Mockito.eq(executionDirectory),
                Mockito.eq("INSTALL"),
                Mockito.eq("npm install"),
                Mockito.eq(platformEnv),
                Mockito.any(),
                Mockito.any());
        Mockito.verify(context.executionService).runStage(
                Mockito.eq(workspace),
                Mockito.eq(executionDirectory),
                Mockito.eq("TEST"),
                Mockito.eq("npm run test:e2e"),
                Mockito.eq(platformEnv),
                Mockito.any(),
                Mockito.any());
    }

    @Test
    void shouldKeepSceneSummaryAlignedWithNewestTask() {
        TestContext context = new TestContext();
        SceneEntity scene = context.scene();
        TestRepositoryEntity repository = context.repository();

        TaskEntity latestTask = new TaskEntity();
        latestTask.setId(202L);
        latestTask.setSceneId(11L);
        latestTask.setStatus("FAILED");
        latestTask.setFinishedAt(LocalDateTime.of(2026, 6, 10, 10, 30));

        Mockito.when(context.sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
        Mockito.when(context.repositoryRepository.findById(21L)).thenReturn(Optional.of(repository));
        context.mockSaveWithGeneratedId();
        Mockito.when(context.taskRepository.findFirstBySceneIdOrderByCreatedAtDescIdDesc(11L)).thenReturn(Optional.of(latestTask));
        Mockito.when(context.workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L)).thenReturn(Path.of("/tmp/task-101"));
        Mockito.when(context.executionService.installDependencies(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(context.executionService.runTests(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm run test:e2e",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(context.taskCaseResultParseService.parse(
                101L,
                Path.of("/tmp/task-101/playwright_framework/test-results/.playwright-results.json"),
                Path.of("/tmp/task-101/playwright_framework")))
                .thenReturn(new ParsedTaskResults(List.of(), List.of()));
        Mockito.when(context.taskCaseResultPersistenceService.persist(Mockito.anyList())).thenReturn(Map.of());

        context.service().createAndRun(11L);

        assertThat(scene.getLastTaskStatus()).isEqualTo("FAILED");
        assertThat(scene.getLastRunAt()).isEqualTo(latestTask.getFinishedAt());
    }

    @Test
    void shouldCreateQueuedTaskImmediatelyAndDeferExecutionUntilBackgroundWorkerRuns() {
        TestContext context = new TestContext();
        SceneEntity scene = context.scene();
        scene.setBrowser("chromium");
        TestRepositoryEntity repository = context.repository();
        repository.setTestRoot("tests");

        AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
        Executor executor = scheduledTask::set;

        Mockito.when(context.sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
        Mockito.when(context.repositoryRepository.findById(21L)).thenReturn(Optional.of(repository));
        context.mockSaveWithGeneratedId();
        Mockito.when(context.taskRepository.findById(101L)).thenAnswer(invocation -> {
            TaskEntity task = new TaskEntity();
            task.setId(101L);
            task.setSceneId(11L);
            task.setRepoId(21L);
            task.setStatus("QUEUED");
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
        Mockito.when(context.taskCommandBuilder.buildRunCommand(repository, scene))
                .thenReturn("npx playwright test tests/login.spec.ts --project chromium");
        Mockito.when(context.workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L)).thenReturn(Path.of("/tmp/task-101"));
        Mockito.when(context.executionService.installDependencies(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(context.executionService.runTests(
                Path.of("/tmp/task-101/playwright_framework"),
                "npx playwright test tests/login.spec.ts --project chromium",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(context.taskCaseResultParseService.parse(
                101L,
                Path.of("/tmp/task-101/playwright_framework/test-results/.playwright-results.json"),
                Path.of("/tmp/task-101/playwright_framework")))
                .thenReturn(new ParsedTaskResults(List.of(), List.of()));
        Mockito.when(context.taskCaseResultPersistenceService.persist(Mockito.anyList())).thenReturn(Map.of());

        TaskEntity createdTask = context.service(executor).createAndStart(11L);

        assertThat(createdTask.getStatus()).isEqualTo("QUEUED");
        assertThat(createdTask.getCurrentStage()).isEqualTo("QUEUED");
        assertThat(createdTask.getQueuedAt()).isNotNull();
        assertThat(scene.getLastTaskStatus()).isEqualTo("QUEUED");
        assertThat(scene.getLastRunAt()).isEqualTo(createdTask.getQueuedAt());
        assertThat(scheduledTask.get()).isNotNull();
        Mockito.verify(context.executionService, Mockito.never()).installDependencies(Mockito.any(), Mockito.anyString(), Mockito.anyMap());

        scheduledTask.get().run();

        Mockito.verify(context.executionService).installDependencies(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"));
    }

    @Test
    void shouldRestoreInterruptFlagWhenRunnerExecutionIsInterrupted() throws Exception {
        RunnerExecutionServiceImpl service = new RunnerExecutionServiceImpl(new LocalRunnerCommandExecutor());
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
    void shouldCaptureCombinedLogOutputToFile() throws Exception {
        RunnerCommandExecutor executor = new LocalRunnerCommandExecutor();
        RunnerCommandRequest request = new RunnerCommandRequest(
                tempDir,
                tempDir,
                "TEST",
                "printf 'line1\\nline2\\n'",
                Map.of(),
                Duration.ofSeconds(5),
                () -> false);

        RunnerCommandResult result = executor.execute(request);

        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.timedOut()).isFalse();
        assertThat(result.canceled()).isFalse();
        assertThat(result.lineCount()).isEqualTo(2);
        assertThat(Files.readString(result.combinedLogFile())).contains("line1").contains("line2");
    }

    @Test
    void shouldCreateLocalRunnerCommandExecutorFromConfiguration() {
        RunnerProperties runnerProperties = new RunnerProperties();
        runnerProperties.setMode(RunnerMode.LOCAL);
        RunnerCommandExecutorConfig config = new RunnerCommandExecutorConfig();

        RunnerCommandExecutor executor = config.runnerCommandExecutor(runnerProperties, new DockerRunnerProperties());

        assertThat(executor).isInstanceOf(LocalRunnerCommandExecutor.class);
    }

    @Test
    void shouldCreateDockerRunnerCommandExecutorForDockerMode() {
        RunnerProperties runnerProperties = new RunnerProperties();
        runnerProperties.setMode(RunnerMode.DOCKER);
        runnerProperties.setWorkspaceRoot(Path.of("/tmp/playwright-platform/workspaces"));
        runnerProperties.setHostWorkspaceRoot(Path.of("/tmp/playwright-platform/workspaces"));
        RunnerCommandExecutorConfig config = new RunnerCommandExecutorConfig();

        RunnerCommandExecutor executor = config.runnerCommandExecutor(runnerProperties, new DockerRunnerProperties());

        assertThat(executor).isInstanceOf(DockerRunnerCommandExecutor.class);
    }

    @Test
    void shouldNotRegisterLegacyRunnerCommandExecutorImplAsSpringBean() throws Exception {
        Class<?> legacyExecutor = Class.forName("com.example.platform.runner.service.RunnerCommandExecutorImpl");

        assertThat(legacyExecutor.getAnnotation(Service.class)).isNull();
    }

    @Test
    void shouldMarkCancelRequestedForRunningTask() {
        TestContext context = new TestContext();
        TaskEntity task = new TaskEntity();
        task.setId(101L);
        task.setStatus("RUNNING");
        Mockito.when(context.taskRepository.findById(101L)).thenReturn(Optional.of(task));
        Mockito.when(context.taskRepository.save(Mockito.any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        context.service().cancelTask(101L, "demo-user");

        assertThat(task.getCancelRequested()).isTrue();
        assertThat(task.getCancelRequestedAt()).isNotNull();
        assertThat(task.getCancelRequestedBy()).isEqualTo("demo-user");
        assertThat(task.getTriggerUser()).isNull();
    }

    @Test
    void shouldRejectStartingTaskWhenSceneAlreadyHasQueuedOrRunningTask() {
        TestContext context = new TestContext();
        SceneEntity scene = context.scene();
        TestRepositoryEntity repository = context.repository();

        Mockito.when(context.sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
        Mockito.when(context.repositoryRepository.findById(21L)).thenReturn(Optional.of(repository));
        Mockito.when(context.taskRepository.existsBySceneIdAndStatusIn(11L, List.of("QUEUED", "RUNNING"))).thenReturn(true);

        assertThatThrownBy(() -> context.service().createAndStart(11L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("当前场景已有执行中的任务，请稍后再试");
    }

    @Test
    void shouldFailQueuedTaskWhenExecutorRejectsDispatch() {
        TestContext context = new TestContext();
        SceneEntity scene = context.scene();
        TestRepositoryEntity repository = context.repository();

        TaskEntity[] savedTaskRef = new TaskEntity[1];
        Executor rejectingExecutor = command -> {
            throw new java.util.concurrent.RejectedExecutionException("queue full");
        };

        Mockito.when(context.sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
        Mockito.when(context.repositoryRepository.findById(21L)).thenReturn(Optional.of(repository));
        Mockito.when(context.taskRepository.save(Mockito.any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity task = invocation.getArgument(0);
            if (task.getId() == null) {
                task.setId(101L);
            }
            savedTaskRef[0] = task;
            return task;
        });
        Mockito.when(context.taskRepository.findById(101L)).thenAnswer(invocation -> Optional.ofNullable(savedTaskRef[0]));

        assertThatThrownBy(() -> context.service(rejectingExecutor).createAndStart(11L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("任务执行队列已满");

        assertThat(savedTaskRef[0].getStatus()).isEqualTo("FAILED");
        assertThat(savedTaskRef[0].getResultCode()).isEqualTo("SYSTEM_BUSY");
        assertThat(savedTaskRef[0].getCurrentStage()).isEqualTo("FINISHED");
    }

    @Test
    void shouldSetStructuredResultCodeWhenInstallStageFails() throws Exception {
        TestContext context = new TestContext();
        SceneEntity scene = context.scene();
        scene.setBrowser("chromium");
        TaskStageLogService taskStageLogService = Mockito.mock(TaskStageLogService.class);

        Path installLog = Files.createTempFile("install-stage-", ".log");
        Files.writeString(installLog, "install failed\n");

        Mockito.when(context.sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
        Mockito.when(context.repositoryRepository.findById(21L)).thenReturn(Optional.of(context.repository()));
        context.mockSaveWithGeneratedId();
        Mockito.when(context.workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L)).thenReturn(tempDir);
        Mockito.when(context.executionService.runStage(
                Mockito.any(),
                Mockito.any(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyMap(),
                Mockito.any(),
                Mockito.any()))
                .thenReturn(new RunnerCommandResult(1, false, false, 10L, installLog, 1));

        TaskEntity result = context.service(taskStageLogService).createAndRun(11L);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getResultCode()).isEqualTo("INSTALL_FAILED");
    }

    @Test
    void shouldListStageLogsWithPresignedDownloadUrls() {
        TestContext context = new TestContext();
        TaskStageLogService taskStageLogService = Mockito.mock(TaskStageLogService.class);

        TaskStageLogEntity entity = new TaskStageLogEntity();
        entity.setId(1L);
        entity.setTaskId(101L);
        entity.setStage("TESTING");
        entity.setStreamType("COMBINED");
        entity.setPreviewText("line1");
        entity.setLineCount(2);
        entity.setObjectKey("runs/101/logs/testing.log");

        Mockito.when(taskStageLogService.listByTaskId(101L)).thenReturn(List.of(entity));
        Mockito.when(context.objectStorageService.createPresignedGetUrl("qa-report", "runs/101/logs/testing.log"))
                .thenReturn("http://minio/presigned/testing.log");

        List<TaskStageLogResponse> logs = context.service(taskStageLogService).listStageLogs(101L);

        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().stage()).isEqualTo("TESTING");
        assertThat(logs.getFirst().downloadUrl()).isEqualTo("http://minio/presigned/testing.log");
    }

    @Test
    void shouldReturnPresignedUrlsForArtifacts() {
        TestContext context = new TestContext();

        TaskEntity task = new TaskEntity();
        task.setId(101L);

        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setId(1L);
        artifact.setTaskId(101L);
        artifact.setBucket("qa-report");
        artifact.setObjectKey("runs/101/artifacts/trace.zip");
        artifact.setUrl("http://minio/qa-report/runs/101/artifacts/trace.zip");

        Mockito.when(context.taskRepository.findAll(Mockito.any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(task), PageRequest.of(0, 10), 1));
        Mockito.when(context.artifactRepository.findAllByTaskIdOrderByIdAsc(101L)).thenReturn(List.of(artifact));
        Mockito.when(context.caseResultRepository.findAllByTaskIdOrderByIdAsc(101L)).thenReturn(List.of());
        Mockito.when(context.objectStorageService.createPresignedGetUrl("qa-report", "runs/101/artifacts/trace.zip"))
                .thenReturn("http://minio/presigned/artifact");

        PageResponse<SceneTaskListResponse> tasks = context.service().list(1, 10);
        List<ArtifactEntity> artifacts = context.service().listArtifacts(101L);

        assertThat(tasks.items()).hasSize(1);
        assertThat(artifacts).hasSize(1);
        assertThat(artifacts.getFirst().getUrl()).isEqualTo("http://minio/presigned/artifact");
    }

    @Test
    void shouldFallbackToStoredArtifactUrlWhenPresignedUrlCreationFails() {
        TestContext context = new TestContext();

        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setId(1L);
        artifact.setTaskId(101L);
        artifact.setBucket("qa-report");
        artifact.setObjectKey("runs/101/artifacts/trace.zip");
        artifact.setUrl("http://127.0.0.1:9000/qa-report/runs/101/artifacts/trace.zip");

        Mockito.when(context.artifactRepository.findAllByTaskIdOrderByIdAsc(101L)).thenReturn(List.of(artifact));
        Mockito.when(context.objectStorageService.createPresignedGetUrl("qa-report", "runs/101/artifacts/trace.zip"))
                .thenThrow(new IllegalStateException("Failed to create presigned url"));

        List<ArtifactEntity> artifacts = context.service().listArtifacts(101L);

        assertThat(artifacts.getFirst().getUrl())
                .isEqualTo("http://127.0.0.1:9000/qa-report/runs/101/artifacts/trace.zip");
    }

    @Test
    void shouldDefaultToWorkspaceRootWhenWorkingDirectoryIsBlank() {
        TestContext context = new TestContext();
        SceneEntity scene = context.scene();
        TestRepositoryEntity repository = context.repository();
        repository.setWorkingDirectory("");

        Mockito.when(context.sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
        Mockito.when(context.repositoryRepository.findById(21L)).thenReturn(Optional.of(repository));
        context.mockSaveWithGeneratedId();
        Mockito.when(context.workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L)).thenReturn(Path.of("/tmp/task-101"));
        Mockito.when(context.executionService.installDependencies(
                Path.of("/tmp/task-101"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(context.executionService.runTests(
                Path.of("/tmp/task-101"),
                "npm run test:e2e",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(context.taskCaseResultParseService.parse(
                101L,
                Path.of("/tmp/task-101/test-results/.playwright-results.json"),
                Path.of("/tmp/task-101")))
                .thenReturn(new ParsedTaskResults(List.of(), List.of()));
        Mockito.when(context.taskCaseResultPersistenceService.persist(Mockito.anyList())).thenReturn(Map.of());

        TaskEntity result = context.service().createAndRun(11L);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        Mockito.verify(context.executionService).installDependencies(
                Path.of("/tmp/task-101"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"));
    }

    @Test
    void shouldFailTaskWhenResultsPathEscapesExecutionDirectory() {
        TaskEntity result = createTaskWithEscapedExecutionPath("../test-results/.playwright-results.json", ".playwright-artifacts");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getLogUrl()).contains("Results relative path escapes execution directory");
    }

    @Test
    void shouldFailTaskWhenArtifactPathEscapesExecutionDirectory() {
        TaskEntity result = createTaskWithEscapedExecutionPath("test-results/.playwright-results.json", "../.playwright-artifacts");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getLogUrl()).contains("Artifact relative path escapes execution directory");
    }

    @Test
    void shouldKeepTaskSuccessfulWhenCaseResultParsingFails() {
        TestContext context = new TestContext();
        SceneEntity scene = context.scene();
        TestRepositoryEntity repository = context.repository();

        Mockito.when(context.sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
        Mockito.when(context.repositoryRepository.findById(21L)).thenReturn(Optional.of(repository));
        context.mockSaveWithGeneratedId();
        Mockito.when(context.workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L)).thenReturn(Path.of("/tmp/task-101"));
        Mockito.when(context.executionService.installDependencies(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(context.executionService.runTests(
                Path.of("/tmp/task-101/playwright_framework"),
                "npm run test:e2e",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
        Mockito.when(context.taskCaseResultParseService.parse(
                101L,
                Path.of("/tmp/task-101/playwright_framework/test-results/.playwright-results.json"),
                Path.of("/tmp/task-101/playwright_framework")))
                .thenThrow(new IllegalStateException("results index missing"));

        TaskEntity result = context.service().createAndRun(11L);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getLogUrl()).contains("results index missing");
    }

    @Test
    void shouldExposeNotFoundWhenListingTasksBySceneForMissingScene() {
        TestContext context = new TestContext();
        Mockito.when(context.sceneRepository.existsById(999L)).thenReturn(false);

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> context.service().listByScene(999L, 1, 10));
        assertThat(exception.getStatusCode().value()).isEqualTo(404);
    }

    private TaskEntity createTaskWithEscapedExecutionPath(
            String resultsIndexRelativePath,
            String artifactRootRelativePath) {
        TestContext context = new TestContext();
        SceneEntity scene = context.scene();
        TestRepositoryEntity repository = context.repository();
        repository.setResultsIndexRelativePath(resultsIndexRelativePath);
        repository.setArtifactRootRelativePath(artifactRootRelativePath);

        Mockito.when(context.sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
        Mockito.when(context.repositoryRepository.findById(21L)).thenReturn(Optional.of(repository));
        context.mockSaveWithGeneratedId();
        Mockito.when(context.workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L)).thenReturn(Path.of("/tmp/task-101"));

        TaskEntity result = context.service().createAndRun(11L);

        Mockito.verify(context.executionService, Mockito.never()).installDependencies(Mockito.any(), Mockito.anyString(), Mockito.anyMap());
        Mockito.verify(context.executionService, Mockito.never()).runTests(Mockito.any(), Mockito.anyString(), Mockito.anyMap());
        Mockito.verifyNoInteractions(
                context.taskArtifactArchiveService,
                context.taskCaseResultParseService,
                context.taskCaseResultPersistenceService);
        return result;
    }

    private static final class TestContext {
        private final SceneJpaRepository sceneRepository = Mockito.mock(SceneJpaRepository.class);
        private final TestRepositoryJpaRepository repositoryRepository = Mockito.mock(TestRepositoryJpaRepository.class);
        private final TaskJpaRepository taskRepository = Mockito.mock(TaskJpaRepository.class);
        private final ArtifactJpaRepository artifactRepository = Mockito.mock(ArtifactJpaRepository.class);
        private final CaseResultJpaRepository caseResultRepository = Mockito.mock(CaseResultJpaRepository.class);
        private final RunnerWorkspaceService workspaceService = Mockito.mock(RunnerWorkspaceService.class);
        private final RunnerExecutionService executionService = Mockito.mock(RunnerExecutionService.class);
        private final TaskArtifactArchiveService taskArtifactArchiveService = Mockito.mock(TaskArtifactArchiveService.class);
        private final TaskCaseResultParseService taskCaseResultParseService = Mockito.mock(TaskCaseResultParseService.class);
        private final TaskCaseResultPersistenceService taskCaseResultPersistenceService = Mockito.mock(TaskCaseResultPersistenceService.class);
        private final ObjectStorageService objectStorageService = Mockito.mock(ObjectStorageService.class);
        private final TaskCommandBuilder taskCommandBuilder = Mockito.mock(TaskCommandBuilder.class);

        private TestContext() {
            Mockito.when(taskCommandBuilder.buildRunCommand(Mockito.any(), Mockito.any())).thenReturn("npm run test:e2e");
        }

        private SceneEntity scene() {
            SceneEntity scene = new SceneEntity();
            scene.setId(11L);
            scene.setRepoId(21L);
            scene.setBranch("main");
            scene.setRunCommand("npm run test:e2e");
            return scene;
        }

        private TestRepositoryEntity repository() {
            TestRepositoryEntity repository = new TestRepositoryEntity();
            repository.setId(21L);
            repository.setEnabled(true);
            repository.setGitUrl("git@demo/repo.git");
            repository.setDefaultBranch("main");
            repository.setWorkingDirectory("playwright_framework");
            repository.setInstallCommand("npm install");
            repository.setRunCommandTemplate("npm run test:e2e");
            repository.setTestRoot("tests");
            repository.setResultsIndexRelativePath("test-results/.playwright-results.json");
            repository.setArtifactRootRelativePath(".playwright-artifacts");
            return repository;
        }

        private void mockSaveWithGeneratedId() {
            Mockito.when(taskRepository.save(Mockito.any(TaskEntity.class))).thenAnswer(invocation -> {
                TaskEntity task = invocation.getArgument(0);
                if (task.getId() == null) {
                    task.setId(101L);
                }
                return task;
            });
        }

        private TaskServiceImpl service() {
            return new TaskServiceImpl(
                    sceneRepository,
                    repositoryRepository,
                    taskRepository,
                    artifactRepository,
                    caseResultRepository,
                    workspaceService,
                    executionService,
                    taskArtifactArchiveService,
                    taskCaseResultParseService,
                    taskCaseResultPersistenceService,
                    objectStorageService,
                    taskCommandBuilder,
                    "qa-report",
                    "http://minio");
        }

        private TaskServiceImpl service(Executor executor) {
            return new TaskServiceImpl(
                    sceneRepository,
                    repositoryRepository,
                    taskRepository,
                    artifactRepository,
                    caseResultRepository,
                    workspaceService,
                    executionService,
                    taskArtifactArchiveService,
                    taskCaseResultParseService,
                    taskCaseResultPersistenceService,
                    objectStorageService,
                    taskCommandBuilder,
                    executor,
                    "qa-report",
                    "http://minio");
        }

        private TaskServiceImpl service(TaskStageLogService taskStageLogService) {
            return new TaskServiceImpl(
                    sceneRepository,
                    repositoryRepository,
                    taskRepository,
                    artifactRepository,
                    caseResultRepository,
                    workspaceService,
                    executionService,
                    taskArtifactArchiveService,
                    taskCaseResultParseService,
                    taskCaseResultPersistenceService,
                    objectStorageService,
                    taskCommandBuilder,
                    taskStageLogService,
                    "qa-report",
                    "http://minio");
        }
    }
}
