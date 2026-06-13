# Repository Working Directory And Platform Env Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a repository-level `workingDirectory` field so monorepo Playwright projects run from a subdirectory without requiring `cd xxx && ...`, and inject `PLAYWRIGHT_PLATFORM_MODE=true` automatically during install and test execution.

**Architecture:** Extend the repository model with an optional `workingDirectory`, interpret `testRoot` and `reportRelativePath` relative to that directory, and keep command construction focused on Playwright arguments only. The runner layer becomes responsible for `cwd` and extra environment variables, while the task service resolves the effective execution directory and passes platform env to install/test commands.

**Tech Stack:** Spring Boot, JPA, Flyway SQL migration, Vue 3, Pinia, Element Plus, Vitest, JUnit, Mockito

---

### Task 1: Add Repository Working Directory Field

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/model/TestRepositoryEntity.java`
- Create: `playwright-platform-server/src/main/resources/db/migration/V5__add_repository_working_directory.sql`
- Test: `playwright-platform-server/src/test/java/com/example/platform/config/EntitySchemaMappingTest.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/repository/RepositoryControllerTest.java`

- [ ] **Step 1: Write the failing repository controller test**

```java
@Test
void shouldCreateRepositoryWithWorkingDirectory() throws Exception {
    TestRepositoryEntity entity = new TestRepositoryEntity();
    entity.setId(1L);
    entity.setName("testframe");
    entity.setGitUrl("https://github.com/demo/testframe.git");
    entity.setDefaultBranch("main");
    entity.setWorkingDirectory("playwright_framework");
    entity.setInstallCommand("npm install");
    entity.setRunCommandTemplate("npm run test:e2e --");
    entity.setTestRoot("tests");
    entity.setReportRelativePath("reports/allure-report");
    entity.setResultsIndexRelativePath("test-results/.playwright-results.json");
    entity.setArtifactRootRelativePath(".playwright-artifacts");
    entity.setEnabled(true);

    Mockito.when(repositoryService.create(Mockito.any(TestRepositoryEntity.class))).thenReturn(entity);

    mockMvc.perform(post("/api/repos")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "testframe",
                  "gitUrl": "https://github.com/demo/testframe.git",
                  "defaultBranch": "main",
                  "workingDirectory": "playwright_framework",
                  "installCommand": "npm install",
                  "runCommandTemplate": "npm run test:e2e --",
                  "testRoot": "tests",
                  "reportRelativePath": "reports/allure-report",
                  "resultsIndexRelativePath": "test-results/.playwright-results.json",
                  "artifactRootRelativePath": ".playwright-artifacts",
                  "enabled": true
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.workingDirectory").value("playwright_framework"));
}
```

- [ ] **Step 2: Run the focused controller test and verify it fails**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=RepositoryControllerTest#shouldCreateRepositoryWithWorkingDirectory test`
Expected: FAIL because `workingDirectory` is missing from entity serialization or request binding.

- [ ] **Step 3: Add the JPA field and migration**

```java
@Column(name = "working_directory", length = 256)
private String workingDirectory;

public String getWorkingDirectory() { return workingDirectory; }
public void setWorkingDirectory(String workingDirectory) { this.workingDirectory = workingDirectory; }
```

```sql
ALTER TABLE test_repository
    ADD COLUMN working_directory VARCHAR(256) NULL AFTER default_branch;
```

- [ ] **Step 4: Extend schema mapping coverage**

```java
assertThat(columnsByField.get("workingDirectory").getName()).isEqualTo("working_directory");
assertThat(columnsByField.get("workingDirectory").getLength()).isEqualTo(256);
assertThat(columnsByField.get("workingDirectory").isNullable()).isTrue();
```

- [ ] **Step 5: Re-run the backend repository tests**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=RepositoryControllerTest,EntitySchemaMappingTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add playwright-platform-server/src/main/java/com/example/platform/repository/model/TestRepositoryEntity.java \
  playwright-platform-server/src/main/resources/db/migration/V5__add_repository_working_directory.sql \
  playwright-platform-server/src/test/java/com/example/platform/config/EntitySchemaMappingTest.java \
  playwright-platform-server/src/test/java/com/example/platform/repository/RepositoryControllerTest.java
git commit -m "feat: add repository working directory"
```

### Task 2: Teach Runner Execution About cwd And Extra Env

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerExecutionService.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerExecutionServiceImpl.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`

- [ ] **Step 1: Write the failing execution service test for subdirectory cwd**

```java
@Test
void shouldRunInstallAndTestsInsideResolvedWorkingDirectory() {
    TestRepositoryEntity repository = new TestRepositoryEntity();
    repository.setGitUrl("git@demo/repo.git");
    repository.setDefaultBranch("main");
    repository.setWorkingDirectory("playwright_framework");
    repository.setInstallCommand("npm install");
    repository.setRunCommandTemplate("npm run test:e2e --");
    repository.setTestRoot("tests");
    repository.setReportRelativePath("reports/allure-report");
    repository.setResultsIndexRelativePath("test-results/.playwright-results.json");
    repository.setArtifactRootRelativePath(".playwright-artifacts");

    Mockito.when(workspaceService.prepareWorkspace("git@demo/repo.git", "main", 101L))
            .thenReturn(Path.of("/tmp/task-101"));
    Mockito.when(executionService.installDependencies(
            Path.of("/tmp/task-101/playwright_framework"),
            "npm install",
            Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"))).thenReturn(0);
}
```

- [ ] **Step 2: Run the focused task execution test and verify it fails**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=TaskExecutionServiceTest#shouldRunInstallAndTestsInsideResolvedWorkingDirectory test`
Expected: FAIL because the runner interface still only accepts `(Path, String)` and no env map.

- [ ] **Step 3: Change the runner interface to accept env**

```java
public interface RunnerExecutionService {
    int installDependencies(Path workingDirectory, String installCommand, Map<String, String> extraEnv);
    int runTests(Path workingDirectory, String runCommand, Map<String, String> extraEnv);
}
```

- [ ] **Step 4: Implement cwd and env injection in the runner**

```java
@Override
public int installDependencies(Path workingDirectory, String installCommand, Map<String, String> extraEnv) {
    return runCommand(workingDirectory, installCommand, extraEnv);
}

@Override
public int runTests(Path workingDirectory, String runCommand, Map<String, String> extraEnv) {
    return runCommand(workingDirectory, runCommand, extraEnv);
}

private int runCommand(Path workingDirectory, String command, Map<String, String> extraEnv) {
    try {
        ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-lc", command)
                .directory(workingDirectory.toFile())
                .inheritIO();
        processBuilder.environment().putAll(extraEnv);
        Process process = processBuilder.start();
        return process.waitFor();
    } catch (IOException | InterruptedException exception) {
        throw new IllegalStateException("Failed to execute command: " + command, exception);
    }
}
```

- [ ] **Step 5: Re-run the focused execution test**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=TaskExecutionServiceTest#shouldRunInstallAndTestsInsideResolvedWorkingDirectory test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerExecutionService.java \
  playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerExecutionServiceImpl.java \
  playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java
git commit -m "refactor: support runner cwd and env injection"
```

### Task 3: Resolve Effective Execution Directory In Task Service

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`

- [ ] **Step 1: Write the failing task service test for platform env injection**

```java
@Test
void shouldInjectPlatformModeEnvForInstallAndRun() {
    TestRepositoryEntity repository = new TestRepositoryEntity();
    repository.setId(21L);
    repository.setGitUrl("git@demo/repo.git");
    repository.setDefaultBranch("main");
    repository.setWorkingDirectory("playwright_framework");
    repository.setInstallCommand("npm install");
    repository.setRunCommandTemplate("npm run test:e2e --");
    repository.setTestRoot("tests");
    repository.setReportRelativePath("reports/allure-report");
    repository.setResultsIndexRelativePath("test-results/.playwright-results.json");
    repository.setArtifactRootRelativePath(".playwright-artifacts");

    Mockito.verify(executionService).installDependencies(
            Path.of("/tmp/task-101/playwright_framework"),
            "npm install",
            Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"));
    Mockito.verify(executionService).runTests(
            Path.of("/tmp/task-101/playwright_framework"),
            "npm run test:e2e -- --project chromium --target tests/codegen/login.spec.ts",
            Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"));
}
```

- [ ] **Step 2: Run the focused task service tests and verify they fail**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=TaskExecutionServiceTest test`
Expected: FAIL because `TaskServiceImpl` still executes in workspace root and does not pass env.

- [ ] **Step 3: Add execution directory resolution and workspace boundary validation**

```java
private Path resolveExecutionDirectory(Path workspace, TestRepositoryEntity repository) {
    String workingDirectory = repository.getWorkingDirectory();
    if (workingDirectory == null || workingDirectory.isBlank()) {
        return workspace;
    }
    Path resolved = workspace.resolve(workingDirectory).normalize();
    if (!resolved.startsWith(workspace.normalize())) {
        throw new IllegalArgumentException("Working directory escapes repository workspace: " + workingDirectory);
    }
    return resolved;
}

private Map<String, String> platformEnv() {
    return Map.of("PLAYWRIGHT_PLATFORM_MODE", "true");
}
```

- [ ] **Step 4: Use the resolved execution directory for install and run**

```java
Path workspace = runnerWorkspaceService.prepareWorkspace(repository.getGitUrl(), resolvedBranch, task.getId());
Path executionDirectory = resolveExecutionDirectory(workspace, repository);
Map<String, String> platformEnv = platformEnv();

installStatus = runnerExecutionService.installDependencies(
        executionDirectory,
        repository.getInstallCommand(),
        platformEnv);
if (installStatus == 0) {
    runStatus = runnerExecutionService.runTests(
            executionDirectory,
            task.getResolvedRunCommand(),
            platformEnv);
}
```

- [ ] **Step 5: Keep report and artifact parsing rooted at workspace**

```java
task.setReportUrl(reportArchiveService.archiveReport(workspace, task.getId(), repository.getReportRelativePath()));
Path resultsIndex = workspace.resolve(repository.getResultsIndexRelativePath());
ParsedTaskResults parsedTaskResults = taskCaseResultParseService.parse(task.getId(), resultsIndex, workspace);
```

Note: this task intentionally changes only install/test cwd. Report parsing still uses repository-relative paths rooted at the cloned workspace until a later cleanup task reinterprets report paths relative to `workingDirectory`.

- [ ] **Step 6: Re-run execution tests**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=TaskExecutionServiceTest test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java \
  playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java
git commit -m "feat: resolve execution directory for repository tasks"
```

### Task 4: Update Frontend Repository Form For Working Directory

**Files:**
- Modify: `playwright-platform-web/src/types/repository.ts`
- Modify: `playwright-platform-web/src/stores/repository.ts`
- Modify: `playwright-platform-web/src/views/repository/RepositoryListView.vue`
- Test: `playwright-platform-web/tests/unit/stores/repository.test.ts`
- Test: `playwright-platform-web/tests/unit/views/repository/RepositoryListView.test.ts`

- [ ] **Step 1: Write the failing frontend tests**

```ts
it('creates an empty repository form with workingDirectory support', () => {
  const store = useRepositoryStore()
  expect(store.createEmptyForm()).toMatchObject({
    workingDirectory: '',
    runCommandTemplate: 'npm run test:e2e --',
    testRoot: 'tests',
    reportRelativePath: 'reports/allure-report',
  })
})

it('renders working directory guidance for monorepo repositories', async () => {
  const wrapper = mount(RepositoryListView, { global: buildGlobals() })
  await wrapper.find('button').trigger('click')
  expect(wrapper.text()).toContain('工作目录')
  expect(wrapper.text()).toContain('为空则直接使用仓库根目录')
  expect(wrapper.text()).toContain('相对工作目录')
})
```

- [ ] **Step 2: Run the focused frontend tests and verify they fail**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- tests/unit/stores/repository.test.ts tests/unit/views/repository/RepositoryListView.test.ts`
Expected: FAIL because the repository type and view do not contain `workingDirectory`.

- [ ] **Step 3: Add the field to repository types and defaults**

```ts
export interface RepositoryRecord {
  id: number
  name: string
  gitUrl: string
  defaultBranch: string
  workingDirectory?: string
  installCommand: string
  runCommandTemplate: string
  testRoot: string
  reportRelativePath: string
  enabled: boolean
}

export const createRepositoryForm = (): RepositoryForm => ({
  name: '',
  gitUrl: '',
  defaultBranch: 'main',
  workingDirectory: '',
  installCommand: 'npm install',
  runCommandTemplate: 'npm run test:e2e --',
  testRoot: 'tests',
  reportRelativePath: 'reports/allure-report',
  enabled: true,
})
```

- [ ] **Step 4: Add the new form field and guidance text**

```vue
<el-form-item label="工作目录">
  <el-input v-model="form.workingDirectory" placeholder="为空则直接使用仓库根目录，如：playwright_framework" />
</el-form-item>
<el-form-item label="安装命令">
  <el-input v-model="form.installCommand" placeholder="只填写命令本体，如：npm install" />
</el-form-item>
<el-form-item label="测试执行命令">
  <el-input v-model="form.runCommandTemplate" placeholder="只填写稳定入口，如：npm run test:e2e --" />
</el-form-item>
<el-form-item label="测试目录">
  <el-input v-model="form.testRoot" placeholder="相对工作目录，如：tests" />
</el-form-item>
<el-form-item label="报告目录">
  <el-input v-model="form.reportRelativePath" placeholder="相对工作目录，如：reports/allure-report" />
</el-form-item>
```

- [ ] **Step 5: Re-run the frontend tests**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- tests/unit/stores/repository.test.ts tests/unit/views/repository/RepositoryListView.test.ts`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add playwright-platform-web/src/types/repository.ts \
  playwright-platform-web/src/stores/repository.ts \
  playwright-platform-web/src/views/repository/RepositoryListView.vue \
  playwright-platform-web/tests/unit/stores/repository.test.ts \
  playwright-platform-web/tests/unit/views/repository/RepositoryListView.test.ts
git commit -m "feat: add repository working directory form field"
```

### Task 5: Reinterpret Report And Test Paths Relative To Working Directory

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`

- [ ] **Step 1: Write the failing test for report path resolution**

```java
@Test
void shouldResolveReportAndResultsPathsFromWorkingDirectory() {
    TestRepositoryEntity repository = new TestRepositoryEntity();
    repository.setWorkingDirectory("playwright_framework");
    repository.setReportRelativePath("reports/allure-report");
    repository.setResultsIndexRelativePath("test-results/.playwright-results.json");
    repository.setArtifactRootRelativePath(".playwright-artifacts");

    Mockito.when(archiveService.archiveReport(
            Path.of("/tmp/task-101/playwright_framework"),
            101L,
            "reports/allure-report"))
            .thenReturn("http://minio/report/index.html");

    Mockito.when(taskCaseResultParseService.parse(
            101L,
            Path.of("/tmp/task-101/playwright_framework/test-results/.playwright-results.json"),
            Path.of("/tmp/task-101/playwright_framework")))
            .thenReturn(new ParsedTaskResults(List.of(), List.of()));
}
```

- [ ] **Step 2: Run the focused task execution test and verify it fails**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=TaskExecutionServiceTest#shouldResolveReportAndResultsPathsFromWorkingDirectory test`
Expected: FAIL because the current implementation still resolves report and result paths from workspace root.

- [ ] **Step 3: Add helper methods for execution-relative paths**

```java
private Path resolveExecutionRelativePath(Path workspace, TestRepositoryEntity repository, String relativePath) {
    return resolveExecutionDirectory(workspace, repository).resolve(relativePath).normalize();
}
```

- [ ] **Step 4: Use execution-relative paths for report, result index, and artifact roots**

```java
Path executionDirectory = resolveExecutionDirectory(workspace, repository);
task.setReportUrl(reportArchiveService.archiveReport(executionDirectory, task.getId(), repository.getReportRelativePath()));
Path resultsIndex = executionDirectory.resolve(repository.getResultsIndexRelativePath());
ParsedTaskResults parsedTaskResults = taskCaseResultParseService.parse(task.getId(), resultsIndex, executionDirectory);
taskArtifactArchiveService.archiveArtifacts(
        task.getId(),
        executionDirectory,
        List.of(repository.getArtifactRootRelativePath(), repository.getReportRelativePath()),
        bindingTargets);
```

- [ ] **Step 5: Re-run the backend execution tests**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=TaskExecutionServiceTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java \
  playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java
git commit -m "feat: resolve report paths from repository working directory"
```

### Task 6: Verify Backward Compatibility And Document Usage

**Files:**
- Modify: `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`
- Modify: `playwright-platform-web/tests/unit/views/repository/RepositoryListView.test.ts`
- Modify: `README.md`

- [ ] **Step 1: Write compatibility tests for empty working directory**

```java
@Test
void shouldDefaultToWorkspaceRootWhenWorkingDirectoryIsBlank() {
    TestRepositoryEntity repository = new TestRepositoryEntity();
    repository.setWorkingDirectory("");
    Mockito.verify(executionService).installDependencies(
            Path.of("/tmp/task-101"),
            "npm install",
            Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"));
}
```

```ts
it('keeps root-directory projects simple', async () => {
  const wrapper = mount(RepositoryListView, { global: buildGlobals() })
  await wrapper.find('button').trigger('click')
  expect(wrapper.text()).toContain('为空则直接使用仓库根目录')
})
```

- [ ] **Step 2: Run the full targeted verification suite**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=RepositoryControllerTest,TaskExecutionServiceTest,EntitySchemaMappingTest test`
Expected: PASS

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- tests/unit/stores/repository.test.ts tests/unit/views/repository/RepositoryListView.test.ts`
Expected: PASS

- [ ] **Step 3: Update the README with the new repository configuration model**

```md
### Repository Configuration

- `Git 地址`: the GitHub repository URL
- `工作目录`: optional; leave blank for root projects, use `playwright_framework` for monorepo subprojects
- `安装命令`: write the command only, for example `npm install`
- `测试执行命令`: write the stable entrypoint only, for example `npm run test:e2e --`
- `测试目录`: relative to the working directory, for example `tests`
- `报告目录`: relative to the working directory, for example `reports/allure-report`
```

- [ ] **Step 4: Commit**

```bash
git add playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java \
  playwright-platform-web/tests/unit/views/repository/RepositoryListView.test.ts \
  README.md
git commit -m "docs: describe repository working directory configuration"
```

## Self-Review

- Spec coverage:
  - `workingDirectory` field: Task 1 and Task 4
  - runner `cwd + env`: Task 2 and Task 3
  - path semantics relative to working directory: Task 5
  - compatibility for root repositories: Task 6
- Placeholder scan:
  - No `TODO`, `TBD`, or implicit “write tests later” placeholders remain
  - Every task includes exact file paths, commands, and code snippets
- Type consistency:
  - Backend field name is consistently `workingDirectory`
  - Runner interface uses `Map<String, String> extraEnv`
  - Frontend repository field is also `workingDirectory`

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-11-repository-working-directory-and-platform-env.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
