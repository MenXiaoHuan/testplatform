package com.example.platform.runner.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RunnerWorkspaceServiceImpl implements RunnerWorkspaceService {
    private final Path workspaceRoot;

    public RunnerWorkspaceServiceImpl(@Value("${platform.runner.workspace-root}") String workspaceRoot) {
        this.workspaceRoot = Path.of(workspaceRoot);
    }

    @Override
    public Path prepareWorkspace(String gitUrl, String branch, Long taskId) {
        try {
            Files.createDirectories(workspaceRoot);
            Path taskWorkspace = workspaceRoot.resolve(String.valueOf(taskId));
            if (Files.exists(taskWorkspace)) {
                try (var walk = Files.walk(taskWorkspace)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                    });
                }
            }
            Process process = new ProcessBuilder("git", "clone", "--depth", "1", "--branch", branch, gitUrl, taskWorkspace.toString())
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Git clone failed with exit code " + exitCode);
            }
            return taskWorkspace;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to prepare workspace", exception);
        }
    }
}
