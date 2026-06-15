package com.example.platform.runner.service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;

public record RunnerCommandRequest(
        Path workingDirectory,
        String command,
        Map<String, String> extraEnv,
        Duration timeout,
        BooleanSupplier cancellationRequested) {
}
