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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes runner stages directly on the host machine.
 *
 * <p>This executor is intended for local development and trusted environments.
 * It streams combined process output to a temporary log file and supports
 * cooperative cancellation and timeout enforcement.
 */
public class LocalRunnerCommandExecutor implements RunnerCommandExecutor {
    @Override
    public RunnerCommandResult execute(RunnerCommandRequest request) {
        Instant startedAt = Instant.now();
        Path logFile = createTempLogFile();
        AtomicInteger lineCount = new AtomicInteger();
        Process process = null;
        Thread logThread = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-lc", request.command())
                    .directory(request.workingDirectory().toFile())
                    .redirectErrorStream(true);
            processBuilder.environment().putAll(request.extraEnv());
            process = processBuilder.start();

            Process runningProcess = process;
            logThread = new Thread(() -> captureOutput(runningProcess, logFile, lineCount), "runner-log-capture");
            logThread.start();

            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (request.cancellationRequested().getAsBoolean()) {
                    process.destroyForcibly();
                    waitForLogThread(logThread);
                    return new RunnerCommandResult(-1, false, true, elapsedMs(startedAt), logFile, lineCount.get());
                }
                if (Duration.between(startedAt, Instant.now()).compareTo(request.timeout()) > 0) {
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
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to execute command: " + request.command(), exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to execute command: " + request.command(), exception);
        }
    }

    private Path createTempLogFile() {
        try {
            return Files.createTempFile("runner-command-", ".log");
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
