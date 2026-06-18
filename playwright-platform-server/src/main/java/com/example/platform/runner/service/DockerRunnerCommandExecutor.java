package com.example.platform.runner.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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

public class DockerRunnerCommandExecutor implements RunnerCommandExecutor {
    private static final Logger log = LoggerFactory.getLogger(DockerRunnerCommandExecutor.class);
    private static final Pattern TASK_ID_PATTERN = Pattern.compile(".*/([0-9]+)$");

    private final DockerCommandBuilder commandBuilder;
    private final DockerContainerNameFactory containerNameFactory;
    private final RunnerProcessLauncher processLauncher;

    public DockerRunnerCommandExecutor(
            DockerRunnerProperties dockerProperties,
            RunnerProperties runnerProperties,
            DockerCommandBuilder commandBuilder,
            DockerContainerNameFactory containerNameFactory) {
        this(dockerProperties, runnerProperties, commandBuilder, containerNameFactory, new ProcessBuilderRunnerProcessLauncher());
    }

    DockerRunnerCommandExecutor(
            DockerRunnerProperties dockerProperties,
            RunnerProperties runnerProperties,
            DockerCommandBuilder commandBuilder,
            DockerContainerNameFactory containerNameFactory,
            RunnerProcessLauncher processLauncher) {
        this.commandBuilder = commandBuilder;
        this.containerNameFactory = containerNameFactory;
        this.processLauncher = processLauncher;
    }

    @Override
    public RunnerCommandResult execute(RunnerCommandRequest request) {
        Instant startedAt = Instant.now();
        Path logFile = createTempLogFile();
        AtomicInteger lineCount = new AtomicInteger();
        String containerName = containerNameFactory.create(resolveTaskId(request.workspaceRoot()), request.stageName());
        List<String> dockerCommand = commandBuilder.buildRunCommand(request, containerName);
        Process process = null;
        Thread logThread = null;
        try {
            process = processLauncher.start(dockerCommand, request.workspaceRoot(), Map.of());
            Process runningProcess = process;
            logThread = new Thread(() -> captureOutput(runningProcess, logFile, lineCount), "docker-runner-log-capture");
            logThread.start();

            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (request.cancellationRequested().getAsBoolean()) {
                    removeContainer(containerName);
                    process.destroyForcibly();
                    waitForLogThread(logThread);
                    return new RunnerCommandResult(-1, false, true, elapsedMs(startedAt), logFile, lineCount.get());
                }
                if (Duration.between(startedAt, Instant.now()).compareTo(request.timeout()) > 0) {
                    removeContainer(containerName);
                    process.destroyForcibly();
                    waitForLogThread(logThread);
                    return new RunnerCommandResult(-1, true, false, elapsedMs(startedAt), logFile, lineCount.get());
                }
            }

            int exitCode = process.exitValue();
            waitForLogThread(logThread);
            return new RunnerCommandResult(exitCode, false, false, elapsedMs(startedAt), logFile, lineCount.get());
        } catch (InterruptedException exception) {
            if (process != null) {
                process.destroyForcibly();
            }
            removeContainer(containerName);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to execute docker runner command: " + request.command(), exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to execute docker runner command: " + request.command(), exception);
        }
    }

    private Long resolveTaskId(Path workspaceRoot) {
        Matcher matcher = TASK_ID_PATTERN.matcher(workspaceRoot.normalize().toString().replace('\\', '/'));
        return matcher.matches() ? Long.parseLong(matcher.group(1)) : 0L;
    }

    private void removeContainer(String containerName) {
        try {
            Process cleanup = processLauncher.start(List.of("docker", "rm", "-f", containerName), Path.of("."), Map.of());
            cleanup.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.warn("Failed to remove docker runner container. containerName={}, reason={}", containerName, exception.getMessage());
        }
    }

    private Path createTempLogFile() {
        try {
            return Files.createTempFile("docker-runner-command-", ".log");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create temporary log file", exception);
        }
    }

    private void captureOutput(Process process, Path logFile, AtomicInteger lineCount) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             var writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
                lineCount.incrementAndGet();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void waitForLogThread(Thread logThread) throws InterruptedException {
        if (logThread == null) {
            return;
        }
        logThread.join(1000);
    }

    private long elapsedMs(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }
}

interface RunnerProcessLauncher {
    Process start(List<String> command, Path workingDirectory, Map<String, String> extraEnv) throws IOException;
}

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
