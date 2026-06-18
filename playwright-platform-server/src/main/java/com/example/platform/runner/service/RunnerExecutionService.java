package com.example.platform.runner.service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;

public interface RunnerExecutionService {
    int installDependencies(Path workingDirectory, String installCommand, Map<String, String> extraEnv);
    int runTests(Path workingDirectory, String runCommand, Map<String, String> extraEnv);
    int generateReport(Path workingDirectory, String reportCommand, Map<String, String> extraEnv);
    RunnerCommandResult runStage(
            Path workspaceRoot,
            Path workingDirectory,
            String stageName,
            String command,
            Map<String, String> extraEnv,
            Duration timeout,
            BooleanSupplier cancellationRequested);
}
