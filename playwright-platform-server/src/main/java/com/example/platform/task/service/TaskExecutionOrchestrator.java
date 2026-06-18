package com.example.platform.task.service;

import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.runner.service.RunnerCommandResult;
import com.example.platform.runner.service.RunnerExecutionService;
import com.example.platform.runner.service.RunnerWorkspaceService;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.task.parser.ParsedArtifactBinding;
import com.example.platform.task.parser.ParsedTaskResults;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.mapper.TaskMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class TaskExecutionOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutionOrchestrator.class);

    private final TaskMapper taskRepository;
    private final SceneMapper sceneMapper;
    private final RunnerWorkspaceService runnerWorkspaceService;
    private final RunnerExecutionService runnerExecutionService;
    private final TaskArtifactArchiveService taskArtifactArchiveService;
    private final TaskCaseResultParseService taskCaseResultParseService;
    private final TaskCaseResultPersistenceService taskCaseResultPersistenceService;
    private final TaskStageLogService taskStageLogService;
    private final TaskExecutionProperties taskExecutionProperties;

    TaskExecutionOrchestrator(
            TaskMapper taskRepository,
            SceneMapper sceneMapper,
            RunnerWorkspaceService runnerWorkspaceService,
            RunnerExecutionService runnerExecutionService,
            TaskArtifactArchiveService taskArtifactArchiveService,
            TaskCaseResultParseService taskCaseResultParseService,
            TaskCaseResultPersistenceService taskCaseResultPersistenceService,
            TaskStageLogService taskStageLogService,
            TaskExecutionProperties taskExecutionProperties) {
        this.taskRepository = taskRepository;
        this.sceneMapper = sceneMapper;
        this.runnerWorkspaceService = runnerWorkspaceService;
        this.runnerExecutionService = runnerExecutionService;
        this.taskArtifactArchiveService = taskArtifactArchiveService;
        this.taskCaseResultParseService = taskCaseResultParseService;
        this.taskCaseResultPersistenceService = taskCaseResultPersistenceService;
        this.taskStageLogService = taskStageLogService;
        this.taskExecutionProperties = taskExecutionProperties;
    }

    TaskEntity executeTask(TaskEntity task, TestRepositoryEntity repository, SceneEntity scene) {
        int installStatus = 1;
        int runStatus = 1;
        try {
            if (task.getStartedAt() == null) {
                task.setStartedAt(LocalDateTime.now());
            }
            task.setStatus("RUNNING");
            task.setCurrentStage("PREPARING");
            taskRepository.update(task);
            Path workspace = runnerWorkspaceService.prepareWorkspace(
                    repository.getGitUrl(),
                    task.getResolvedBranch(),
                    task.getId());
            Path executionDirectory = resolveExecutionDirectory(workspace, repository);
            Map<String, String> platformEnv = platformEnv();
            ResolvedExecutionPaths executionPaths = resolveExecutionPaths(executionDirectory, repository);
            task.setCurrentStage("INSTALLING");
            taskRepository.update(task);
            RunnerCommandResult installResult = runStageWithFallback(
                    task,
                    workspace,
                    executionDirectory,
                    repository.getInstallCommand(),
                    platformEnv,
                    Duration.ofSeconds(taskExecutionProperties.getInstallTimeoutSeconds()),
                    StageCommandType.INSTALL);
            installStatus = installResult.exitCode();
            archiveStageLog(task.getId(), "INSTALLING", installResult);
            if (installResult.canceled()) {
                return finalizeTask(task, scene, "CANCELED", "CANCELED", "任务已取消");
            }
            if (installResult.timedOut()) {
                return finalizeTask(task, scene, "TIMEOUT", "TIMEOUT", "INSTALLING 阶段超时");
            }
            if (installStatus == 0) {
                task.setCurrentStage("TESTING");
                taskRepository.update(task);
                RunnerCommandResult testResult = runStageWithFallback(
                        task,
                        workspace,
                        executionDirectory,
                        task.getResolvedRunCommand(),
                        platformEnv,
                        Duration.ofSeconds(taskExecutionProperties.getTestTimeoutSeconds()),
                        StageCommandType.TEST);
                runStatus = testResult.exitCode();
                archiveStageLog(task.getId(), "TESTING", testResult);
                if (testResult.canceled()) {
                    return finalizeTask(task, scene, "CANCELED", "CANCELED", "任务已取消");
                }
                if (testResult.timedOut()) {
                    return finalizeTask(task, scene, "TIMEOUT", "TIMEOUT", "TESTING 阶段超时");
                }
                task.setCurrentStage("ARCHIVING");
                taskRepository.update(task);
                continuePostProcessing(task, executionDirectory, executionPaths, runStatus == 0);
            } else {
                task.setResultCode("INSTALL_FAILED");
                task.setResultMessage("安装依赖失败");
            }
        } catch (Exception exception) {
            runStatus = 1;
            appendLogMessage(task, exception.getMessage());
            if (task.getResultCode() == null || task.getResultCode().isBlank()) {
                task.setResultCode("PREPARE_FAILED");
                task.setResultMessage(exception.getMessage());
            }
        }

        task.setFinishedAt(LocalDateTime.now());
        task.setDurationMs(Duration.between(task.getStartedAt(), task.getFinishedAt()).toMillis());
        boolean success = installStatus == 0 && runStatus == 0;
        task.setStatus(success ? "SUCCESS" : "FAILED");
        task.setCurrentStage("FINISHED");
        if (success) {
            task.setResultCode("SUCCESS");
            task.setResultMessage(null);
        } else if ((task.getResultCode() == null || task.getResultCode().isBlank()) && runStatus != 0) {
            task.setResultCode("TEST_FAILED");
            task.setResultMessage("测试执行失败");
        }
        taskRepository.update(task);
        refreshSceneSummary(scene, task);
        return task;
    }

    private void continuePostProcessing(
            TaskEntity task,
            Path executionDirectory,
            ResolvedExecutionPaths executionPaths,
            boolean testsPassed) {
        List<String> errors = new ArrayList<>();
        Path resultsIndex = executionPaths.resultsIndex();
        Path artifactDirectory = executionPaths.artifactDirectory();

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
                errors.add("parse case results failed: " + exception.getMessage());
            }
        }

        if (testsPassed || Files.exists(artifactDirectory)) {
            try {
                taskArtifactArchiveService.archiveArtifacts(
                        task.getId(),
                        executionDirectory,
                        List.of(executionPaths.artifactRootRelativePath()),
                        bindingTargets);
            } catch (Exception exception) {
                errors.add("archive artifacts failed: " + exception.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            appendLogMessage(task, String.join("; ", errors));
        }
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
                resultsIndex,
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

    private RunnerCommandResult runStageWithFallback(
            TaskEntity task,
            Path workspace,
            Path executionDirectory,
            String command,
            Map<String, String> platformEnv,
            Duration timeout,
            StageCommandType stageCommandType) {
        RunnerCommandResult stageResult = runnerExecutionService.runStage(
                workspace,
                executionDirectory,
                stageCommandType.name(),
                command,
                platformEnv,
                timeout,
                () -> isCancellationRequested(task.getId()));
        if (stageResult != null) {
            return stageResult;
        }
        int exitCode = switch (stageCommandType) {
            case INSTALL -> runnerExecutionService.installDependencies(executionDirectory, command, platformEnv);
            case TEST -> runnerExecutionService.runTests(executionDirectory, command, platformEnv);
        };
        return new RunnerCommandResult(exitCode, false, false, 0L, null, 0);
    }

    private boolean isCancellationRequested(Long taskId) {
        return taskRepository.findById(taskId)
                .map(TaskEntity::getCancelRequested)
                .map(Boolean.TRUE::equals)
                .orElse(false);
    }

    private void archiveStageLog(Long taskId, String stage, RunnerCommandResult stageResult) {
        if (stageResult == null || stageResult.combinedLogFile() == null) {
            return;
        }
        try {
            taskStageLogService.archiveStageLog(taskId, stage, stageResult.combinedLogFile(), stageResult.lineCount());
        } catch (Exception exception) {
            log.warn(
                    "Failed to archive stage log, continue task execution. taskId={}, stage={}, reason={}",
                    taskId,
                    stage,
                    exception.getMessage());
        }
    }

    private TaskEntity finalizeTask(
            TaskEntity task,
            SceneEntity scene,
            String status,
            String resultCode,
            String resultMessage) {
        task.setStatus(status);
        task.setCurrentStage("FINISHED");
        task.setResultCode(resultCode);
        task.setResultMessage(resultMessage);
        task.setFinishedAt(LocalDateTime.now());
        if (task.getStartedAt() != null) {
            task.setDurationMs(Duration.between(task.getStartedAt(), task.getFinishedAt()).toMillis());
        }
        taskRepository.update(task);
        refreshSceneSummary(scene, task);
        return task;
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
        sceneMapper.update(scene);
    }

    private record ResolvedExecutionPaths(
            Path resultsIndex,
            Path artifactDirectory,
            String artifactRootRelativePath) {
    }

    private enum StageCommandType {
        INSTALL,
        TEST
    }
}
