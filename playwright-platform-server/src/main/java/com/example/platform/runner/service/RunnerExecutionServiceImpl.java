package com.example.platform.runner.service;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

@Service
public class RunnerExecutionServiceImpl implements RunnerExecutionService {
    @Override
    public int installDependencies(Path workspace, String installCommand) {
        return runCommand(workspace, installCommand);
    }

    @Override
    public int runTests(Path workspace, String runCommand) {
        return runCommand(workspace, runCommand);
    }

    private int runCommand(Path workspace, String command) {
        try {
            Process process = new ProcessBuilder("/bin/sh", "-lc", command)
                    .directory(workspace.toFile())
                    .inheritIO()
                    .start();
            return process.waitFor();
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Failed to execute command: " + command, exception);
        }
    }
}
