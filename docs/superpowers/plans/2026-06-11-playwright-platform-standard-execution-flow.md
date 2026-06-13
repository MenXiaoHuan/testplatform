# Playwright Platform Standard Execution Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Define and implement a platform-wide execution flow that defaults to native Playwright commands while still producing stable task summaries, attachments, and HTML reports for the platform UI.

**Architecture:** The repository record remains the single source of truth for execution inputs such as working directory, install command, run command, test root, and report path. The backend resolves the execution directory, injects platform environment variables, runs installation, executes tests, generates an Allure HTML report from raw results, archives report and artifact directories, parses structured case results, and finally exposes task summaries and task report pages to the frontend.

**Tech Stack:** Vue 3, Pinia, Element Plus, Spring Boot, JPA, Playwright, Allure, Vitest, JUnit 5, Mockito

---

## Target Flow

The platform should standardize on the following runtime model for Playwright repositories:

1. Repository configuration captures only stable execution inputs.
2. The backend clones the repository and resolves `workingDirectory`.
3. The backend runs the install command inside that directory.
4. The backend runs the test command inside that directory.
5. The backend always injects `PLAYWRIGHT_PLATFORM_MODE=true`.
6. Playwright writes raw artifacts and structured results to stable relative paths.
7. The backend generates the HTML report from `.allure-results`.
8. The backend archives the HTML report and raw artifacts.
9. The backend parses `test-results/.playwright-results.json` into case-level task data.
10. The frontend shows task list summaries, report readiness, failed case attachments, and report entry links.

## Standard Repository Template

The default repository template for a Playwright project should be:

```text
工作目录: playwright_framework
安装命令: npm install && npx playwright install
测试执行命令: npx playwright test
测试目录: tests
报告目录: reports/allure-report
结果索引: test-results/.playwright-results.json
附件根目录: .playwright-artifacts
```

For a repository rooted directly at the Playwright project, `工作目录` should be left blank.

## Runtime Command Model

The backend should execute commands in this order inside the resolved execution directory:

```bash
npm install && npx playwright install
npx playwright test tests/codegen/login.spec.ts --project chromium
npx allure awesome ./.allure-results --output ./reports/allure-report --report-name "Allure 自动化测试报告" --report-language zh-CN --hide-labels package --hide-labels feature --hide-labels titlePath --hide-labels parentSuite --hide-labels subSuite --hide-labels host --hide-labels thread
```

If the repository still uses a wrapper script, the platform should continue to allow:

```bash
npm run test:e2e -- --project chromium --target tests/codegen/login.spec.ts
```

The command builder must decide between the two modes:

- Native Playwright mode: append the matched test path as a positional argument.
- Wrapper mode: preserve the existing `--target` behavior.

## Expected Output Directories

The platform should treat these paths as stable contracts relative to `workingDirectory`:

```text
.allure-results
.playwright-artifacts
test-results/.playwright-results.json
reports/allure-report
```

Their responsibilities are:

- `.allure-results`: raw Allure results emitted by `allure-playwright`
- `.playwright-artifacts`: screenshots, videos, traces, and other Playwright attachments
- `test-results/.playwright-results.json`: structured test result index used by the platform parser
- `reports/allure-report`: static HTML report uploaded for user access

## Failure Handling Rules

The platform should not treat report generation and case parsing as all-or-nothing work that only happens on successful test runs.

The target behavior is:

- If installation fails, mark task as failed and stop before test execution.
- If test execution fails, still attempt report generation when `.allure-results` exists.
- If the JSON result index exists, still parse and persist case-level results even when the test exit code is non-zero.
- If the report directory exists, still archive it even when some cases failed.
- If artifacts exist, still archive them and bind them to parsed case results where possible.

This preserves failed-case screenshots, trace files, and report links for debugging.

### Task 1: Document Frontend Defaults And Repository Guidance

**Files:**
- Modify: `README.md`
- Modify: `playwright-platform-web/src/types/repository.ts`
- Modify: `playwright-platform-web/src/views/repository/RepositoryListView.vue`
- Test: `playwright-platform-web/tests/unit/stores/repository.test.ts`
- Test: `playwright-platform-web/tests/unit/views/repository/RepositoryListView.test.ts`

- [ ] **Step 1: Write failing frontend tests for the new default model**

```ts
expect(createRepositoryForm()).toMatchObject({
  workingDirectory: '',
  installCommand: 'npm install && npx playwright install',
  runCommandTemplate: 'npx playwright test',
  testRoot: 'tests',
  reportRelativePath: 'reports/allure-report',
})
```

- [ ] **Step 2: Run the focused frontend tests and verify they fail**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- tests/unit/stores/repository.test.ts tests/unit/views/repository/RepositoryListView.test.ts`
Expected: FAIL because the old defaults still point to `npm run test:e2e --`.

- [ ] **Step 3: Update repository defaults to the standard Playwright template**

```ts
export const createRepositoryForm = (): RepositoryForm => ({
  name: '',
  gitUrl: '',
  defaultBranch: 'main',
  workingDirectory: '',
  installCommand: 'npm install && npx playwright install',
  runCommandTemplate: 'npx playwright test',
  testRoot: 'tests',
  reportRelativePath: 'reports/allure-report',
  enabled: true,
})
```

- [ ] **Step 4: Update field help text so users understand what the platform auto-handles**

```ts
const repositoryFieldHelp = {
  workingDirectory: '为空时直接在仓库根目录执行；多模块仓库可填写子目录，例如 playwright_framework。',
  installCommand: '默认使用 npm install && npx playwright install；只填写命令本体，不要手写 cd。',
  runCommandTemplate: '默认使用 npx playwright test。原生命令会自动拼接测试路径与 --project；如仓库使用包装脚本，也可改成 npm run test:e2e --。',
  testRoot: '相对工作目录填写测试目录，例如 tests。',
  reportRelativePath: '相对工作目录填写报告目录，例如 reports/allure-report。',
}
```

- [ ] **Step 5: Update README so the documented flow matches the platform defaults**

```md
- `工作目录`: optional; leave blank for root projects, use values like `playwright_framework` for monorepo subprojects
- `安装命令`: default to `npm install && npx playwright install`
- `测试执行命令`: default to `npx playwright test`; wrapper scripts such as `npm run test:e2e --` remain supported
- `测试目录`: relative to the working directory, for example `tests`
- `报告目录`: relative to the working directory, for example `reports/allure-report`
```

- [ ] **Step 6: Re-run the focused frontend tests and verify they pass**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- tests/unit/stores/repository.test.ts tests/unit/views/repository/RepositoryListView.test.ts`
Expected: PASS

### Task 2: Add Backend Dual-Mode Command Building

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskCommandBuilderImpl.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`

- [ ] **Step 1: Write failing backend tests for native Playwright mode**

```java
assertThat(command).isEqualTo("npx playwright test tests/codegen/login.spec.ts --project chromium");
```

- [ ] **Step 2: Write failing backend tests for wrapper compatibility mode**

```java
assertThat(command).isEqualTo("npm run test:e2e -- --project chromium --target tests/codegen/login.spec.ts");
```

- [ ] **Step 3: Run the focused backend test and verify it fails**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=TaskExecutionServiceTest test`
Expected: FAIL because the command builder currently always appends `--target`.

- [ ] **Step 4: Teach the command builder to detect native Playwright commands**

```java
private boolean isNativePlaywrightCommand(String command) {
    if (command == null) {
        return false;
    }
    String normalized = command.trim().replaceAll("\\s+", " ").toLowerCase();
    return normalized.startsWith("npx playwright test");
}
```

- [ ] **Step 5: Append the match value as a positional path in native mode and preserve wrapper mode**

```java
if (matchValue != null && !matchValue.isBlank()) {
    String normalizedRoot = repository.getTestRoot() == null
            ? ""
            : repository.getTestRoot().replaceAll("/+$", "");
    String normalizedMatch = matchValue.replaceAll("^/+", "");
    String resolvedTarget = normalizedRoot.isBlank()
            ? normalizedMatch
            : normalizedRoot + "/" + normalizedMatch;

    if (isNativePlaywrightCommand(baseCommand)) {
        builder.append(" ").append(resolvedTarget);
    } else {
        builder.append(" --target ").append(resolvedTarget);
    }
}
```

- [ ] **Step 6: Re-run the focused backend test and verify it passes**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=TaskExecutionServiceTest test`
Expected: PASS

### Task 3: Add Explicit Report Generation After Test Execution

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerExecutionService.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerExecutionServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`

- [ ] **Step 1: Write a failing test that expects report generation after test execution**

```java
Mockito.verify(executionService).generateReport(
        Path.of("/tmp/task-101/playwright_framework"),
        "npx allure awesome ./.allure-results --output ./reports/allure-report --report-name \"Allure 自动化测试报告\" --report-language zh-CN --hide-labels package --hide-labels feature --hide-labels titlePath --hide-labels parentSuite --hide-labels subSuite --hide-labels host --hide-labels thread",
        Map.of("PLAYWRIGHT_PLATFORM_MODE", "true"));
```

- [ ] **Step 2: Run the backend test and verify it fails**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=TaskExecutionServiceTest test`
Expected: FAIL because `RunnerExecutionService` does not yet expose report generation.

- [ ] **Step 3: Extend the runner interface with a dedicated report generation method**

```java
public interface RunnerExecutionService {
    int installDependencies(Path workingDirectory, String installCommand, Map<String, String> extraEnv);
    int runTests(Path workingDirectory, String runCommand, Map<String, String> extraEnv);
    int generateReport(Path workingDirectory, String reportCommand, Map<String, String> extraEnv);
}
```

- [ ] **Step 4: Implement the report method by reusing the existing shell execution path**

```java
@Override
public int generateReport(Path workingDirectory, String reportCommand, Map<String, String> extraEnv) {
    return runCommand(workingDirectory, reportCommand, extraEnv);
}
```

- [ ] **Step 5: Update task execution so test completion triggers HTML report generation**

```java
int reportStatus = executionService.generateReport(
        executionDirectory,
        "npx allure awesome ./.allure-results --output ./reports/allure-report --report-name \"Allure 自动化测试报告\" --report-language zh-CN --hide-labels package --hide-labels feature --hide-labels titlePath --hide-labels parentSuite --hide-labels subSuite --hide-labels host --hide-labels thread",
        platformEnv);
```

- [ ] **Step 6: Re-run the backend test and verify it passes**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=TaskExecutionServiceTest test`
Expected: PASS

### Task 4: Keep Reports And Artifacts Available On Failed Test Runs

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`

- [ ] **Step 1: Write a failing test for a non-zero test exit code with existing result files**

```java
Mockito.when(executionService.runTests(...)).thenReturn(1);
Mockito.when(executionService.generateReport(...)).thenReturn(0);
Mockito.when(taskCaseResultParseService.parse(...)).thenReturn(new ParsedTaskResults(List.of(), List.of()));
```

- [ ] **Step 2: Assert that report archiving and case parsing still happen when files exist**

```java
Mockito.verify(archiveService).archiveReport(...);
Mockito.verify(taskCaseResultParseService).parse(...);
Mockito.verify(taskArtifactArchiveService).archiveArtifacts(...);
```

- [ ] **Step 3: Run the backend test and verify it fails**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=TaskExecutionServiceTest test`
Expected: FAIL because the current code only archives and parses after a fully successful run.

- [ ] **Step 4: Change task execution to treat report parsing and archiving as best-effort post-processing**

```java
if (installStatus == 0) {
    runStatus = runnerExecutionService.runTests(executionDirectory, task.getResolvedRunCommand(), platformEnv);
    int reportStatus = runnerExecutionService.generateReport(executionDirectory, reportCommand, platformEnv);
    archiveAndParseOutputs(task, repository, executionDirectory, reportStatus);
}
```

- [ ] **Step 5: Keep task status tied to install and test status, not report archive side effects**

```java
task.setStatus(installStatus == 0 && runStatus == 0 ? "SUCCESS" : "FAILED");
```

- [ ] **Step 6: Re-run the backend test and verify it passes**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=TaskExecutionServiceTest test`
Expected: PASS

### Task 5: Verify End-To-End Platform Flow

**Files:**
- Verify: `playwright-platform-web/src/types/repository.ts`
- Verify: `playwright-platform-web/src/views/repository/RepositoryListView.vue`
- Verify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskCommandBuilderImpl.java`
- Verify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
- Verify: `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerExecutionServiceImpl.java`
- Verify: `README.md`

- [ ] **Step 1: Run frontend regression for repository defaults and copy**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- tests/unit/stores/repository.test.ts tests/unit/views/repository/RepositoryListView.test.ts`
Expected: PASS

- [ ] **Step 2: Run backend regression for command building and task execution flow**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=TaskExecutionServiceTest test`
Expected: PASS

- [ ] **Step 3: Validate the final runtime flow against a sample repository**

```text
工作目录: playwright_framework
安装命令: npm install && npx playwright install
测试执行命令: npx playwright test
测试目录: tests
场景筛选值: codegen/login.spec.ts
Playwright project: chromium
```

- [ ] **Step 4: Confirm the generated runtime command is correct**

```bash
cd playwright_framework
npx playwright test tests/codegen/login.spec.ts --project chromium
```

- [ ] **Step 5: Confirm the expected output set is complete after execution**

```text
.allure-results
.playwright-artifacts
test-results/.playwright-results.json
reports/allure-report
```

- [ ] **Step 6: Confirm the platform UI behavior matches the stored outputs**

```text
任务列表: 有通过/失败/跳过摘要，有报告就绪状态
任务报告页: 可打开 HTML 报告，可查看失败用例，可打开 trace/video/screenshot
失败任务: 仍保留可调试的报告与附件
```

## Complete Runtime Walkthrough

This section is the canonical operational walkthrough for the platform after the above work lands:

1. User creates a repository record.
2. User fills `工作目录` with `playwright_framework` for monorepos, or leaves it blank for root projects.
3. User keeps `安装命令` as `npm install && npx playwright install`.
4. User keeps `测试执行命令` as `npx playwright test`.
5. User keeps `测试目录` as `tests`.
6. User keeps `报告目录` as `reports/allure-report`.
7. User creates a scene and sets `筛选值` such as `codegen/login.spec.ts`.
8. User sets `Playwright project` such as `chromium`.
9. Platform clones the repository into an isolated task workspace.
10. Platform resolves the real execution directory from `workingDirectory`.
11. Platform injects `PLAYWRIGHT_PLATFORM_MODE=true`.
12. Platform installs dependencies and Playwright browsers.
13. Platform builds the run command from repository template plus scene parameters.
14. Platform executes the test run.
15. Playwright emits `.allure-results`, `.playwright-artifacts`, and `test-results/.playwright-results.json`.
16. Platform executes the Allure HTML generation command.
17. Platform uploads `reports/allure-report`.
18. Platform parses the JSON result index into case-level data.
19. Platform archives raw artifacts and binds them to case results.
20. Frontend shows task summary, report readiness, failed-case attachments, and report entry links.

## Open Decisions To Confirm During Implementation

- Whether the Allure generation command should be hard-coded in `TaskServiceImpl` or moved into a dedicated report command builder.
- Whether report generation should be attempted only when `.allure-results` exists or always invoked and handled by exit code.
- Whether `reportReady` should mean “HTML report uploaded successfully” only, or “any report/debug artifact is available”.
- Whether the repository form should eventually expose `resultsIndexRelativePath` and `artifactRootRelativePath`, or keep them as backend defaults.

