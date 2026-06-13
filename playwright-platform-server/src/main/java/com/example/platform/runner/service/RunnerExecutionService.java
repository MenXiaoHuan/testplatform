package com.example.platform.runner.service;

import java.nio.file.Path;
import java.util.Map;

public interface RunnerExecutionService {
    int installDependencies(Path workingDirectory, String installCommand, Map<String, String> extraEnv);
    int runTests(Path workingDirectory, String runCommand, Map<String, String> extraEnv);
    int generateReport(Path workingDirectory, String reportCommand, Map<String, String> extraEnv);
}
