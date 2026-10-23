package com.example.platform.runner.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 启动期 Docker Runner 预检。
 *
 * <p>平台只使用 Docker Runner；server 进程本身不执行用户命令。启动时提前检查：
 * <ul>
 *   <li>{@code docker} CLI 是否存在，且能连上 daemon；</li>
 *   <li>runner workspace 是否可写（容器 UID/GID 或 init 服务是否正确）；</li>
 * </ul>
 *
 * <p>这样部署错误会在启动阶段暴露，而不是等第一个任务跑到一半才失败。
 * 测试 profile 跳过，避免单元测试依赖本机 Docker。
 */
@Component
@Profile("!test")
public class RunnerSafetyValidator {
    private static final Logger log = LoggerFactory.getLogger(RunnerSafetyValidator.class);

    private final RunnerProperties runnerProperties;

    public RunnerSafetyValidator(RunnerProperties runnerProperties) {
        this.runnerProperties = runnerProperties;
    }

    @PostConstruct
    public void validate() {
        validateDockerAvailable();
        validateWorkspaceWritable();
    }

    private void validateDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("docker version timed out; check the Docker daemon and /var/run/docker.sock");
            }
            String output;
            try (var stream = process.getInputStream()) {
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "Docker runner preflight failed: `docker version` exited with "
                                + process.exitValue() + ": " + output
                                + ". Check that the docker CLI is installed in the server image, "
                                + "that /var/run/docker.sock is mounted, and that the container user is in the socket group.");
            }
            log.info("Docker runner preflight passed. serverVersion={}", output);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Docker runner requires the `docker` CLI in PATH. "
                            + "Install the Docker CLI in the server image and mount /var/run/docker.sock.",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while checking Docker availability", exception);
        }
    }

    private void validateWorkspaceWritable() {
        Path workspaceRoot = runnerProperties.getWorkspaceRoot();
        if (workspaceRoot == null) {
            return;
        }
        try {
            Files.createDirectories(workspaceRoot);
            if (!Files.isWritable(workspaceRoot)) {
                throw new IllegalStateException(
                        "Runner workspace is not writable: " + workspaceRoot
                                + ". Pre-create it with the server container UID/GID or use the compose init service.");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare runner workspace: " + workspaceRoot, exception);
        }
    }
}
