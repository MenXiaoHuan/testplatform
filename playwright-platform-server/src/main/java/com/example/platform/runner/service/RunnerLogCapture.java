package com.example.platform.runner.service;

import com.example.platform.common.SensitiveDataRedactor;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 子进程输出捕获：持续排空管道，但只在达到上限前落盘。
 *
 * <p>必须始终读取进程输出，否则测试打印过多内容时子进程会阻塞在写管道。
 * 超过上限后继续读取并丢弃，只写入一行截断标记，避免把宿主机磁盘打满。
 * 每行写入前会做统一脱敏，避免 token/密码进入 MinIO 阶段日志。
 */
final class RunnerLogCapture {
    private RunnerLogCapture() {
    }

    static void capture(Process process, Path logFile, AtomicInteger lineCount, long maxBytes) {
        capture(process, logFile, lineCount, maxBytes, false);
    }

    static void capture(Process process, Path logFile, AtomicInteger lineCount, long maxBytes, boolean append) {
        long writtenBytes = 0;
        boolean truncated = false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = openWriter(logFile, append)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount.incrementAndGet();
                if (truncated) {
                    continue;
                }
                String redacted = SensitiveDataRedactor.redact(line);
                int bytes = redacted.getBytes(StandardCharsets.UTF_8).length + 1;
                if (maxBytes > 0 && writtenBytes + bytes > maxBytes) {
                    writer.write("[log truncated: exceeded " + maxBytes + " bytes]\n");
                    writer.flush();
                    truncated = true;
                    continue;
                }
                writer.write(redacted);
                writer.newLine();
                writtenBytes += bytes;
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static BufferedWriter openWriter(Path logFile, boolean append) throws IOException {
        if (append) {
            return Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        }
        return Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
