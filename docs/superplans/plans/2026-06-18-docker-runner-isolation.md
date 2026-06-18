# Docker Runner Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Docker-based Runner execution mode so install/test stages can run in short-lived containers while the existing task orchestration, logs, result parsing, and artifact archiving stay intact.

**Architecture:** Keep `RunnerExecutionServiceImpl` dependent on the `RunnerCommandExecutor` interface and select either local or Docker execution by configuration. Docker execution builds `docker run` argument lists, mounts only the current task workspace, streams combined logs through the existing log path, and removes the container on cancel/timeout.

**Tech Stack:** Spring Boot 3.5, Java 21, Maven/JUnit/Mockito, Docker CLI, Docker Compose, Flyway/MySQL/MinIO existing platform stack.

---

## File Structure

- Modify: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerCommandRequest.java`
  - Add `workspaceRoot` and `stageName` fields so Docker execution can validate and mount the task workspace.
- Rename by create/delete or direct file move: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerCommandExecutorImpl.java`
  - Replace with `LocalRunnerCommandExecutor.java`, preserving current local `ProcessBuilder` behavior.
- Create: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerMode.java`
  - Enum for `LOCAL` and `DOCKER`.
- Create: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerProperties.java`
  - Binds `platform.runner.mode`, `workspace-root`, and `host-workspace-root`.
- Create: `playwright-platform-server/src/main/java/com/example/platform/runner/service/DockerRunnerProperties.java`
  - Binds `platform.runner.docker.*`.
- Create: `playwright-platform-server/src/main/java/com/example/platform/runner/service/DockerContainerNameFactory.java`
  - Generates deterministic-safe, unique container names.
- Create: `playwright-platform-server/src/main/java/com/example/platform/runner/service/DockerCommandBuilder.java`
  - Converts a `RunnerCommandRequest` to a `docker run` argument list.
- Create: `playwright-platform-server/src/main/java/com/example/platform/runner/service/DockerRunnerCommandExecutor.java`
  - Runs Docker CLI, captures logs, and removes containers on cancellation/timeout.
- Create: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerCommandExecutorConfig.java`
  - Enables config properties and selects local or Docker executor based on mode.
- Modify: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerExecutionServiceImpl.java`
  - Remove manual constructor instantiation of the old executor and pass `workspaceRoot/stageName`.
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskExecutionOrchestrator.java`
  - Pass task workspace root and stage names to `runStage`.
- Modify: `playwright-platform-server/src/main/resources/application.yml`
  - Add safe defaults for runner mode and Docker runner settings.
- Modify: `playwright-platform-server/src/main/resources/application-dev.yml`
  - Add local-dev override hooks for Docker runner settings.
- Modify: `playwright-platform-server/src/test/resources/application.yml`
  - Keep tests in `local` mode unless a test explicitly instantiates Docker classes.
- Modify: `docker-compose.yml`
  - Enable Docker runner mode for `server`, mount Docker socket, and bind `.runner-workspaces`.
- Modify: `.env.example`
  - Add runner mode and Docker runner variables.
- Modify: `.gitignore`
  - Ignore `.runner-workspaces/`.
- Modify: `README.md`
  - Document Docker Runner usage and Docker socket risk.
- Modify: `playwright-platform-server/src/test/java/com/example/platform/config/ApplicationConfigurationTest.java`
  - Assert runner defaults are present.
- Modify: `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`
  - Update `RunnerCommandRequest` constructor calls and verify workspace/stage propagation.
- Create: `playwright-platform-server/src/test/java/com/example/platform/runner/DockerCommandBuilderTest.java`
  - Unit coverage for command construction and path validation.
- Create: `playwright-platform-server/src/test/java/com/example/platform/runner/DockerContainerNameFactoryTest.java`
  - Unit coverage for safe container names.

---

### Task 1: Runner Configuration Properties

**Files:**
- Create: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerMode.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerProperties.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/runner/service/DockerRunnerProperties.java`
- Modify: `playwright-platform-server/src/main/resources/application.yml`
- Modify: `playwright-platform-server/src/main/resources/application-dev.yml`
- Modify: `playwright-platform-server/src/test/resources/application.yml`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/config/ApplicationConfigurationTest.java`

- [ ] **Step 1: Add failing configuration assertions**

Append these tests to `playwright-platform-server/src/test/java/com/example/platform/config/ApplicationConfigurationTest.java`:

```java
    @Test
    void shouldExposeRunnerDefaults() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationYaml).contains("runner:");
        assertThat(applicationYaml).contains("mode: ${PLATFORM_RUNNER_MODE:local}");
        assertThat(applicationYaml).contains("workspace-root: ${PLATFORM_RUNNER_WORKSPACE_ROOT:${java.io.tmpdir}/playwright-platform/workspaces}");
        assertThat(applicationYaml).contains("host-workspace-root: ${PLATFORM_RUNNER_HOST_WORKSPACE_ROOT:${platform.runner.workspace-root}}");
        assertThat(applicationYaml).contains("image: ${PLATFORM_RUNNER_DOCKER_IMAGE:mcr.microsoft.com/playwright:v1.44.0-jammy}");
        assertThat(applicationYaml).contains("network: ${PLATFORM_RUNNER_DOCKER_NETWORK:bridge}");
        assertThat(applicationYaml).contains("memory: ${PLATFORM_RUNNER_DOCKER_MEMORY:2g}");
        assertThat(applicationYaml).contains("cpus: ${PLATFORM_RUNNER_DOCKER_CPUS:2}");
        assertThat(applicationYaml).contains("container-workspace-root: ${PLATFORM_RUNNER_DOCKER_CONTAINER_WORKSPACE_ROOT:/workspace/task}");
    }
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=ApplicationConfigurationTest test
```

Expected: FAIL because runner defaults are not in `application.yml`.

- [ ] **Step 3: Add runner mode enum**

Create `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerMode.java`:

```java
package com.example.platform.runner.service;

public enum RunnerMode {
    LOCAL,
    DOCKER
}
```

- [ ] **Step 4: Add runner properties**

Create `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerProperties.java`:

```java
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
```

- [ ] **Step 5: Add Docker runner properties**

Create `playwright-platform-server/src/main/java/com/example/platform/runner/service/DockerRunnerProperties.java`:

```java
package com.example.platform.runner.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.runner.docker")
public class DockerRunnerProperties {
    private String image = "mcr.microsoft.com/playwright:v1.44.0-jammy";
    private String network = "bridge";
    private String memory = "2g";
    private String cpus = "2";
    private String containerWorkspaceRoot = "/workspace/task";
    private boolean removeContainer = true;

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public String getMemory() {
        return memory;
    }

    public void setMemory(String memory) {
        this.memory = memory;
    }

    public String getCpus() {
        return cpus;
    }

    public void setCpus(String cpus) {
        this.cpus = cpus;
    }

    public String getContainerWorkspaceRoot() {
        return containerWorkspaceRoot;
    }

    public void setContainerWorkspaceRoot(String containerWorkspaceRoot) {
        this.containerWorkspaceRoot = containerWorkspaceRoot;
    }

    public boolean isRemoveContainer() {
        return removeContainer;
    }

    public void setRemoveContainer(boolean removeContainer) {
        this.removeContainer = removeContainer;
    }
}
```

- [ ] **Step 6: Add YAML defaults**

Update `playwright-platform-server/src/main/resources/application.yml` runner section to:

```yaml
  runner:
    mode: ${PLATFORM_RUNNER_MODE:local}
    workspace-root: ${PLATFORM_RUNNER_WORKSPACE_ROOT:${java.io.tmpdir}/playwright-platform/workspaces}
    host-workspace-root: ${PLATFORM_RUNNER_HOST_WORKSPACE_ROOT:${platform.runner.workspace-root}}
    docker:
      image: ${PLATFORM_RUNNER_DOCKER_IMAGE:mcr.microsoft.com/playwright:v1.44.0-jammy}
      network: ${PLATFORM_RUNNER_DOCKER_NETWORK:bridge}
      memory: ${PLATFORM_RUNNER_DOCKER_MEMORY:2g}
      cpus: ${PLATFORM_RUNNER_DOCKER_CPUS:2}
      container-workspace-root: ${PLATFORM_RUNNER_DOCKER_CONTAINER_WORKSPACE_ROOT:/workspace/task}
      remove-container: ${PLATFORM_RUNNER_DOCKER_REMOVE_CONTAINER:true}
```

Update `playwright-platform-server/src/main/resources/application-dev.yml` by adding:

```yaml
platform:
  runner:
    mode: ${PLATFORM_RUNNER_MODE:local}
```

Keep `playwright-platform-server/src/test/resources/application.yml` in local mode by adding:

```yaml
platform:
  runner:
    mode: local
```

- [ ] **Step 7: Verify configuration test passes**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=ApplicationConfigurationTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerMode.java \
  playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerProperties.java \
  playwright-platform-server/src/main/java/com/example/platform/runner/service/DockerRunnerProperties.java \
  playwright-platform-server/src/main/resources/application.yml \
  playwright-platform-server/src/main/resources/application-dev.yml \
  playwright-platform-server/src/test/resources/application.yml \
  playwright-platform-server/src/test/java/com/example/platform/config/ApplicationConfigurationTest.java
git commit -m "feat: add runner execution configuration"
```

---

### Task 2: Split Local Executor and Select Executor by Mode

**Files:**
- Delete: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerCommandExecutorImpl.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/runner/service/LocalRunnerCommandExecutor.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerCommandExecutorConfig.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerExecutionServiceImpl.java`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`

- [ ] **Step 1: Add local executor class with existing behavior**

Create `playwright-platform-server/src/main/java/com/example/platform/runner/service/LocalRunnerCommandExecutor.java` by moving the current contents of `RunnerCommandExecutorImpl` and changing the class name:

```java
package com.example.platform.runner.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalRunnerCommandExecutor implements RunnerCommandExecutor {
    @Override
    public RunnerCommandResult execute(RunnerCommandRequest request) {
        Instant startedAt = Instant.now();
        Path logFile = createTempLogFile();
        AtomicInteger lineCount = new AtomicInteger();
        Process process = null;
        Thread logThread = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-lc", request.command())
                    .directory(request.workingDirectory().toFile())
                    .redirectErrorStream(true);
            processBuilder.environment().putAll(request.extraEnv());
            process = processBuilder.start();

            Process runningProcess = process;
            logThread = new Thread(() -> captureOutput(runningProcess, logFile, lineCount), "runner-log-capture");
            logThread.start();

            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (request.cancellationRequested().getAsBoolean()) {
                    process.destroyForcibly();
                    waitForLogThread(logThread);
                    return new RunnerCommandResult(-1, false, true, elapsedMs(startedAt), logFile, lineCount.get());
                }
                if (Duration.between(startedAt, Instant.now()).compareTo(request.timeout()) > 0) {
                    process.destroyForcibly();
                    waitForLogThread(logThread);
                    return new RunnerCommandResult(-1, true, false, elapsedMs(startedAt), logFile, lineCount.get());
                }
            }

            int exitCode = process.exitValue();
            waitForLogThread(logThread);
            return new RunnerCommandResult(exitCode, false, false, elapsedMs(startedAt), logFile, lineCount.get());
        } catch (InterruptedException exception) {
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to execute command: " + request.command(), exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to execute command: " + request.command(), exception);
        }
    }

    private Path createTempLogFile() {
        try {
            return Files.createTempFile("runner-command-", ".log");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create temporary log file", exception);
        }
    }

    private void captureOutput(Process process, Path logFile, AtomicInteger lineCount) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             var writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
                lineCount.incrementAndGet();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void waitForLogThread(Thread logThread) throws InterruptedException {
        if (logThread == null) {
            return;
        }
        logThread.join(1000);
    }

    private long elapsedMs(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }
}
```

- [ ] **Step 2: Delete old executor implementation**

Delete `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerCommandExecutorImpl.java`.

- [ ] **Step 3: Update direct test references**

In `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`, replace:

```java
import com.example.platform.runner.service.RunnerCommandExecutorImpl;
```

with:

```java
import com.example.platform.runner.service.LocalRunnerCommandExecutor;
```

Replace:

```java
new RunnerExecutionServiceImpl();
```

with:

```java
new RunnerExecutionServiceImpl(new LocalRunnerCommandExecutor());
```

Replace:

```java
RunnerCommandExecutor executor = new RunnerCommandExecutorImpl();
```

with:

```java
RunnerCommandExecutor executor = new LocalRunnerCommandExecutor();
```

- [ ] **Step 4: Add executor configuration**

Create `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerCommandExecutorConfig.java`:

```java
package com.example.platform.runner.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({RunnerProperties.class, DockerRunnerProperties.class})
public class RunnerCommandExecutorConfig {
    @Bean
    public RunnerCommandExecutor runnerCommandExecutor(
            RunnerProperties runnerProperties,
            DockerRunnerProperties dockerRunnerProperties) {
        if (runnerProperties.getMode() == RunnerMode.DOCKER) {
            return new DockerRunnerCommandExecutor(
                    dockerRunnerProperties,
                    runnerProperties,
                    new DockerCommandBuilder(dockerRunnerProperties, runnerProperties),
                    new DockerContainerNameFactory());
        }
        return new LocalRunnerCommandExecutor();
    }
}
```

This will not compile until Docker classes are added in Task 4. If implementing task-by-task, temporarily return `new LocalRunnerCommandExecutor()` for both branches, then replace it in Task 4.

- [ ] **Step 5: Remove default constructor from execution service**

Update `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerExecutionServiceImpl.java` to remove:

```java
    public RunnerExecutionServiceImpl() {
        this(new RunnerCommandExecutorImpl());
    }
```

Keep:

```java
    public RunnerExecutionServiceImpl(RunnerCommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }
```

- [ ] **Step 6: Run tests after using temporary local-only branch**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=TaskExecutionServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-server/src/main/java/com/example/platform/runner/service \
  playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java
git commit -m "refactor: split local runner executor"
```

---

### Task 3: Request Context for Workspace and Stage

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerCommandRequest.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerExecutionService.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerExecutionServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskExecutionOrchestrator.java`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`

- [ ] **Step 1: Extend command request**

Replace `RunnerCommandRequest` with:

```java
package com.example.platform.runner.service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;

public record RunnerCommandRequest(
        Path workspaceRoot,
        Path workingDirectory,
        String stageName,
        String command,
        Map<String, String> extraEnv,
        Duration timeout,
        BooleanSupplier cancellationRequested) {
}
```

- [ ] **Step 2: Extend execution service interface**

Change `RunnerExecutionService.runStage` signature to:

```java
RunnerCommandResult runStage(
        Path workspaceRoot,
        Path workingDirectory,
        String stageName,
        String command,
        Map<String, String> extraEnv,
        Duration timeout,
        BooleanSupplier cancellationRequested);
```

- [ ] **Step 3: Update execution service implementation**

Update `RunnerExecutionServiceImpl.runStage` to:

```java
    @Override
    public RunnerCommandResult runStage(
            Path workspaceRoot,
            Path workingDirectory,
            String stageName,
            String command,
            Map<String, String> extraEnv,
            Duration timeout,
            BooleanSupplier cancellationRequested) {
        return commandExecutor.execute(new RunnerCommandRequest(
                workspaceRoot,
                workingDirectory,
                stageName,
                command,
                extraEnv,
                timeout,
                cancellationRequested));
    }
```

Update private fallback calls:

```java
    private int runCommand(Path workingDirectory, String command, Map<String, String> extraEnv) {
        return runStage(workingDirectory, workingDirectory, "LEGACY", command, extraEnv, DEFAULT_STAGE_TIMEOUT, () -> false).exitCode();
    }
```

- [ ] **Step 4: Pass workspace root and stage name from task orchestration**

In `TaskExecutionOrchestrator`, change `runStageWithFallback` signature to:

```java
    private RunnerCommandResult runStageWithFallback(
            TaskEntity task,
            Path workspace,
            Path executionDirectory,
            String command,
            Map<String, String> platformEnv,
            Duration timeout,
            StageCommandType stageCommandType) {
```

Change the call to `runnerExecutionService.runStage` to:

```java
        RunnerCommandResult stageResult = runnerExecutionService.runStage(
                workspace,
                executionDirectory,
                stageCommandType.name(),
                command,
                platformEnv,
                timeout,
                () -> isCancellationRequested(task.getId()));
```

Update install call to include `workspace`:

```java
            RunnerCommandResult installResult = runStageWithFallback(
                    task,
                    workspace,
                    executionDirectory,
                    repository.getInstallCommand(),
                    platformEnv,
                    Duration.ofSeconds(taskExecutionProperties.getInstallTimeoutSeconds()),
                    StageCommandType.INSTALL);
```

Update test call to include `workspace`:

```java
                RunnerCommandResult testResult = runStageWithFallback(
                        task,
                        workspace,
                        executionDirectory,
                        task.getResolvedRunCommand(),
                        platformEnv,
                        Duration.ofSeconds(taskExecutionProperties.getTestTimeoutSeconds()),
                        StageCommandType.TEST);
```

- [ ] **Step 5: Update tests**

In `TaskExecutionServiceTest`, update `RunnerCommandRequest` constructor calls from:

```java
new RunnerCommandRequest(
        tempDir,
        "printf 'line1\nline2\n'",
        Map.of(),
        Duration.ofSeconds(5),
        () -> false);
```

to:

```java
new RunnerCommandRequest(
        tempDir,
        tempDir,
        "TEST",
        "printf 'line1\nline2\n'",
        Map.of(),
        Duration.ofSeconds(5),
        () -> false);
```

Update Mockito verifications of `runStage` to match the new signature:

```java
Mockito.when(context.executionService.runStage(
        Mockito.any(),
        Mockito.any(),
        Mockito.anyString(),
        Mockito.anyString(),
        Mockito.anyMap(),
        Mockito.any(),
        Mockito.any()))
        .thenReturn(new RunnerCommandResult(1, false, false, 10L, installLog, 1));
```

- [ ] **Step 6: Run focused tests**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=TaskExecutionServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-server/src/main/java/com/example/platform/runner/service \
  playwright-platform-server/src/main/java/com/example/platform/task/service/TaskExecutionOrchestrator.java \
  playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java
git commit -m "feat: pass runner workspace context"
```

---

### Task 4: Docker Command Builder and Container Naming

**Files:**
- Create: `playwright-platform-server/src/main/java/com/example/platform/runner/service/DockerContainerNameFactory.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/runner/service/DockerCommandBuilder.java`
- Create: `playwright-platform-server/src/test/java/com/example/platform/runner/DockerContainerNameFactoryTest.java`
- Create: `playwright-platform-server/src/test/java/com/example/platform/runner/DockerCommandBuilderTest.java`

- [ ] **Step 1: Add failing container name test**

Create `playwright-platform-server/src/test/java/com/example/platform/runner/DockerContainerNameFactoryTest.java`:

```java
package com.example.platform.runner;

import com.example.platform.runner.service.DockerContainerNameFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DockerContainerNameFactoryTest {
    @Test
    void shouldCreateSafeContainerName() {
        DockerContainerNameFactory factory = new DockerContainerNameFactory();

        String name = factory.create(101L, "INSTALL");

        assertThat(name).startsWith("playwright-platform-task-101-install-");
        assertThat(name).matches("[a-z0-9][a-z0-9_.-]+");
        assertThat(name.length()).isLessThanOrEqualTo(128);
    }
}
```

- [ ] **Step 2: Add container name factory**

Create `playwright-platform-server/src/main/java/com/example/platform/runner/service/DockerContainerNameFactory.java`:

```java
package com.example.platform.runner.service;

import java.security.SecureRandom;

public class DockerContainerNameFactory {
    private static final SecureRandom RANDOM = new SecureRandom();

    public String create(Long taskId, String stageName) {
        String stage = stageName == null || stageName.isBlank()
                ? "stage"
                : stageName.toLowerCase().replaceAll("[^a-z0-9_.-]", "-");
        String suffix = Long.toUnsignedString(RANDOM.nextLong(), 36);
        String name = "playwright-platform-task-" + taskId + "-" + stage + "-" + suffix;
        return name.length() <= 128 ? name : name.substring(0, 128);
    }
}
```

- [ ] **Step 3: Add failing Docker command builder tests**

Create `playwright-platform-server/src/test/java/com/example/platform/runner/DockerCommandBuilderTest.java`:

```java
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
```

- [ ] **Step 4: Run failing Docker builder tests**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=DockerContainerNameFactoryTest,DockerCommandBuilderTest test
```

Expected: FAIL because builder classes do not exist.

- [ ] **Step 5: Add Docker command builder**

Create `playwright-platform-server/src/main/java/com/example/platform/runner/service/DockerCommandBuilder.java`:

```java
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
```

- [ ] **Step 6: Run Docker builder tests**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=DockerContainerNameFactoryTest,DockerCommandBuilderTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-server/src/main/java/com/example/platform/runner/service/DockerContainerNameFactory.java \
  playwright-platform-server/src/main/java/com/example/platform/runner/service/DockerCommandBuilder.java \
  playwright-platform-server/src/test/java/com/example/platform/runner/DockerContainerNameFactoryTest.java \
  playwright-platform-server/src/test/java/com/example/platform/runner/DockerCommandBuilderTest.java
git commit -m "feat: build docker runner command"
```

---

### Task 5: Docker Runner Executor

**Files:**
- Create: `playwright-platform-server/src/main/java/com/example/platform/runner/service/DockerRunnerCommandExecutor.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerCommandExecutorConfig.java`
- Create: `playwright-platform-server/src/test/java/com/example/platform/runner/DockerRunnerCommandExecutorTest.java`

- [ ] **Step 1: Add testable process launcher interface inside executor file**

When creating the executor, include a package-private launcher interface:

```java
interface RunnerProcessLauncher {
    Process start(List<String> command, Path workingDirectory, Map<String, String> extraEnv) throws IOException;
}
```

Default launcher:

```java
final class ProcessBuilderRunnerProcessLauncher implements RunnerProcessLauncher {
    @Override
    public Process start(List<String> command, Path workingDirectory, Map<String, String> extraEnv) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true);
        builder.environment().putAll(extraEnv);
        return builder.start();
    }
}
```

- [ ] **Step 2: Add executor behavior tests**

Create `playwright-platform-server/src/test/java/com/example/platform/runner/DockerRunnerCommandExecutorTest.java` with tests that instantiate `DockerRunnerCommandExecutor` using a fake `RunnerProcessLauncher`. The test must cover:

```java
@Test
void shouldReturnSuccessfulResultWhenDockerProcessExitsZero()
```

Expected assertions:

```java
assertThat(result.exitCode()).isEqualTo(0);
assertThat(result.timedOut()).isFalse();
assertThat(result.canceled()).isFalse();
assertThat(Files.readString(result.combinedLogFile())).contains("docker output");
```

And:

```java
@Test
void shouldRemoveContainerWhenCancellationIsRequested()
```

Expected assertions:

```java
assertThat(result.canceled()).isTrue();
assertThat(launcher.startedCommands()).anySatisfy(command ->
        assertThat(command).containsExactly("docker", "rm", "-f", "container-name"));
```

And:

```java
@Test
void shouldRemoveContainerWhenCommandTimesOut()
```

Expected assertions:

```java
assertThat(result.timedOut()).isTrue();
assertThat(launcher.startedCommands()).anySatisfy(command ->
        assertThat(command).containsExactly("docker", "rm", "-f", "container-name"));
```

- [ ] **Step 3: Implement Docker runner executor**

Create `playwright-platform-server/src/main/java/com/example/platform/runner/service/DockerRunnerCommandExecutor.java`:

```java
package com.example.platform.runner.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DockerRunnerCommandExecutor implements RunnerCommandExecutor {
    private static final Logger log = LoggerFactory.getLogger(DockerRunnerCommandExecutor.class);
    private static final Pattern TASK_ID_PATTERN = Pattern.compile(".*/([0-9]+)$");

    private final DockerRunnerProperties dockerProperties;
    private final RunnerProperties runnerProperties;
    private final DockerCommandBuilder commandBuilder;
    private final DockerContainerNameFactory containerNameFactory;
    private final RunnerProcessLauncher processLauncher;

    public DockerRunnerCommandExecutor(
            DockerRunnerProperties dockerProperties,
            RunnerProperties runnerProperties,
            DockerCommandBuilder commandBuilder,
            DockerContainerNameFactory containerNameFactory) {
        this(dockerProperties, runnerProperties, commandBuilder, containerNameFactory, new ProcessBuilderRunnerProcessLauncher());
    }

    DockerRunnerCommandExecutor(
            DockerRunnerProperties dockerProperties,
            RunnerProperties runnerProperties,
            DockerCommandBuilder commandBuilder,
            DockerContainerNameFactory containerNameFactory,
            RunnerProcessLauncher processLauncher) {
        this.dockerProperties = dockerProperties;
        this.runnerProperties = runnerProperties;
        this.commandBuilder = commandBuilder;
        this.containerNameFactory = containerNameFactory;
        this.processLauncher = processLauncher;
    }

    @Override
    public RunnerCommandResult execute(RunnerCommandRequest request) {
        Instant startedAt = Instant.now();
        Path logFile = createTempLogFile();
        AtomicInteger lineCount = new AtomicInteger();
        String containerName = containerNameFactory.create(resolveTaskId(request.workspaceRoot()), request.stageName());
        List<String> dockerCommand = commandBuilder.buildRunCommand(request, containerName);
        Process process = null;
        Thread logThread = null;
        try {
            process = processLauncher.start(dockerCommand, request.workspaceRoot(), Map.of());
            Process runningProcess = process;
            logThread = new Thread(() -> captureOutput(runningProcess, logFile, lineCount), "docker-runner-log-capture");
            logThread.start();

            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (request.cancellationRequested().getAsBoolean()) {
                    removeContainer(containerName);
                    process.destroyForcibly();
                    waitForLogThread(logThread);
                    return new RunnerCommandResult(-1, false, true, elapsedMs(startedAt), logFile, lineCount.get());
                }
                if (Duration.between(startedAt, Instant.now()).compareTo(request.timeout()) > 0) {
                    removeContainer(containerName);
                    process.destroyForcibly();
                    waitForLogThread(logThread);
                    return new RunnerCommandResult(-1, true, false, elapsedMs(startedAt), logFile, lineCount.get());
                }
            }

            int exitCode = process.exitValue();
            waitForLogThread(logThread);
            return new RunnerCommandResult(exitCode, false, false, elapsedMs(startedAt), logFile, lineCount.get());
        } catch (InterruptedException exception) {
            if (process != null) {
                process.destroyForcibly();
            }
            removeContainer(containerName);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to execute docker runner command: " + request.command(), exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to execute docker runner command: " + request.command(), exception);
        }
    }

    private Long resolveTaskId(Path workspaceRoot) {
        Matcher matcher = TASK_ID_PATTERN.matcher(workspaceRoot.normalize().toString().replace('\\', '/'));
        return matcher.matches() ? Long.parseLong(matcher.group(1)) : 0L;
    }

    private void removeContainer(String containerName) {
        try {
            Process cleanup = processLauncher.start(List.of("docker", "rm", "-f", containerName), Path.of("."), Map.of());
            cleanup.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.warn("Failed to remove docker runner container. containerName={}, reason={}", containerName, exception.getMessage());
        }
    }

    private Path createTempLogFile() {
        try {
            return Files.createTempFile("docker-runner-command-", ".log");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create temporary log file", exception);
        }
    }

    private void captureOutput(Process process, Path logFile, AtomicInteger lineCount) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             var writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
                lineCount.incrementAndGet();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void waitForLogThread(Thread logThread) throws InterruptedException {
        if (logThread == null) {
            return;
        }
        logThread.join(1000);
    }

    private long elapsedMs(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }
}
```

- [ ] **Step 4: Wire real Docker executor in config**

Ensure `RunnerCommandExecutorConfig` uses Docker classes in Docker mode:

```java
        if (runnerProperties.getMode() == RunnerMode.DOCKER) {
            return new DockerRunnerCommandExecutor(
                    dockerRunnerProperties,
                    runnerProperties,
                    new DockerCommandBuilder(dockerRunnerProperties, runnerProperties),
                    new DockerContainerNameFactory());
        }
```

- [ ] **Step 5: Run runner tests**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest='*Runner*Test,Docker*Test' test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-server/src/main/java/com/example/platform/runner/service \
  playwright-platform-server/src/test/java/com/example/platform/runner
git commit -m "feat: execute runner stages in docker"
```

---

### Task 6: Compose, Env, Ignore, and README

**Files:**
- Modify: `docker-compose.yml`
- Modify: `.env.example`
- Modify: `.gitignore`
- Modify: `README.md`

- [ ] **Step 1: Update `.env.example`**

Append:

```dotenv
PLATFORM_RUNNER_MODE=docker
PLATFORM_RUNNER_WORKSPACE_ROOT=/workspace/.runner-workspaces
PLATFORM_RUNNER_DOCKER_IMAGE=mcr.microsoft.com/playwright:v1.44.0-jammy
PLATFORM_RUNNER_DOCKER_NETWORK=bridge
PLATFORM_RUNNER_DOCKER_MEMORY=2g
PLATFORM_RUNNER_DOCKER_CPUS=2
PLATFORM_RUNNER_DOCKER_CONTAINER_WORKSPACE_ROOT=/workspace/task
```

- [ ] **Step 2: Update `.gitignore`**

Add:

```gitignore
.runner-workspaces/
```

- [ ] **Step 3: Update Compose server service**

In `docker-compose.yml`, update `server.environment` with:

```yaml
      PLATFORM_RUNNER_MODE: ${PLATFORM_RUNNER_MODE:-docker}
      PLATFORM_RUNNER_WORKSPACE_ROOT: ${PLATFORM_RUNNER_WORKSPACE_ROOT:-/workspace/.runner-workspaces}
      PLATFORM_RUNNER_HOST_WORKSPACE_ROOT: ${PLATFORM_RUNNER_HOST_WORKSPACE_ROOT:-${PWD}/.runner-workspaces}
      PLATFORM_RUNNER_DOCKER_IMAGE: ${PLATFORM_RUNNER_DOCKER_IMAGE:-mcr.microsoft.com/playwright:v1.44.0-jammy}
      PLATFORM_RUNNER_DOCKER_NETWORK: ${PLATFORM_RUNNER_DOCKER_NETWORK:-bridge}
      PLATFORM_RUNNER_DOCKER_MEMORY: ${PLATFORM_RUNNER_DOCKER_MEMORY:-2g}
      PLATFORM_RUNNER_DOCKER_CPUS: ${PLATFORM_RUNNER_DOCKER_CPUS:-2}
      PLATFORM_RUNNER_DOCKER_CONTAINER_WORKSPACE_ROOT: ${PLATFORM_RUNNER_DOCKER_CONTAINER_WORKSPACE_ROOT:-/workspace/task}
```

In `server.volumes`, add:

```yaml
      - /var/run/docker.sock:/var/run/docker.sock
      - ./.runner-workspaces:/workspace/.runner-workspaces
```

Remove or replace the old `PLATFORM_RUNNER_WORKSPACE_ROOT: /tmp/playwright-platform/workspaces` line.

- [ ] **Step 4: Update README**

Add a `Docker Runner` subsection under Docker Compose development docs:

```markdown
### Docker Runner

Compose 开发环境默认启用 `PLATFORM_RUNNER_MODE=docker`。后端会通过 Docker socket 启动短生命周期 Runner 容器来执行安装和测试命令，任务工作区位于 `.runner-workspaces/`。

该模式会把宿主机 `/var/run/docker.sock` 挂载给 server 容器。Docker socket 具备较高权限，仅建议用于本地开发或受控环境。Runner 容器本身不会挂载 Docker socket，只挂载当前任务 workspace。

如需回退到本地执行模式，在 `.env` 中设置：

```bash
PLATFORM_RUNNER_MODE=local
```
```

- [ ] **Step 5: Validate Compose config**

Run:

```bash
cd /Users/bytedance/test_platform
docker compose config
```

Expected: command exits 0 and rendered server service includes `/var/run/docker.sock` plus `.runner-workspaces`.

- [ ] **Step 6: Commit**

```bash
cd /Users/bytedance/test_platform
git add docker-compose.yml .env.example .gitignore README.md
git commit -m "chore: enable docker runner compose config"
```

---

### Task 7: Full Verification

**Files:**
- No source changes expected unless tests expose a defect.

- [ ] **Step 1: Run backend tests**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run frontend tests**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-web
npm test
```

Expected: all Vitest suites pass.

- [ ] **Step 3: Run frontend build**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-web
npm run build
```

Expected: build succeeds. Existing chunk size warning for Element Plus is acceptable.

- [ ] **Step 4: Validate Compose**

Run:

```bash
cd /Users/bytedance/test_platform
docker compose config
```

Expected: command exits 0.

- [ ] **Step 5: Inspect final diff**

Run:

```bash
cd /Users/bytedance/test_platform
git status --short
git diff --stat HEAD
```

Expected: no unstaged unrelated files; only intended fifth-stage changes if the previous commits were not made one-by-one.

- [ ] **Step 6: Commit verification fixes if needed**

If fixes were required:

```bash
cd /Users/bytedance/test_platform
git add <fixed-files>
git commit -m "fix: stabilize docker runner verification"
```

If no fixes were required, no commit is needed.

---

## Self-Review

- Spec coverage: Tasks cover local/docker mode, Docker command construction, workspace mount boundaries, cancel/timeout cleanup, Compose/env/docs updates, and full validation.
- Scope: The plan does not introduce Worker services, queues, Kubernetes, or distributed scheduling.
- Type consistency: `RunnerCommandRequest` fields are used consistently as `workspaceRoot`, `workingDirectory`, `stageName`, `command`, `extraEnv`, `timeout`, and `cancellationRequested`.
- Testing: Each implementation task has focused tests before implementation and exact verification commands.
