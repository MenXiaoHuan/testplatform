package com.example.platform.runner.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RunnerWorkspaceServiceImpl implements RunnerWorkspaceService {
    private static final Logger log = LoggerFactory.getLogger(RunnerWorkspaceServiceImpl.class);
    private final Path workspaceRoot;

    public RunnerWorkspaceServiceImpl(@Value("${platform.runner.workspace-root}") String workspaceRoot) {
        this.workspaceRoot = Path.of(workspaceRoot);
    }

    @Override
    public Path prepareWorkspace(String gitUrl, String branch, Long taskId) {
        try {
            Files.createDirectories(workspaceRoot);
            Path taskWorkspace = workspaceRoot.resolve(String.valueOf(taskId));
            deleteWorkspace(taskWorkspace);
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

    @Override
    public void cleanupWorkspace(Long taskId) {
        if (taskId == null) {
            return;
        }
        Path taskWorkspace = workspaceRoot.resolve(String.valueOf(taskId));
        try {
            deleteWorkspace(taskWorkspace);
        } catch (IOException exception) {
            log.warn("Failed to cleanup runner workspace. taskId={}, reason={}", taskId, exception.getMessage());
        }
    }

    private void deleteWorkspace(Path taskWorkspace) throws IOException {
        if (!Files.exists(taskWorkspace)) {
            return;
        }
        try (var walk = Files.walk(taskWorkspace)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
