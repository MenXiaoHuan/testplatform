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
 * 本地命令执行器 —— 在宿主机上直接执行任务阶段命令。
 *
 * <p>核心职责：
 * <ul>
 *   <li>通过 /bin/sh -lc 在本地 shell 中执行用户命令</li>
 *   <li>将进程输出捕获到临时日志文件</li>
 *   <li>支持协作式取消和超时强制终止</li>
 * </ul>
 *
 * <p>适用场景：本地开发和受信任的环境。
 */
public class LocalRunnerCommandExecutor implements RunnerCommandExecutor {
    /**
     * 在本地执行命令。
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
        Process process = null;
        Thread logThread = null;
        try {
            // 构建本地执行的 shell 命令
            ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-lc", request.command())
                    .directory(request.workingDirectory().toFile())
                    .redirectErrorStream(true);
            // 设置额外环境变量
            processBuilder.environment().putAll(request.extraEnv());
            process = processBuilder.start();

            Process runningProcess = process;
            // 启动日志捕获线程
            logThread = new Thread(() -> captureOutput(runningProcess, logFile, lineCount), "runner-log-capture");
            logThread.start();

            // 轮询等待进程完成，同时检查取消和超时
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                // 检查取消请求
                if (request.cancellationRequested().getAsBoolean()) {
                    process.destroyForcibly();
                    waitForLogThread(logThread);
                    return new RunnerCommandResult(-1, false, true, elapsedMs(startedAt), logFile, lineCount.get());
                }
                // 检查超时
                if (Duration.between(startedAt, Instant.now()).compareTo(request.timeout()) > 0) {
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
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to execute command: " + request.command(), exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to execute command: " + request.command(), exception);
        }
    }

    /**
     * 创建临时日志文件。
     *
     * @return 临时文件路径
     */
    private Path createTempLogFile() {
        try {
            return Files.createTempFile("runner-command-", ".log");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create temporary log file", exception);
        }
    }

    /**
     * 异步捕获进程输出并写入日志文件。
     *
     * @param process   目标进程
     * @param logFile   日志文件路径
     * @param lineCount 行数计数器
     */
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