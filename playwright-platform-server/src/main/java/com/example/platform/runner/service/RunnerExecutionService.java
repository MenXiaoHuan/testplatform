package com.example.platform.runner.service;

import java.nio.file.Path;

public interface RunnerExecutionService {
    int installDependencies(Path workspace, String installCommand);
    int runTests(Path workspace, String runCommand);
}
