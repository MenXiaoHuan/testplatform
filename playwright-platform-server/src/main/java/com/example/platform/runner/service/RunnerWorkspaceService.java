package com.example.platform.runner.service;

import java.nio.file.Path;

public interface RunnerWorkspaceService {
    Path prepareWorkspace(String gitUrl, String branch, Long taskId);

    void cleanupWorkspace(Long taskId);
}
