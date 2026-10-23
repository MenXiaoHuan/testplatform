package com.example.platform.runner.service;

import com.example.platform.common.SensitiveDataRedactor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Docker 命令执行器 —— 在短生命周期的 Docker 容器中执行任务阶段。
 *
 * <p>核心职责：
 * <ul>
 *   <li>在执行前检查并拉取所需的 Docker 镜像</li>
 *   <li>启动 Docker 容器并在其中执行用户命令</li>
 *   <li>捕获容器输出到临时日志文件</li>
 *   <li>支持协作式取消和超时强制终止</li>
 *   <li>超时或取消后强制删除容器</li>
 * </ul>
 *
 * <p>依赖：{@link DockerCommandBuilder}、{@link DockerContainerNameFactory}、
 *         {@link DockerRunnerProperties}、{@link RunnerProperties}
 */
public class DockerRunnerCommandExecutor implements RunnerCommandExecutor {
    private static final Logger log = LoggerFactory.getLogger(DockerRunnerCommandExecutor.class);
    // 从工作空间路径中提取任务 ID 的正则表达式
    private static final Pattern TASK_ID_PATTERN = Pattern.compile(".*/([0-9]+)$");

    private final DockerRunnerProperties dockerProperties;
    private final DockerCommandBuilder commandBuilder;
    private final DockerContainerNameFactory containerNameFactory;
    private final RunnerProcessLauncher processLauncher;
    private final long maxLogBytes;
    private final long maxTestLogBytes;

    public DockerRunnerCommandExecutor(
            DockerRunnerProperties dockerProperties,
            RunnerProperties runnerProperties,
            DockerCommandBuilder commandBuilder,
            DockerContainerNameFactory containerNameFactory) {
        this(dockerProperties, runnerProperties, commandBuilder, containerNameFactory,
                new ProcessBuilderRunnerProcessLauncher(), 50L * 1024 * 1024, 200L * 1024 * 1024);
    }

    public DockerRunnerCommandExecutor(
            DockerRunnerProperties dockerProperties,
            RunnerProperties runnerProperties,
            DockerCommandBuilder commandBuilder,
            DockerContainerNameFactory containerNameFactory,
            long maxLogBytes) {
        this(dockerProperties, runnerProperties, commandBuilder, containerNameFactory,
                new ProcessBuilderRunnerProcessLauncher(), maxLogBytes, 200L * 1024 * 1024);
    }

    public DockerRunnerCommandExecutor(
            DockerRunnerProperties dockerProperties,
            RunnerProperties runnerProperties,
            DockerCommandBuilder commandBuilder,
            DockerContainerNameFactory containerNameFactory,
            long maxLogBytes,
            long maxTestLogBytes) {
        this(dockerProperties, runnerProperties, commandBuilder, containerNameFactory,
                new ProcessBuilderRunnerProcessLauncher(), maxLogBytes, maxTestLogBytes);
    }

    DockerRunnerCommandExecutor(
            DockerRunnerProperties dockerProperties,
            RunnerProperties runnerProperties,
            DockerCommandBuilder commandBuilder,
            DockerContainerNameFactory containerNameFactory,
            RunnerProcessLauncher processLauncher) {
        this(dockerProperties, runnerProperties, commandBuilder, containerNameFactory,
                processLauncher, 50L * 1024 * 1024, 200L * 1024 * 1024);
    }

    DockerRunnerCommandExecutor(
            DockerRunnerProperties dockerProperties,
            RunnerProperties runnerProperties,
            DockerCommandBuilder commandBuilder,
            DockerContainerNameFactory containerNameFactory,
            RunnerProcessLauncher processLauncher,
            long maxLogBytes) {
        this(dockerProperties, runnerProperties, commandBuilder, containerNameFactory,
                processLauncher, maxLogBytes, 200L * 1024 * 1024);
    }

    DockerRunnerCommandExecutor(
            DockerRunnerProperties dockerProperties,
            RunnerProperties runnerProperties,
            DockerCommandBuilder commandBuilder,
            DockerContainerNameFactory containerNameFactory,
            RunnerProcessLauncher processLauncher,
            long maxLogBytes,
            long maxTestLogBytes) {
        this.dockerProperties = dockerProperties;
        this.commandBuilder = commandBuilder;
        this.containerNameFactory = containerNameFactory;
        this.processLauncher = processLauncher;
        this.maxLogBytes = maxLogBytes;
        this.maxTestLogBytes = maxTestLogBytes;
    }

    /**
     * 执行 Docker 容器命令。
     *
     * @param request 执行请求
     * @return 执行结果，包含退出码、超时/取消状态、日志文件路径等
     */
    @Override
    public RunnerCommandResult execute(RunnerCommandRequest request) {
        Instant startedAt = Instant.now();
        // 创建临时日志文件
        Path logFile = createTempLogFile();
        AtomicInteger lineCount = new AtomicInteger();
        // 从工作空间路径解析任务 ID，生成容器名称
        String containerName = containerNameFactory.create(resolveTaskId(request.workspaceRoot()), request.stageName());
        List<String> dockerCommand = commandBuilder.buildRunCommand(request, containerName);
        Process process = null;
        Thread logThread = null;
        try {
            // 确保 Docker 镜像可用（不存在则拉取）
            RunnerCommandResult imagePreparationResult = ensureImageAvailable(request, logFile, lineCount);
            if (imagePreparationResult != null) {
                return imagePreparationResult;
            }
            // 启动 Docker 进程
            process = processLauncher.start(dockerCommand, request.workspaceRoot(), Map.of());
            Process runningProcess = process;
            boolean testStage = "TEST".equalsIgnoreCase(request.stageName())
                    || "TESTING".equalsIgnoreCase(request.stageName());
            long stageLogLimit = testStage ? maxTestLogBytes : maxLogBytes;
            // 镜像不存在时 pull 日志已经写入同一个文件；追加而不是覆盖它。
            logThread = new Thread(() -> RunnerLogCapture.capture(
                    runningProcess, logFile, lineCount, stageLogLimit, true),
                    "docker-runner-log-capture");
            logThread.start();

            // 轮询等待进程完成，同时检查取消和超时
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                // 检查取消请求
                if (request.cancellationRequested().getAsBoolean()) {
                    removeContainer(containerName);
                    process.destroyForcibly();
                    waitForLogThread(logThread);
                    return new RunnerCommandResult(-1, false, true, elapsedMs(startedAt), logFile, lineCount.get());
                }
                // 检查超时
                if (Duration.between(startedAt, Instant.now()).compareTo(request.timeout()) > 0) {
                    removeContainer(containerName);
                    process.destroyForcibly();
                    waitForLogThread(logThread);
                    return new RunnerCommandResult(-1, true, false, elapsedMs(startedAt), logFile, lineCount.get());
                }
            }

            // 进程正常退出
            int exitCode = process.exitValue();
            waitForLogThread(logThread);
            return new RunnerCommandResult(exitCode, false, false, elapsedMs(startedAt), logFile, lineCount.get());
        } catch (InterruptedException exception) {
            // 中断时清理资源
            if (process != null) {
                process.destroyForcibly();
            }
            removeContainer(containerName);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to execute docker runner command: "
                    + SensitiveDataRedactor.redact(request.command()), exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to execute docker runner command: "
                    + SensitiveDataRedactor.redact(request.command()), exception);
        }
    }

    /**
     * 确保 Docker 镜像可用。若本地不存在则自动拉取。
     *
     * @param request  执行请求（用于传递超时和取消信号）
     * @param logFile  日志文件路径
     * @param lineCount 日志行数计数器
     * @return 准备结果，null 表示镜像已就绪
     */
    private RunnerCommandResult ensureImageAvailable(
            RunnerCommandRequest request,
            Path logFile,
            AtomicInteger lineCount) throws IOException, InterruptedException {
        // 先检查镜像是否存在
        List<String> inspectCommand = List.of("docker", "image", "inspect", dockerProperties.getImage());
        Process inspectProcess = processLauncher.start(inspectCommand, request.workspaceRoot(), Map.of());
        int inspectExitCode = inspectProcess.waitFor();
        if (inspectExitCode == 0) {
            return null;
        }

        // 镜像不存在，执行拉取
        Instant startedAt = Instant.now();
        Process pullProcess = processLauncher.start(
                List.of("docker", "pull", dockerProperties.getImage()),
                request.workspaceRoot(),
                Map.of());
        Thread logThread = new Thread(() -> RunnerLogCapture.capture(pullProcess, logFile, lineCount, maxLogBytes),
                "docker-image-pull-log-capture");
        logThread.start();

        Duration pullTimeout = Duration.ofSeconds(Math.max(0, dockerProperties.getImagePullTimeoutSeconds()));
        // 轮询等待拉取完成
        while (!pullProcess.waitFor(100, TimeUnit.MILLISECONDS)) {
            if (request.cancellationRequested().getAsBoolean()) {
                pullProcess.destroyForcibly();
                waitForLogThread(logThread);
                return new RunnerCommandResult(-1, false, true, elapsedMs(startedAt), logFile, lineCount.get());
            }
            if (Duration.between(startedAt, Instant.now()).compareTo(pullTimeout) > 0) {
                pullProcess.destroyForcibly();
                waitForLogThread(logThread);
                return new RunnerCommandResult(-1, true, false, elapsedMs(startedAt), logFile, lineCount.get());
            }
        }

        int pullExitCode = pullProcess.exitValue();
        waitForLogThread(logThread);
        if (pullExitCode != 0) {
            // 保留 pull 日志，并把它作为 INSTALLING 阶段失败结果返回；
            // 这样阶段日志里能看到镜像拉取失败的原因，而不是只有一条任务错误。
            String message = "[image pull failed] exitCode=" + pullExitCode
                    + ", image=" + dockerProperties.getImage();
            Files.writeString(logFile, message + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);
            lineCount.incrementAndGet();
            return new RunnerCommandResult(-1, false, false, elapsedMs(startedAt), logFile, lineCount.get());
        }
        return null;
    }

    /**
     * 从工作空间路径中解析任务 ID。
     *
     * @param workspaceRoot 工作空间根路径
     * @return 任务 ID，解析失败返回 0
     */
    private Long resolveTaskId(Path workspaceRoot) {
        Matcher matcher = TASK_ID_PATTERN.matcher(workspaceRoot.normalize().toString().replace('\\', '/'));
        return matcher.matches() ? Long.parseLong(matcher.group(1)) : 0L;
    }

    /**
     * 强制移除 Docker 容器，忽略移除失败的异常。
     *
     * @param containerName 容器名称
     */
    private void removeContainer(String containerName) {
        try {
            Process cleanup = processLauncher.start(List.of("docker", "rm", "-f", containerName), Path.of("."), Map.of());
            cleanup.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.warn("Failed to remove docker runner container. containerName={}, reason={}", containerName, exception.getMessage());
        }
    }

    /**
     * 创建临时日志文件。
     *
     * @return 临时文件路径
     */
    private Path createTempLogFile() {
        try {
            return Files.createTempFile("docker-runner-command-", ".log");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create temporary log file", exception);
        }
    }

    /**
     * 等待日志线程结束，最多等待 1 秒。
     *
     * @param logThread 日志捕获线程
     */
    private void waitForLogThread(Thread logThread) throws InterruptedException {
        if (logThread == null) {
            return;
        }
        logThread.join(1000);
    }

    /**
     * 计算从开始时间到现在的耗时（毫秒）。
     *
     * @param startedAt 开始时间
     * @return 耗时毫秒数
     */
    private long elapsedMs(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }
}

/**
 * 进程启动器接口 —— 抽象进程启动逻辑，便于测试。
 */
interface RunnerProcessLauncher {
    /**
     * 启动外部进程。
     *
     * @param command         命令及参数
     * @param workingDirectory 工作目录
     * @param extraEnv        额外环境变量
     * @return 启动的进程对象
     */
    Process start(List<String> command, Path workingDirectory, Map<String, String> extraEnv) throws IOException;
}

/**
 * 基于 ProcessBuilder 的进程启动器实现。
 */
final class ProcessBuilderRunnerProcessLauncher implements RunnerProcessLauncher {
    @Override
    public Process start(List<String> command, Path workingDirectory, Map<String, String> extraEnv) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true);
        builder.environment().putAll(extraEnv);
        return builder.start();
    }
}