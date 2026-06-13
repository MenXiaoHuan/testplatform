package com.example.platform.runner.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RunnerExecutionServiceImpl implements RunnerExecutionService {
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

    private int runCommand(Path workingDirectory, String command, Map<String, String> extraEnv) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-lc", command)
                    .directory(workingDirectory.toFile())
                    .inheritIO();
            processBuilder.environment().putAll(extraEnv);
            Process process = processBuilder.start();
            return process.waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to execute command: " + command, exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to execute command: " + command, exception);
        }
    }
}
