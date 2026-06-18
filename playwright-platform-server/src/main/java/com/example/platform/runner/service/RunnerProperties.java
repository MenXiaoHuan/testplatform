package com.example.platform.runner.service;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.runner")
public class RunnerProperties {
    private RunnerMode mode = RunnerMode.LOCAL;
    private Path workspaceRoot;
    private Path hostWorkspaceRoot;

    public RunnerMode getMode() {
        return mode;
    }

    public void setMode(RunnerMode mode) {
        this.mode = mode;
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public Path getHostWorkspaceRoot() {
        return hostWorkspaceRoot;
    }

    public void setHostWorkspaceRoot(Path hostWorkspaceRoot) {
        this.hostWorkspaceRoot = hostWorkspaceRoot;
    }
}
