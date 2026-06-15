package com.example.platform.runner.service;

import java.nio.file.Path;

public record RunnerCommandResult(
        int exitCode,
        boolean timedOut,
        boolean canceled,
        long durationMs,
        Path combinedLogFile,
        int lineCount) {
}
