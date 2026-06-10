# Playwright Platform Artifact Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为成功执行的任务补齐真实报告文件扫描、MinIO 上传、`artifact` 元数据落库和接口可读能力。

**Architecture:** 保持当前中心化执行链路不变，在后端新增一个专职的任务产物归档服务，负责遍历 `reportRelativePath` 目录、上传文件并保存 `ArtifactEntity`。`TaskServiceImpl` 只负责在测试执行成功后调用该服务，并在失败时统一把任务标记为 `FAILED`。

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, Mockito, JUnit 5, MySQL, MinIO.

---

## File Map

- Create: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskArtifactArchiveService.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskArtifactArchiveServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskArtifactArchiveServiceTest.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskControllerTest.java`
- Verify: `playwright_framework/docs/superpowers/specs/2026-06-10-playwright-platform-artifact-persistence-design.md`

## Task 1: 新增任务产物归档服务

**Files:**
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskArtifactArchiveService.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskArtifactArchiveServiceImpl.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskArtifactArchiveServiceTest.java`

- [ ] **Step 1: 先写归档服务失败测试**

Create `playwright-platform-server/src/test/java/com/example/platform/task/TaskArtifactArchiveServiceTest.java`:

```java
package com.example.platform.task;

import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.ArtifactJpaRepository;
import com.example.platform.task.service.TaskArtifactArchiveServiceImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskArtifactArchiveServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldUploadReportFilesAndPersistArtifacts() throws Exception {
        ObjectStorageService objectStorageService = Mockito.mock(ObjectStorageService.class);
        ArtifactJpaRepository artifactRepository = Mockito.mock(ArtifactJpaRepository.class);

        Path workspace = tempDir.resolve("workspace");
        Path reportDir = workspace.resolve("playwright-report");
        Files.createDirectories(reportDir.resolve("data"));
        Files.writeString(reportDir.resolve("index.html"), "<html>ok</html>");
        Files.writeString(reportDir.resolve("data/trace.zip"), "zip");

        Mockito.when(objectStorageService.uploadFile(Mockito.anyString(), Mockito.anyString(), Mockito.any(Path.class)))
                .thenAnswer(invocation -> "http://minio/" + invocation.getArgument(1, String.class));

        TaskArtifactArchiveServiceImpl service = new TaskArtifactArchiveServiceImpl(
                objectStorageService,
                artifactRepository,
                "qa-report");

        List<ArtifactEntity> artifacts = service.archiveReportArtifacts(101L, workspace, "playwright-report");

        assertThat(artifacts).hasSize(2);
        assertThat(artifacts)
                .extracting(ArtifactEntity::getArtifactType)
                .containsOnly("REPORT_FILE");
        assertThat(artifacts)
                .extracting(ArtifactEntity::getObjectKey)
                .containsExactlyInAnyOrder(
                        "runs/101/artifacts/index.html",
                        "runs/101/artifacts/data/trace.zip");

        ArgumentCaptor<ArtifactEntity> captor = ArgumentCaptor.forClass(ArtifactEntity.class);
        Mockito.verify(artifactRepository, Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ArtifactEntity::getTaskId)
                .containsOnly(101L);
    }

    @Test
    void shouldReturnEmptyWhenReportDirectoryMissing() {
        ObjectStorageService objectStorageService = Mockito.mock(ObjectStorageService.class);
        ArtifactJpaRepository artifactRepository = Mockito.mock(ArtifactJpaRepository.class);

        TaskArtifactArchiveServiceImpl service = new TaskArtifactArchiveServiceImpl(
                objectStorageService,
                artifactRepository,
                "qa-report");

        List<ArtifactEntity> artifacts = service.archiveReportArtifacts(101L, tempDir, "playwright-report");

        assertThat(artifacts).isEmpty();
        Mockito.verifyNoInteractions(objectStorageService, artifactRepository);
    }

    @Test
    void shouldFailWhenUploadFails() throws Exception {
        ObjectStorageService objectStorageService = Mockito.mock(ObjectStorageService.class);
        ArtifactJpaRepository artifactRepository = Mockito.mock(ArtifactJpaRepository.class);

        Path workspace = tempDir.resolve("workspace");
        Path reportDir = workspace.resolve("playwright-report");
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("index.html"), "<html>broken</html>");

        Mockito.when(objectStorageService.uploadFile("qa-report", "runs/101/artifacts/index.html", reportDir.resolve("index.html")))
                .thenThrow(new IllegalStateException("upload failed"));

        TaskArtifactArchiveServiceImpl service = new TaskArtifactArchiveServiceImpl(
                objectStorageService,
                artifactRepository,
                "qa-report");

        assertThatThrownBy(() -> service.archiveReportArtifacts(101L, workspace, "playwright-report"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("upload failed");
    }
}
```

- [ ] **Step 2: 跑测试确认服务尚不存在**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=TaskArtifactArchiveServiceTest test
```

Expected: FAIL，提示 `TaskArtifactArchiveServiceImpl` 或 `archiveReportArtifacts` 不存在。

- [ ] **Step 3: 写最小接口和实现**

Create `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskArtifactArchiveService.java`:

```java
package com.example.platform.task.service;

import com.example.platform.task.model.ArtifactEntity;
import java.nio.file.Path;
import java.util.List;

public interface TaskArtifactArchiveService {
    List<ArtifactEntity> archiveReportArtifacts(Long taskId, Path workspace, String reportRelativePath);
}
```

Create `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskArtifactArchiveServiceImpl.java`:

```java
package com.example.platform.task.service;

import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.ArtifactJpaRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TaskArtifactArchiveServiceImpl implements TaskArtifactArchiveService {
    private final ObjectStorageService objectStorageService;
    private final ArtifactJpaRepository artifactRepository;
    private final String storageBucket;

    public TaskArtifactArchiveServiceImpl(
            ObjectStorageService objectStorageService,
            ArtifactJpaRepository artifactRepository,
            @Value("${platform.storage.bucket}") String storageBucket) {
        this.objectStorageService = objectStorageService;
        this.artifactRepository = artifactRepository;
        this.storageBucket = storageBucket;
    }

    @Override
    public List<ArtifactEntity> archiveReportArtifacts(Long taskId, Path workspace, String reportRelativePath) {
        Path reportDir = workspace.resolve(reportRelativePath);
        if (!Files.exists(reportDir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(reportDir)) {
            return walk.filter(Files::isRegularFile)
                    .sorted(Comparator.naturalOrder())
                    .map(path -> persistArtifact(taskId, reportDir, path))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan report artifacts", exception);
        }
    }

    private ArtifactEntity persistArtifact(Long taskId, Path reportDir, Path file) {
        Path relativePath = reportDir.relativize(file);
        String objectKey = "runs/" + taskId + "/artifacts/" + relativePath.toString().replace('\\', '/');
        String url = objectStorageService.uploadFile(storageBucket, objectKey, file);

        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setTaskId(taskId);
        artifact.setArtifactType("REPORT_FILE");
        artifact.setBucket(storageBucket);
        artifact.setObjectKey(objectKey);
        artifact.setContentType(probeContentType(file));
        artifact.setSize(readFileSize(file));
        artifact.setUrl(url);
        return artifactRepository.save(artifact);
    }

    private String probeContentType(Path file) {
        try {
            return Files.probeContentType(file);
        } catch (IOException exception) {
            return null;
        }
    }

    private Long readFileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read artifact size", exception);
        }
    }
}
```

- [ ] **Step 4: 跑测试验证归档服务通过**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=TaskArtifactArchiveServiceTest test
```

Expected: PASS，3 个测试全部通过。

- [ ] **Step 5: 提交归档服务**

Run:

```bash
git -C /Users/bytedance/test_platform/playwright-platform-server add src/main/java/com/example/platform/task/service/TaskArtifactArchiveService.java src/main/java/com/example/platform/task/service/TaskArtifactArchiveServiceImpl.java src/test/java/com/example/platform/task/TaskArtifactArchiveServiceTest.java
git -C /Users/bytedance/test_platform/playwright-platform-server commit -m "feat: archive report files as task artifacts"
```

Expected: 生成专注于 artifact 归档服务的提交。

## Task 2: 接入任务执行链路并补失败策略

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`

- [ ] **Step 1: 先写任务执行失败测试**

Update `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`，新增依赖和两个测试：

```java
import com.example.platform.task.service.TaskArtifactArchiveService;
```

```java
@Test
void shouldArchiveArtifactsAfterSuccessfulRun() {
    SceneJpaRepository sceneRepository = Mockito.mock(SceneJpaRepository.class);
    TestRepositoryJpaRepository repositoryRepository = Mockito.mock(TestRepositoryJpaRepository.class);
    TaskJpaRepository taskRepository = Mockito.mock(TaskJpaRepository.class);
    ArtifactJpaRepository artifactRepository = Mockito.mock(ArtifactJpaRepository.class);
    RunnerWorkspaceService workspaceService = Mockito.mock(RunnerWorkspaceService.class);
    RunnerExecutionService executionService = Mockito.mock(RunnerExecutionService.class);
    ReportArchiveService archiveService = Mockito.mock(ReportArchiveService.class);
    TaskArtifactArchiveService taskArtifactArchiveService = Mockito.mock(TaskArtifactArchiveService.class);
    ObjectStorageService objectStorageService = Mockito.mock(ObjectStorageService.class);

    SceneEntity scene = new SceneEntity();
    scene.setId(11L);
    scene.setRepoId(21L);
    scene.setBranch("main");
    scene.setRunCommand("npm run test:e2e");

    TestRepositoryEntity repository = new TestRepositoryEntity();
    repository.setId(21L);
    repository.setGitUrl("git@demo/repo.git");
    repository.setInstallCommand("npm install");
    repository.setReportRelativePath("playwright-report");

    Mockito.when(sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
    Mockito.when(repositoryRepository.findById(21L)).thenReturn(Optional.of(repository));
    Mockito.when(taskRepository.save(Mockito.any(TaskEntity.class))).thenAnswer(invocation -> {
        TaskEntity task = invocation.getArgument(0);
        if (task.getId() == null) {
            task.setId(101L);
        }
        return task;
    });
    Mockito.when(workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L)).thenReturn(Path.of("/tmp/task-101"));
    Mockito.when(executionService.installDependencies(Path.of("/tmp/task-101"), "npm install")).thenReturn(0);
    Mockito.when(executionService.runTests(Path.of("/tmp/task-101"), "npm run test:e2e")).thenReturn(0);
    Mockito.when(archiveService.archiveReport(Path.of("/tmp/task-101"), 101L, "playwright-report"))
            .thenReturn("http://minio/qa-report/runs/101/report/index.html");

    TaskServiceImpl service = new TaskServiceImpl(
            sceneRepository,
            repositoryRepository,
            taskRepository,
            artifactRepository,
            workspaceService,
            executionService,
            archiveService,
            taskArtifactArchiveService,
            objectStorageService,
            "qa-report",
            "http://minio");

    TaskEntity result = service.createAndRun(11L);

    assertThat(result.getStatus()).isEqualTo("SUCCESS");
    Mockito.verify(taskArtifactArchiveService)
            .archiveReportArtifacts(101L, Path.of("/tmp/task-101"), "playwright-report");
}
```

```java
@Test
void shouldMarkTaskFailedWhenArtifactArchivingFails() {
    SceneJpaRepository sceneRepository = Mockito.mock(SceneJpaRepository.class);
    TestRepositoryJpaRepository repositoryRepository = Mockito.mock(TestRepositoryJpaRepository.class);
    TaskJpaRepository taskRepository = Mockito.mock(TaskJpaRepository.class);
    ArtifactJpaRepository artifactRepository = Mockito.mock(ArtifactJpaRepository.class);
    RunnerWorkspaceService workspaceService = Mockito.mock(RunnerWorkspaceService.class);
    RunnerExecutionService executionService = Mockito.mock(RunnerExecutionService.class);
    ReportArchiveService archiveService = Mockito.mock(ReportArchiveService.class);
    TaskArtifactArchiveService taskArtifactArchiveService = Mockito.mock(TaskArtifactArchiveService.class);
    ObjectStorageService objectStorageService = Mockito.mock(ObjectStorageService.class);

    SceneEntity scene = new SceneEntity();
    scene.setId(11L);
    scene.setRepoId(21L);
    scene.setBranch("main");
    scene.setRunCommand("npm run test:e2e");

    TestRepositoryEntity repository = new TestRepositoryEntity();
    repository.setId(21L);
    repository.setGitUrl("git@demo/repo.git");
    repository.setInstallCommand("npm install");
    repository.setReportRelativePath("playwright-report");

    Mockito.when(sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
    Mockito.when(repositoryRepository.findById(21L)).thenReturn(Optional.of(repository));
    Mockito.when(taskRepository.save(Mockito.any(TaskEntity.class))).thenAnswer(invocation -> {
        TaskEntity task = invocation.getArgument(0);
        if (task.getId() == null) {
            task.setId(101L);
        }
        return task;
    });
    Mockito.when(workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L)).thenReturn(Path.of("/tmp/task-101"));
    Mockito.when(executionService.installDependencies(Path.of("/tmp/task-101"), "npm install")).thenReturn(0);
    Mockito.when(executionService.runTests(Path.of("/tmp/task-101"), "npm run test:e2e")).thenReturn(0);
    Mockito.when(archiveService.archiveReport(Path.of("/tmp/task-101"), 101L, "playwright-report"))
            .thenReturn("http://minio/qa-report/runs/101/report/index.html");
    Mockito.when(taskArtifactArchiveService.archiveReportArtifacts(101L, Path.of("/tmp/task-101"), "playwright-report"))
            .thenThrow(new IllegalStateException("artifact upload failed"));

    TaskServiceImpl service = new TaskServiceImpl(
            sceneRepository,
            repositoryRepository,
            taskRepository,
            artifactRepository,
            workspaceService,
            executionService,
            archiveService,
            taskArtifactArchiveService,
            objectStorageService,
            "qa-report",
            "http://minio");

    TaskEntity result = service.createAndRun(11L);

    assertThat(result.getStatus()).isEqualTo("FAILED");
    assertThat(result.getLogUrl()).contains("artifact upload failed");
}
```

- [ ] **Step 2: 跑测试确认 `TaskServiceImpl` 尚未接入新服务**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=TaskExecutionServiceTest test
```

Expected: FAIL，提示构造函数参数不匹配或 `archiveReportArtifacts` 未被调用。

- [ ] **Step 3: 修改任务执行服务最小实现**

Update `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`:

```java
private final TaskArtifactArchiveService taskArtifactArchiveService;
```

```java
public TaskServiceImpl(
        SceneJpaRepository sceneRepository,
        TestRepositoryJpaRepository repositoryRepository,
        TaskJpaRepository taskRepository,
        ArtifactJpaRepository artifactRepository,
        RunnerWorkspaceService runnerWorkspaceService,
        RunnerExecutionService runnerExecutionService,
        ReportArchiveService reportArchiveService,
        TaskArtifactArchiveService taskArtifactArchiveService,
        ObjectStorageService objectStorageService,
        @Value("${platform.storage.bucket}") String storageBucket,
        @Value("${platform.storage.minio.endpoint}") String minioEndpoint) {
    this.sceneRepository = sceneRepository;
    this.repositoryRepository = repositoryRepository;
    this.taskRepository = taskRepository;
    this.artifactRepository = artifactRepository;
    this.runnerWorkspaceService = runnerWorkspaceService;
    this.runnerExecutionService = runnerExecutionService;
    this.reportArchiveService = reportArchiveService;
    this.taskArtifactArchiveService = taskArtifactArchiveService;
    this.objectStorageService = objectStorageService;
    this.storageBucket = storageBucket;
    this.minioEndpoint = minioEndpoint;
}
```

```java
Path workspace = runnerWorkspaceService.prepareWorkspace(repository.getGitUrl(), scene.getBranch(), task.getId());
installStatus = runnerExecutionService.installDependencies(workspace, repository.getInstallCommand());
if (installStatus == 0) {
    runStatus = runnerExecutionService.runTests(workspace, scene.getRunCommand());
    if (runStatus == 0) {
        task.setReportUrl(reportArchiveService.archiveReport(workspace, task.getId(), repository.getReportRelativePath()));
        taskArtifactArchiveService.archiveReportArtifacts(task.getId(), workspace, repository.getReportRelativePath());
    }
}
```

- [ ] **Step 4: 跑测试验证执行链路通过**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=TaskExecutionServiceTest test
```

Expected: PASS，新增归档调用和失败策略测试通过。

- [ ] **Step 5: 提交任务执行接入**

Run:

```bash
git -C /Users/bytedance/test_platform/playwright-platform-server add src/main/java/com/example/platform/task/service/TaskServiceImpl.java src/test/java/com/example/platform/task/TaskExecutionServiceTest.java
git -C /Users/bytedance/test_platform/playwright-platform-server commit -m "feat: persist task artifacts after successful runs"
```

Expected: 生成任务执行链路接入提交。

## Task 3: 补接口契约测试并做端到端验证

**Files:**
- Modify: `playwright-platform-server/src/test/java/com/example/platform/task/TaskControllerTest.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`

- [ ] **Step 1: 先补控制器契约断言**

Update `playwright-platform-server/src/test/java/com/example/platform/task/TaskControllerTest.java`:

```java
ArtifactEntity artifact = new ArtifactEntity();
artifact.setId(10L);
artifact.setTaskId(1L);
artifact.setArtifactType("REPORT_FILE");
artifact.setBucket("qa-report");
artifact.setObjectKey("runs/1/artifacts/index.html");
artifact.setUrl("http://localhost:9000/qa-report/runs/1/artifacts/index.html?X-Amz-Signature=demo");
```

```java
mockMvc.perform(get("/api/tasks/1/artifacts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].artifactType").value("REPORT_FILE"))
        .andExpect(jsonPath("$[0].taskId").value(1))
        .andExpect(jsonPath("$[0].objectKey").value("runs/1/artifacts/index.html"))
        .andExpect(jsonPath("$[0].url").value("http://localhost:9000/qa-report/runs/1/artifacts/index.html?X-Amz-Signature=demo"));
```

- [ ] **Step 2: 跑后端测试确认接口契约通过**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=TaskControllerTest,TaskExecutionServiceTest,TaskArtifactArchiveServiceTest test
```

Expected: PASS，控制器契约与服务层测试全部通过。

- [ ] **Step 3: 跑全量测试**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn test
```

Expected: PASS，无新增失败测试。

- [ ] **Step 4: 做真实烟雾验证**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

Then in another terminal:

```bash
python3 - <<'PY'
import json, urllib.request
base='http://127.0.0.1:8081'
repo_payload={
  'name':'artifact-smoke-repo',
  'gitUrl':'/Users/bytedance/test_platform/.tmp/minio-smoke-repo',
  'defaultBranch':'main',
  'packageManager':'npm',
  'installCommand':'true',
  'runCommandTemplate':'{command}',
  'testRoot':'.',
  'reportRelativePath':'playwright-report',
  'nodeVersion':'20',
  'enabled': True,
}
scene_payload={
  'name':'artifact-smoke-scene',
  'branch':'main',
  'testSelectorType':'COMMAND',
  'testSelectorValue':'smoke',
  'projectName':'chromium',
  'browser':'chromium',
  'envJson':'{}',
  'runCommand':'mkdir -p playwright-report/data && printf "<html>ok</html>" > playwright-report/index.html && printf "trace" > playwright-report/data/trace.zip',
  'enabled': True,
}
def post(path, payload):
  req=urllib.request.Request(base+path, data=json.dumps(payload).encode(), headers={'Content-Type':'application/json'})
  with urllib.request.urlopen(req) as resp:
    return json.loads(resp.read().decode())
repo=post('/api/repos', repo_payload)
scene_payload['repoId']=repo['id']
scene=post('/api/scenes', scene_payload)
req=urllib.request.Request(base+f"/api/scenes/{scene['id']}/run", data=b'', method='POST')
with urllib.request.urlopen(req) as resp:
  task=json.loads(resp.read().decode())
with urllib.request.urlopen(base+f"/api/tasks/{task['id']}/artifacts") as resp:
  artifacts=json.loads(resp.read().decode())
print(json.dumps({'task': task, 'artifacts': artifacts}, ensure_ascii=False))
PY
```

Expected: `artifacts` 至少返回 `index.html` 和 `data/trace.zip` 两条记录，且 `url` 包含 `X-Amz-` 参数。

- [ ] **Step 5: 提交契约测试与验证结果**

Run:

```bash
git -C /Users/bytedance/test_platform/playwright-platform-server add src/test/java/com/example/platform/task/TaskControllerTest.java src/test/java/com/example/platform/task/TaskExecutionServiceTest.java
git -C /Users/bytedance/test_platform/playwright-platform-server commit -m "test: cover artifact persistence workflow"
```

Expected: 生成测试与验证提交。

## Self-Review Checklist

- [ ] 设计文档中的“扫描报告目录、上传 MinIO、写 artifact 表、保持预签名读取行为”都已在任务 1-3 覆盖。
- [ ] 计划中未出现 `TODO`、`TBD`、`类似 Task N` 之类占位内容。
- [ ] 新增类型和方法名保持一致：`TaskArtifactArchiveService`、`archiveReportArtifacts()`、`REPORT_FILE`。

