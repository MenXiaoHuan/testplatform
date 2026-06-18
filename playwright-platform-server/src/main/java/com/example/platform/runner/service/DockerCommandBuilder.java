package com.example.platform.runner.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class DockerCommandBuilder {
    private final DockerRunnerProperties dockerProperties;
    private final RunnerProperties runnerProperties;

    public DockerCommandBuilder(DockerRunnerProperties dockerProperties, RunnerProperties runnerProperties) {
        this.dockerProperties = dockerProperties;
        this.runnerProperties = runnerProperties;
    }

    public List<String> buildRunCommand(RunnerCommandRequest request, String containerName) {
        Path workspaceRoot = request.workspaceRoot().toAbsolutePath().normalize();
        Path workingDirectory = request.workingDirectory().toAbsolutePath().normalize();
        if (!workingDirectory.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Working directory escapes runner workspace: " + request.workingDirectory());
        }

        Path hostWorkspace = resolveHostWorkspace(workspaceRoot);
        String containerWorkdir = resolveContainerWorkdir(workspaceRoot, workingDirectory);
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("run");
        if (dockerProperties.isRemoveContainer()) {
            command.add("--rm");
        }
        command.add("--name");
        command.add(containerName);
        command.add("--workdir");
        command.add(containerWorkdir);
        command.add("--memory");
        command.add(dockerProperties.getMemory());
        command.add("--cpus");
        command.add(dockerProperties.getCpus());
        command.add("--network");
        command.add(dockerProperties.getNetwork());
        request.extraEnv().entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> {
                    command.add("-e");
                    command.add(entry.getKey() + "=" + entry.getValue());
                });
        command.add("-v");
        command.add(hostWorkspace + ":" + dockerProperties.getContainerWorkspaceRoot() + ":rw");
        command.add(dockerProperties.getImage());
        command.add("/bin/sh");
        command.add("-lc");
        command.add(request.command());
        return command;
    }

    private Path resolveHostWorkspace(Path workspaceRoot) {
        Path configuredWorkspaceRoot = runnerProperties.getWorkspaceRoot().toAbsolutePath().normalize();
        Path configuredHostWorkspaceRoot = runnerProperties.getHostWorkspaceRoot().toAbsolutePath().normalize();
        if (!workspaceRoot.startsWith(configuredWorkspaceRoot)) {
            return workspaceRoot;
        }
        Path relative = configuredWorkspaceRoot.relativize(workspaceRoot);
        return configuredHostWorkspaceRoot.resolve(relative).normalize();
    }

    private String resolveContainerWorkdir(Path workspaceRoot, Path workingDirectory) {
        Path relative = workspaceRoot.relativize(workingDirectory);
        String relativeUnix = relative.toString().replace('\\', '/');
        if (relativeUnix.isBlank()) {
            return dockerProperties.getContainerWorkspaceRoot();
        }
        return dockerProperties.getContainerWorkspaceRoot() + "/" + relativeUnix;
    }
}
