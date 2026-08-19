package com.example.platform.task.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 准备阶段日志记录器 —— 用于在任务执行的准备阶段（如 Playwright 安装、依赖检查等）
 * 实时记录日志信息到临时文件。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #create()} —— 创建临时日志文件</li>
 *   <li>{@link #write(String)} —— 按行写入带时间戳的日志</li>
 *   <li>{@link #logFile()} —— 返回日志文件路径供后续归档</li>
 *   <li>{@link #lineCount()} —— 返回已写入的日志行数</li>
 * </ul>
 */
final class PreparationStageLog {
    private final Path logFile;
    private int lineCount;

    private PreparationStageLog(Path logFile) {
        this.logFile = logFile;
    }

    /**
     * 创建一个新的准备阶段日志实例，使用临时文件存储日志内容。
     */
    static PreparationStageLog create() {
        try {
            return new PreparationStageLog(Files.createTempFile("task-preparing-", ".log"));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create preparing stage log file", exception);
        }
    }

    /**
     * 写入日志消息，自动为每一行添加时间戳前缀，跳过空行。
     */
    void write(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (String line : message.replace("\r\n", "\n").split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            lines.add(Instant.now() + " " + line);
        }
        if (lines.isEmpty()) {
            return;
        }
        try {
            Files.write(
                    logFile,
                    lines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            lineCount += lines.size();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write preparing stage log", exception);
        }
    }

    /**
     * 返回日志文件路径。
     */
    Path logFile() {
        return logFile;
    }

    /**
     * 返回已写入的日志行数。
     */
    int lineCount() {
        return lineCount;
    }
}
