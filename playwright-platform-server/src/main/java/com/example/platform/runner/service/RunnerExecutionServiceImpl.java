package com.example.platform.runner.service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Service;

@Service
public class RunnerExecutionServiceImpl implements RunnerExecutionService {
    private static final Duration DEFAULT_STAGE_TIMEOUT = Duration.ofHours(1);

    private final RunnerCommandExecutor commandExecutor;

    public RunnerExecutionServiceImpl(RunnerCommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    @Override
    public int installDependencies(Path workingDirectory, String installCommand, Map<String, String> extraEnv) {
        return runCommand(workingDirectory, installCommand, extraEnv);
    }

    @Override
    public int runTests(Path workingDirectory, String runCommand, Map<String, String> extraEnv) {
        return runCommand(workingDirectory, runCommand, extraEnv);
    }

    @Override
    public int generateReport(Path workingDirectory, String reportCommand, Map<String, String> extraEnv) {
        return runCommand(workingDirectory, reportCommand, extraEnv);
    }

    @Override
    public RunnerCommandResult runStage(
            Path workingDirectory,
            String command,
            Map<String, String> extraEnv,
            Duration timeout,
            BooleanSupplier cancellationRequested) {
        return commandExecutor.execute(new RunnerCommandRequest(
                workingDirectory,
                command,
                extraEnv,
                timeout,
                cancellationRequested));
    }

    private int runCommand(Path workingDirectory, String command, Map<String, String> extraEnv) {
        return runStage(workingDirectory, command, extraEnv, DEFAULT_STAGE_TIMEOUT, () -> false).exitCode();
    }
}
