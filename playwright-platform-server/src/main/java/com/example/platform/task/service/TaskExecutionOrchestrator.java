package com.example.platform.task.service;

import com.example.platform.cache.DetailCacheService;
import com.example.platform.common.ApplicationErrorSummaryService;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 任务执行编排器 —— 协调任务接受执行后的完整生命周期。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #executeTask()} —— 执行任务的完整流程（准备→安装→测试→归档）</li>
 *   <li>调用外部系统：Git、Shell/Docker 执行器、文件系统、对象存储</li>
 *   <li>各阶段日志归档和错误记录</li>
 *   <li>任务完成后的结果解析和持久化</li>
 * </ul>
 *
 * <p>依赖：{@link TaskMapper}、{@link SceneMapper}、{@link RunnerExecutionService}、
 *         {@link TaskArtifactArchiveService}、{@link TaskCaseResultParseService}、{@link TaskExecutionMutationService}
 *
 * <p>说明：此类涉及外部系统调用，不能用单个事务包裹；数据库变更委托给 {@link TaskExecutionMutationService}。
 */
final class TaskExecutionOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutionOrchestrator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TaskMapper taskRepository;
    private final SceneMapper sceneMapper;
    private final RunnerWorkspaceService runnerWorkspaceService;
    private final RunnerExecutionService runnerExecutionService;
    private final TaskArtifactArchiveService taskArtifactArchiveService;
    private final TaskCaseResultParseService taskCaseResultParseService;
    private final TaskCaseResultPersistenceService taskCaseResultPersistenceService;
    private final TaskStageLogService taskStageLogService;
    private final TaskExecutionProperties taskExecutionProperties;
    private final TaskExecutionMutationService taskExecutionMutationService;
    private final DetailCacheService detailCacheService;
    private final ApplicationErrorSummaryService applicationErrorSummaryService;

    TaskExecutionOrchestrator(
            TaskMapper taskRepository,
            SceneMapper sceneMapper,
            RunnerWorkspaceService runnerWorkspaceService,
            RunnerExecutionService runnerExecutionService,
            TaskArtifactArchiveService taskArtifactArchiveService,
            TaskCaseResultParseService taskCaseResultParseService,
            TaskCaseResultPersistenceService taskCaseResultPersistenceService,
            TaskStageLogService taskStageLogService,
            TaskExecutionProperties taskExecutionProperties,
            TaskExecutionMutationService taskExecutionMutationService,
            DetailCacheService detailCacheService,
            ApplicationErrorSummaryService applicationErrorSummaryService) {
        this.taskRepository = taskRepository;
        this.sceneMapper = sceneMapper;
        this.runnerWorkspaceService = runnerWorkspaceService;
        this.runnerExecutionService = runnerExecutionService;
        this.taskArtifactArchiveService = taskArtifactArchiveService;
        this.taskCaseResultParseService = taskCaseResultParseService;
        this.taskCaseResultPersistenceService = taskCaseResultPersistenceService;
        this.taskStageLogService = taskStageLogService;
        this.taskExecutionProperties = taskExecutionProperties;
        this.taskExecutionMutationService = taskExecutionMutationService;
        this.detailCacheService = detailCacheService;
        this.applicationErrorSummaryService = applicationErrorSummaryService;
    }

/**
     * 执行任务的完整流程：准备工作区 → 安装依赖 → 运行测试 → 归档结果
     */
      TaskEntity executeTask(TaskEntity task, TestRepositoryEntity repository, SceneEntity scene) {
          int installStatus = 1;
          int runStatus = 1;
          Path workspace = null;
          PreparationStageLog preparationStageLog = PreparationStageLog.create();
          StageExecutionContext currentStage = null;
          try {
              try (TaskLogContext taskLogContext = TaskLogContext.open(task, scene)) {
            if (task.getStartedAt() == null) {
                task.setStartedAt(LocalDateTime.now());
            }
            task.setStatus("RUNNING");
            task.setCurrentStage("PREPARING");
            taskLogContext.setStage("PREPARING");
            log.info("Task execution started");
            preparationStageLog.write("Task accepted for execution");
            preparationStageLog.write("Preparing workspace. branch=" + task.getResolvedBranch());
            taskExecutionMutationService.saveTask(task);

            currentStage = StageExecutionContext.start("PREPARING");
            workspace = runnerWorkspaceService.prepareWorkspace(
                    repository.getGitUrl(),
                    task.getResolvedBranch(),
                    task.getId());
            preparationStageLog.write("Workspace prepared at " + workspace);
            Path executionDirectory = resolveExecutionDirectory(workspace, repository);
            preparationStageLog.write("Execution directory resolved to " + executionDirectory);
            Map<String, String> platformEnv = platformEnv(task);
            ResolvedExecutionPaths executionPaths = resolveExecutionPaths(executionDirectory, repository);
            preparationStageLog.write("Results index path: " + executionPaths.resultsIndex());
            preparationStageLog.write("Artifact root path: " + executionPaths.artifactDirectory());
            archiveStageLog(task.getId(), "PREPARING", preparationStageLog.logFile(),
                    preparationStageLog.lineCount(), currentStage,
                    "Workspace preparation: clone=" + task.getResolvedBranch() + ", dir=" + executionDirectory,
                    null, null, "SUCCESS");

            task.setCurrentStage("INSTALLING");
            taskLogContext.setStage("INSTALLING");
            log.info("Preparation completed, entering install stage");
            taskExecutionMutationService.saveTask(task);

            String installCommand = repository.getInstallCommand();
            currentStage = StageExecutionContext.start("INSTALLING");
            RunnerCommandResult installResult = runStageWithFallback(
                    task,
                    workspace,
                    executionDirectory,
                    installCommand,
                    platformEnv,
                    Duration.ofSeconds(taskExecutionProperties.getInstallTimeoutSeconds()),
                    StageCommandType.INSTALL);
            installStatus = installResult.exitCode();
            String installError = null;
            String installStatusStr = "SUCCESS";
            if (installResult.canceled()) {
                installStatusStr = "CANCELED";
                installError = "任务已取消";
            } else if (installResult.timedOut()) {
                installStatusStr = "TIMEOUT";
                installError = "INSTALLING 阶段超时";
            } else if (installStatus != 0) {
                installStatusStr = "FAILED";
                installError = "安装依赖失败，退出码=" + installStatus;
            }
            archiveStageLog(task.getId(), "INSTALLING", installResult, currentStage,
                    installCommand, installError, installStatusStr);

            if (installResult.canceled()) {
                return finalizeTask(task, scene, "CANCELED", "CANCELED", "任务已取消");
            }
            if (installResult.timedOut()) {
                recordApplicationError(task, scene, "INSTALLING", "INSTALLING 阶段超时", null);
                return finalizeTask(task, scene, "TIMEOUT", "TIMEOUT", "INSTALLING 阶段超时");
            }
            if (installStatus == 0) {
                task.setCurrentStage("TESTING");
                taskLogContext.setStage("TESTING");
                log.info("Install stage completed successfully, entering test stage");
                taskExecutionMutationService.saveTask(task);

                String testCommand = task.getResolvedRunCommand();
                currentStage = StageExecutionContext.start("TESTING");
                RunnerCommandResult testResult = runStageWithFallback(
                        task,
                        workspace,
                        executionDirectory,
                        testCommand,
                        platformEnv,
                        Duration.ofSeconds(taskExecutionProperties.getTestTimeoutSeconds()),
                        StageCommandType.TEST);
                runStatus = testResult.exitCode();
                String testError = null;
                String testStatusStr = "SUCCESS";
                if (testResult.canceled()) {
                    testStatusStr = "CANCELED";
                    testError = "任务已取消";
                } else if (testResult.timedOut()) {
                    testStatusStr = "TIMEOUT";
                    testError = "TESTING 阶段超时";
                } else if (runStatus != 0) {
                    testStatusStr = "FAILED";
                    testError = "测试执行失败，退出码=" + runStatus;
                }
                archiveStageLog(task.getId(), "TESTING", testResult, currentStage,
                        testCommand, testError, testStatusStr);

                if (testResult.canceled()) {
                    return finalizeTask(task, scene, "CANCELED", "CANCELED", "任务已取消");
                }
                if (testResult.timedOut()) {
                    recordApplicationError(task, scene, "TESTING", "TESTING 阶段超时", null);
                    return finalizeTask(task, scene, "TIMEOUT", "TIMEOUT", "TESTING 阶段超时");
                }
                task.setCurrentStage("ARCHIVING");
                taskLogContext.setStage("ARCHIVING");
                log.info("Test stage completed, entering archive stage");
                taskExecutionMutationService.saveTask(task);

                StageExecutionContext archiveStage = StageExecutionContext.start("ARCHIVING");
                try {
                    continuePostProcessing(task, executionDirectory, executionPaths, runStatus == 0);
                    archiveStageLog(task.getId(), "ARCHIVING", null, 0, archiveStage,
                            "Artifact archive and case result parsing", null, null, "SUCCESS");
                } catch (Exception ex) {
                    archiveStageLog(task.getId(), "ARCHIVING", null, 0, archiveStage,
                            null, null, ex.getMessage(), "FAILED");
                    throw ex;
                }
            } else {
                task.setResultCode("INSTALL_FAILED");
                task.setResultMessage("安装依赖失败");
            }
              } catch (Exception exception) {
                  runStatus = 1;
                  if ("PREPARING".equalsIgnoreCase(task.getCurrentStage())) {
                      preparationStageLog.write("Preparing stage failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
                      archiveStageLog(task.getId(), "PREPARING", preparationStageLog.logFile(),
                              preparationStageLog.lineCount(), currentStage,
                              exception.getMessage(), "FAILED");
                  }
                  log.error("Task execution failed", exception);
                  if (applicationErrorSummaryService != null) {
                      applicationErrorSummaryService.recordError(log.getName(), exception.getMessage(), exception);
                  }
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
              log.info("Task execution finished. status={}, resultCode={}, durationMs={}", task.getStatus(), task.getResultCode(), task.getDurationMs());
              taskExecutionMutationService.saveTask(task);
              taskExecutionMutationService.refreshSceneSummary(scene, task);
              return task;
          } finally {
              if (workspace != null) {
                  runnerWorkspaceService.cleanupWorkspace(task.getId());
              }
          }
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
                recordApplicationError(task, null, "ARCHIVING", "parse case results failed: " + exception.getMessage(), exception);
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
                recordApplicationError(task, null, "ARCHIVING", "archive artifacts failed: " + exception.getMessage(), exception);
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

    private Map<String, String> platformEnv(TaskEntity task) {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("PLAYWRIGHT_PLATFORM_MODE", "true");
        String resolvedEnvJson = task.getResolvedEnvJson();
        if (resolvedEnvJson == null || resolvedEnvJson.isBlank()) {
            return Map.copyOf(environment);
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(resolvedEnvJson);
            if (!root.isObject()) {
                throw new IllegalArgumentException("Task environment JSON must be a JSON object");
            }
            root.fields().forEachRemaining(entry -> environment.put(entry.getKey(), stringifyEnvValue(entry.getValue())));
            return Map.copyOf(environment);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse task environment JSON", exception);
        }
    }

    private String stringifyEnvValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        return value.isTextual() ? value.asText() : value.toString();
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

    private void archiveStageLog(Long taskId, String stage, RunnerCommandResult stageResult,
                                StageExecutionContext context, String command, String errorMessage, String stageStatus) {
        if (stageResult == null || stageResult.combinedLogFile() == null) {
            archiveStageLog(taskId, stage, null, 0, context, errorMessage, stageStatus);
            return;
        }
        archiveStageLog(taskId, stage, stageResult.combinedLogFile(), stageResult.lineCount(),
                context, command, stageResult.exitCode(), errorMessage, stageStatus);
    }

    private void archiveStageLog(Long taskId, String stage, Path logFile, int lineCount,
                                StageExecutionContext context, String errorMessage, String stageStatus) {
        archiveStageLog(taskId, stage, logFile, lineCount, context, null, null, errorMessage, stageStatus);
    }

    private void archiveStageLog(Long taskId, String stage, Path logFile, int lineCount,
                                StageExecutionContext context, String command, Integer exitCode,
                                String errorMessage, String stageStatus) {
        if (logFile == null) {
            return;
        }
        try {
            LocalDateTime startedAt = context != null ? context.startedAt() : null;
            LocalDateTime endedAt = LocalDateTime.now();
            long durationMs = context != null ? context.durationMs(endedAt) : 0L;
            taskStageLogService.archiveStageLog(
                    taskId, stage, logFile, lineCount,
                    durationMs, exitCode, stageStatus, command,
                    startedAt, endedAt, errorMessage);
        } catch (Exception exception) {
            recordApplicationError(taskRepository.findById(taskId).orElse(null), null, stage, "Failed to archive stage log: " + exception.getMessage(), exception);
            log.warn(
                    "Failed to archive stage log, continue task execution. taskId={}, stage={}, reason={}",
                    taskId,
                    stage,
                    exception.getMessage());
        }
    }

    private void archiveStageLog(Long taskId, String stage, Path logFile, int lineCount) {
        archiveStageLog(taskId, stage, logFile, lineCount, null, null, null, null, null);
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
        taskExecutionMutationService.saveTask(task);
        taskExecutionMutationService.refreshSceneSummary(scene, task);
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

    private void recordApplicationError(
            TaskEntity task,
            SceneEntity scene,
            String stage,
            String message,
            Throwable throwable) {
        if (applicationErrorSummaryService == null || (message == null || message.isBlank()) && throwable == null) {
            return;
        }
        try (TaskLogContext taskLogContext = TaskLogContext.open(task, scene)) {
            taskLogContext.setStage(stage);
            applicationErrorSummaryService.recordError(log.getName(), message, throwable);
        }
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

    private record ResolvedExecutionPaths(
            Path resultsIndex,
            Path artifactDirectory,
            String artifactRootRelativePath) {
    }

    private static final class StageExecutionContext {
        private final String stage;
        private final LocalDateTime startedAt;

        private StageExecutionContext(String stage, LocalDateTime startedAt) {
            this.stage = stage;
            this.startedAt = startedAt;
        }

        static StageExecutionContext start(String stage) {
            return new StageExecutionContext(stage, LocalDateTime.now());
        }

        String stage() {
            return stage;
        }

        LocalDateTime startedAt() {
            return startedAt;
        }

        long durationMs(LocalDateTime endedAt) {
            return Duration.between(startedAt, endedAt).toMillis();
        }
    }

    private enum StageCommandType {
        INSTALL,
        TEST
    }
}
