package com.example.platform.runner.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class DockerRunnerCommandExecutorTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldReturnSuccessfulResultWhenDockerProcessExitsZero() throws IOException {
        FakeRunnerProcessLauncher launcher = new FakeRunnerProcessLauncher(new CompletedProcess(0, "docker output\n"));
        DockerRunnerCommandExecutor executor = executor(launcher);
        RunnerCommandRequest request = request(false, Duration.ofSeconds(5));

        RunnerCommandResult result = executor.execute(request);

        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.timedOut()).isFalse();
        assertThat(result.canceled()).isFalse();
        assertThat(result.lineCount()).isEqualTo(1);
        assertThat(Files.readString(result.combinedLogFile())).contains("docker output");
        assertThat(launcher.startedCommands().getFirst()).contains("docker", "run", "--name", "container-name");
    }

    @Test
    void shouldRemoveContainerWhenCancellationIsRequested() {
        FakeRunnerProcessLauncher launcher = new FakeRunnerProcessLauncher(new RunningProcess());
        AtomicBoolean canceled = new AtomicBoolean(false);
        DockerRunnerCommandExecutor executor = executor(launcher);
        RunnerCommandRequest request = request(canceled::get, Duration.ofSeconds(5));
        canceled.set(true);

        RunnerCommandResult result = executor.execute(request);

        assertThat(result.canceled()).isTrue();
        assertThat(result.timedOut()).isFalse();
        assertThat(launcher.startedCommands()).anySatisfy(command ->
                assertThat(command).containsExactly("docker", "rm", "-f", "container-name"));
    }

    @Test
    void shouldRemoveContainerWhenCommandTimesOut() {
        FakeRunnerProcessLauncher launcher = new FakeRunnerProcessLauncher(new RunningProcess());
        DockerRunnerCommandExecutor executor = executor(launcher);
        RunnerCommandRequest request = request(false, Duration.ZERO);

        RunnerCommandResult result = executor.execute(request);

        assertThat(result.timedOut()).isTrue();
        assertThat(result.canceled()).isFalse();
        assertThat(launcher.startedCommands()).anySatisfy(command ->
                assertThat(command).containsExactly("docker", "rm", "-f", "container-name"));
    }

    @Test
    void shouldCreateDockerRunnerCommandExecutorFromConfiguration() {
        RunnerProperties runnerProperties = runnerProperties();
        runnerProperties.setMode(RunnerMode.DOCKER);
        RunnerCommandExecutorConfig config = new RunnerCommandExecutorConfig();

        RunnerCommandExecutor executor = config.runnerCommandExecutor(runnerProperties, dockerProperties());

        assertThat(executor).isInstanceOf(DockerRunnerCommandExecutor.class);
    }

    private DockerRunnerCommandExecutor executor(FakeRunnerProcessLauncher launcher) {
        RunnerProperties runnerProperties = runnerProperties();
        DockerRunnerProperties dockerProperties = dockerProperties();
        return new DockerRunnerCommandExecutor(
                dockerProperties,
                runnerProperties,
                new DockerCommandBuilder(dockerProperties, runnerProperties),
                new FixedDockerContainerNameFactory(),
                launcher);
    }

    private RunnerCommandRequest request(boolean canceled, Duration timeout) {
        return request(() -> canceled, timeout);
    }

    private RunnerCommandRequest request(java.util.function.BooleanSupplier canceled, Duration timeout) {
        Path workspace = tempDir.resolve("101");
        return new RunnerCommandRequest(
                workspace,
                workspace.resolve("project"),
                "TEST",
                "npm test",
                Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"),
                timeout,
                canceled);
    }

    private RunnerProperties runnerProperties() {
        RunnerProperties properties = new RunnerProperties();
        properties.setMode(RunnerMode.DOCKER);
        properties.setWorkspaceRoot(tempDir);
        properties.setHostWorkspaceRoot(tempDir);
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

    private static final class FixedDockerContainerNameFactory extends DockerContainerNameFactory {
        @Override
        public String create(Long taskId, String stageName) {
            return "container-name";
        }
    }

    private static final class FakeRunnerProcessLauncher implements RunnerProcessLauncher {
        private final Process dockerRunProcess;
        private final List<List<String>> startedCommands = new ArrayList<>();

        private FakeRunnerProcessLauncher(Process dockerRunProcess) {
            this.dockerRunProcess = dockerRunProcess;
        }

        @Override
        public Process start(List<String> command, Path workingDirectory, Map<String, String> extraEnv) {
            startedCommands.add(List.copyOf(command));
            if (command.equals(List.of("docker", "rm", "-f", "container-name"))) {
                return new CompletedProcess(0, "");
            }
            return dockerRunProcess;
        }

        private List<List<String>> startedCommands() {
            return startedCommands;
        }
    }

    private static class CompletedProcess extends Process {
        private final int exitCode;
        private final InputStream inputStream;

        private CompletedProcess(int exitCode, String output) {
            this.exitCode = exitCode;
            this.inputStream = new ByteArrayInputStream(output.getBytes());
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return inputStream;
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            return true;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
        }
    }

    private static final class RunningProcess extends CompletedProcess {
        private boolean destroyed;

        private RunningProcess() {
            super(-1, "");
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            Thread.sleep(Math.min(unit.toMillis(timeout), 10));
            return destroyed;
        }

        @Override
        public int exitValue() {
            if (!destroyed) {
                throw new IllegalThreadStateException("Process is still running");
            }
            return -1;
        }

        @Override
        public Process destroyForcibly() {
            destroyed = true;
            return this;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }
}
