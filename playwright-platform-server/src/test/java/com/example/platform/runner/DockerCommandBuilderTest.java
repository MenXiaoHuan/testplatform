package com.example.platform.runner;

import com.example.platform.runner.service.DockerCommandBuilder;
import com.example.platform.runner.service.DockerRunnerProperties;
import com.example.platform.runner.service.RunnerCommandRequest;
import com.example.platform.runner.service.RunnerMode;
import com.example.platform.runner.service.RunnerProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DockerCommandBuilderTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldBuildDockerRunCommandWithWorkspaceMountAndLimits() {
        Path workspace = tempDir.resolve("101");
        Path workingDirectory = workspace.resolve("playwright_framework");
        RunnerCommandRequest request = new RunnerCommandRequest(
                workspace,
                workingDirectory,
                "INSTALL",
                "npm install",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"),
                Duration.ofSeconds(5),
                () -> false);
        RunnerProperties runnerProperties = runnerProperties(tempDir);
        DockerRunnerProperties dockerProperties = dockerProperties();

        DockerCommandBuilder builder = new DockerCommandBuilder(dockerProperties, runnerProperties);
        List<String> command = builder.buildRunCommand(request, "container-name");

        assertThat(command).containsExactly(
                "docker", "run", "--rm",
                "--name", "container-name",
                "--workdir", "/workspace/task/playwright_framework",
                "--memory", "2g",
                "--cpus", "2",
                "--network", "bridge",
                "-e", "PLAYWRIGHT_PLATFORM_MODE=true",
                "-v", tempDir.resolve("101") + ":/workspace/task:rw",
                "mcr.microsoft.com/playwright:v1.44.0-jammy",
                "/bin/sh", "-lc", "npm install");
    }

    @Test
    void shouldRejectWorkingDirectoryOutsideWorkspace() {
        Path workspace = tempDir.resolve("101");
        RunnerCommandRequest request = new RunnerCommandRequest(
                workspace,
                tempDir.resolve("other"),
                "TEST",
                "npm test",
                Map.of(),
                Duration.ofSeconds(5),
                () -> false);

        DockerCommandBuilder builder = new DockerCommandBuilder(dockerProperties(), runnerProperties(tempDir));

        assertThatThrownBy(() -> builder.buildRunCommand(request, "container-name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Working directory escapes runner workspace");
    }

    private RunnerProperties runnerProperties(Path hostWorkspaceRoot) {
        RunnerProperties properties = new RunnerProperties();
        properties.setMode(RunnerMode.DOCKER);
        properties.setWorkspaceRoot(hostWorkspaceRoot);
        properties.setHostWorkspaceRoot(hostWorkspaceRoot);
        return properties;
    }

    private DockerRunnerProperties dockerProperties() {
        DockerRunnerProperties properties = new DockerRunnerProperties();
        properties.setImage("mcr.microsoft.com/playwright:v1.44.0-jammy");
        properties.setNetwork("bridge");
        properties.setMemory("2g");
        properties.setCpus("2");
        properties.setContainerWorkspaceRoot("/workspace/task");
        properties.setRemoveContainer(true);
        return properties;
    }
}
