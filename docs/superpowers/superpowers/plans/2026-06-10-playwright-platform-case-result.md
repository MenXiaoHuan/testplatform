# Playwright Platform Case Result Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于 Playwright 原生产物实现 `case_result` 解析、附件与用例关联、后端查询接口和真实烟雾验证闭环。

**Architecture:** 本次实现分成两段：先在 `playwright_framework` 中建立“平台运行契约”，确保产出可解析的 JSON 结果索引并保留原始附件目录；再在 `playwright-platform-server` 中新增解析服务、持久化与查询接口，把 `case_result` 和 `artifact.case_result_id` 真正打通。附件上传仍复用现有 MinIO 能力，但归档逻辑从“任务级扫描”升级为“解析驱动 + 降级到未归属附件”。

**Tech Stack:** Spring Boot 3.5, Spring MVC, Spring Data JPA, Flyway, MySQL, JUnit 5, Mockito, Playwright, Node.js, Allure.

---

## File Map

- Modify: `playwright_framework/playwright.config.ts`
- Modify: `playwright_framework/scripts/lib/run-e2e-core.cjs`
- Modify: `playwright-platform-server/src/main/resources/db/migration/V1__init_platform_tables.sql`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/model/TestRepositoryEntity.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryServiceImpl.java`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/repository/RepositoryControllerTest.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/dto/CaseResultResponse.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/parser/ParsedCaseResult.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/parser/ParsedArtifactBinding.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/parser/ParsedTaskResults.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskCaseResultParseService.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskCaseResultParseServiceImpl.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskCaseResultPersistenceService.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskCaseResultPersistenceServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/model/CaseResultJpaRepository.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/model/ArtifactJpaRepository.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskArtifactArchiveService.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskArtifactArchiveServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskService.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/controller/TaskController.java`
- Create: `playwright-platform-server/src/test/java/com/example/platform/task/TaskCaseResultParseServiceTest.java`
- Create: `playwright-platform-server/src/test/resources/playwright-results/minimal-results.json`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/task/TaskControllerTest.java`

## Task 1: 建立 Playwright 平台运行契约

**Files:**
- Modify: `playwright_framework/playwright.config.ts`
- Modify: `playwright_framework/scripts/lib/run-e2e-core.cjs`

- [ ] **Step 1: 先补失败测试或最小校验脚本**

Create a temporary verification command by checking current output behavior:

```bash
cd /Users/bytedance/test_platform/playwright_framework
node - <<'EOF'
const config = require('./playwright.config.ts')
console.log(Array.isArray(config.default.reporter))
EOF
```

Expected: 当前无法从配置和脚本保证 `test-results/.playwright-results.json` 输出与 `.playwright-artifacts` 保留。

- [ ] **Step 2: 手工确认当前缺口**

Run:

```bash
cd /Users/bytedance/test_platform/playwright_framework
grep -n "reporter" playwright.config.ts
grep -n "cleanupRuntimeArtifacts" scripts/lib/run-e2e-core.cjs
```

Expected:
- `playwright.config.ts` 只包含 `list` 和 `allure-playwright`
- `run-e2e-core.cjs` 成功后仍会删除 `.playwright-artifacts`

- [ ] **Step 3: 写最小实现，新增平台模式约定**

Update `playwright_framework/playwright.config.ts`:

```ts
const platformMode = process.env.PLAYWRIGHT_PLATFORM_MODE === 'true';

const reporters = [
  ['list'],
  [
    'allure-playwright',
    {
      resultsDir: './.allure-results',
      detail: true,
      suiteTitle: true,
      categories: [
        // keep existing categories unchanged
      ],
      environmentInfo,
    },
  ],
];

if (platformMode) {
  reporters.push([
    'json',
    {
      outputFile: './test-results/.playwright-results.json',
    },
  ]);
}

export default defineConfig({
  // ...
  reporter: reporters,
  outputDir: './.playwright-artifacts',
  // ...
});
```

Update `playwright_framework/scripts/lib/run-e2e-core.cjs`:

```js
function shouldKeepPlatformArtifacts(env = process.env) {
  return env.PLAYWRIGHT_PLATFORM_MODE === 'true';
}

function runE2E(deps = {}) {
  const argv = deps.argv ?? [];
  const env = deps.env ?? process.env;
  // ...

  if ((testResult.status ?? 1) === 0 && (reportResult.status ?? 1) === 0 && !shouldKeepPlatformArtifacts(env)) {
    cleanupArtifacts();
  }
  // ...
}

module.exports = {
  REPORT_ARGS,
  REPORT_HIDDEN_LABELS,
  cleanupPaths,
  cleanupRuntimeArtifacts,
  createOpenReport,
  createRunSync,
  patchGeneratedReportShell,
  resolveExitCode,
  runE2E,
  shouldKeepPlatformArtifacts,
};
```

- [ ] **Step 4: 跑最小校验确认契约生效**

Run:

```bash
cd /Users/bytedance/test_platform/playwright_framework
PLAYWRIGHT_PLATFORM_MODE=true node - <<'EOF'
const { shouldKeepPlatformArtifacts } = require('./scripts/lib/run-e2e-core.cjs')
console.log(shouldKeepPlatformArtifacts({ PLAYWRIGHT_PLATFORM_MODE: 'true' }))
EOF
```

Expected: 输出 `true`。

- [ ] **Step 5: 提交框架契约改动**

Run:

```bash
git -C /Users/bytedance/test_platform/playwright_framework add playwright.config.ts scripts/lib/run-e2e-core.cjs
git -C /Users/bytedance/test_platform/playwright_framework commit -m "feat: preserve playwright raw results for platform mode"
```

Expected: 生成只包含平台模式契约的提交。

## Task 2: 扩展仓库配置与数据库字段

**Files:**
- Modify: `playwright-platform-server/src/main/resources/db/migration/V1__init_platform_tables.sql`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/model/TestRepositoryEntity.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryServiceImpl.java`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/repository/RepositoryControllerTest.java`

- [ ] **Step 1: 先补仓库接口失败测试**

Update `RepositoryControllerTest.java` by extending the payloads and assertions with:

```json
"resultsIndexRelativePath": "test-results/.playwright-results.json",
"artifactRootRelativePath": ".playwright-artifacts"
```

and verify response contains:

```java
.andExpect(jsonPath("$.resultsIndexRelativePath").value("test-results/.playwright-results.json"))
.andExpect(jsonPath("$.artifactRootRelativePath").value(".playwright-artifacts"))
```

- [ ] **Step 2: 跑控制器测试确认当前字段不存在**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=RepositoryControllerTest test
```

Expected: FAIL，提示 JSON 字段不存在或序列化/反序列化不包含新增字段。

- [ ] **Step 3: 写最小数据库与实体实现**

Update `V1__init_platform_tables.sql` by adding columns to `test_repository`:

```sql
results_index_relative_path varchar(256) not null default 'test-results/.playwright-results.json',
artifact_root_relative_path varchar(256) not null default '.playwright-artifacts',
```

Update `TestRepositoryEntity.java`:

```java
@Column(name = "results_index_relative_path", nullable = false, length = 256)
private String resultsIndexRelativePath = "test-results/.playwright-results.json";

@Column(name = "artifact_root_relative_path", nullable = false, length = 256)
private String artifactRootRelativePath = ".playwright-artifacts";
```

Add getters/setters:

```java
public String getResultsIndexRelativePath() { return resultsIndexRelativePath; }
public void setResultsIndexRelativePath(String resultsIndexRelativePath) { this.resultsIndexRelativePath = resultsIndexRelativePath; }
public String getArtifactRootRelativePath() { return artifactRootRelativePath; }
public void setArtifactRootRelativePath(String artifactRootRelativePath) { this.artifactRootRelativePath = artifactRootRelativePath; }
```

Update `RepositoryServiceImpl.java`:

```java
existing.setResultsIndexRelativePath(entity.getResultsIndexRelativePath());
existing.setArtifactRootRelativePath(entity.getArtifactRootRelativePath());
```

- [ ] **Step 4: 重新跑控制器测试**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=RepositoryControllerTest test
```

Expected: PASS。

- [ ] **Step 5: 提交仓库配置扩展**

Run:

```bash
git -C /Users/bytedance/test_platform/playwright-platform-server add src/main/resources/db/migration/V1__init_platform_tables.sql src/main/java/com/example/platform/repository/model/TestRepositoryEntity.java src/main/java/com/example/platform/repository/service/RepositoryServiceImpl.java src/test/java/com/example/platform/repository/RepositoryControllerTest.java
git -C /Users/bytedance/test_platform/playwright-platform-server commit -m "feat: add raw result paths to repository config"
```

Expected: 生成仓库配置字段扩展提交。

## Task 3: 实现 Playwright 结果解析服务

**Files:**
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/parser/ParsedCaseResult.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/parser/ParsedArtifactBinding.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/parser/ParsedTaskResults.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskCaseResultParseService.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskCaseResultParseServiceImpl.java`
- Create: `playwright-platform-server/src/test/java/com/example/platform/task/TaskCaseResultParseServiceTest.java`
- Create: `playwright-platform-server/src/test/resources/playwright-results/minimal-results.json`

- [ ] **Step 1: 先写解析服务失败测试**

Create `minimal-results.json`:

```json
{
  "suites": [
    {
      "title": "checkout",
      "specs": [
        {
          "title": "should pay successfully",
          "tests": [
            {
              "projectName": "chromium",
              "results": [
                {
                  "status": "passed",
                  "duration": 321,
                  "attachments": [
                    {
                      "name": "trace",
                      "contentType": "application/zip",
                      "path": ".playwright-artifacts/checkout/trace.zip"
                    },
                    {
                      "name": "screenshot",
                      "contentType": "image/png",
                      "path": ".playwright-artifacts/checkout/failure.png"
                    }
                  ]
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

Create `TaskCaseResultParseServiceTest.java` with assertions:

```java
@Test
void shouldParseCaseResultsAndArtifactBindings() {
    TaskCaseResultParseService service = new TaskCaseResultParseServiceImpl(new ObjectMapper());

    ParsedTaskResults parsed = service.parse(
            101L,
            Path.of("src/test/resources/playwright-results/minimal-results.json"),
            Path.of("/tmp/task-101"));

    assertThat(parsed.caseResults()).hasSize(1);
    assertThat(parsed.caseResults().getFirst().taskId()).isEqualTo(101L);
    assertThat(parsed.caseResults().getFirst().status()).isEqualTo("PASSED");
    assertThat(parsed.caseResults().getFirst().projectName()).isEqualTo("chromium");
    assertThat(parsed.artifactBindings()).hasSize(2);
    assertThat(parsed.artifactBindings())
            .extracting(ParsedArtifactBinding::artifactType)
            .containsExactlyInAnyOrder("TRACE", "SCREENSHOT");
}
```

- [ ] **Step 2: 跑测试确认解析服务不存在**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=TaskCaseResultParseServiceTest test
```

Expected: FAIL，提示解析服务或中间模型不存在。

- [ ] **Step 3: 写最小解析模型与实现**

Create `ParsedCaseResult.java`:

```java
package com.example.platform.task.parser;

public record ParsedCaseResult(
        Long taskId,
        String historyId,
        String fullName,
        String suiteName,
        String storyName,
        String status,
        Long durationMs,
        String projectName) {
}
```

Create `ParsedArtifactBinding.java`:

```java
package com.example.platform.task.parser;

public record ParsedArtifactBinding(
        String relativePath,
        String artifactType,
        String caseHistoryId) {
}
```

Create `ParsedTaskResults.java`:

```java
package com.example.platform.task.parser;

import java.util.List;

public record ParsedTaskResults(
        List<ParsedCaseResult> caseResults,
        List<ParsedArtifactBinding> artifactBindings) {
}
```

Create `TaskCaseResultParseService.java`:

```java
package com.example.platform.task.service;

import com.example.platform.task.parser.ParsedTaskResults;
import java.nio.file.Path;

public interface TaskCaseResultParseService {
    ParsedTaskResults parse(Long taskId, Path resultsIndexFile, Path workspaceRoot);
}
```

Create `TaskCaseResultParseServiceImpl.java` with minimal parsing:

```java
package com.example.platform.task.service;

import com.example.platform.task.parser.ParsedArtifactBinding;
import com.example.platform.task.parser.ParsedCaseResult;
import com.example.platform.task.parser.ParsedTaskResults;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TaskCaseResultParseServiceImpl implements TaskCaseResultParseService {
    private final ObjectMapper objectMapper;

    public TaskCaseResultParseServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ParsedTaskResults parse(Long taskId, Path resultsIndexFile, Path workspaceRoot) {
        try {
            JsonNode root = objectMapper.readTree(resultsIndexFile.toFile());
            List<ParsedCaseResult> cases = new ArrayList<>();
            List<ParsedArtifactBinding> bindings = new ArrayList<>();

            for (JsonNode suite : root.path("suites")) {
                String suiteName = suite.path("title").asText();
                for (JsonNode spec : suite.path("specs")) {
                    String storyName = spec.path("title").asText();
                    for (JsonNode test : spec.path("tests")) {
                        String projectName = test.path("projectName").asText();
                        JsonNode result = test.path("results").get(test.path("results").size() - 1);
                        String historyId = projectName + "::" + suiteName + "::" + storyName;
                        cases.add(new ParsedCaseResult(
                                taskId,
                                historyId,
                                suiteName + " :: " + storyName,
                                suiteName,
                                storyName,
                                mapStatus(result.path("status").asText()),
                                result.path("duration").asLong(),
                                projectName));

                        for (JsonNode attachment : result.path("attachments")) {
                            bindings.add(new ParsedArtifactBinding(
                                    workspaceRoot.relativize(Path.of(attachment.path("path").asText())).toString().replace('\\', '/'),
                                    mapArtifactType(attachment.path("name").asText(), attachment.path("contentType").asText(), attachment.path("path").asText()),
                                    historyId));
                        }
                    }
                }
            }
            return new ParsedTaskResults(cases, bindings);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse playwright results", exception);
        }
    }

    private String mapStatus(String status) {
        return switch (status) {
            case "passed" -> "PASSED";
            case "failed" -> "FAILED";
            case "timedOut" -> "TIMEOUT";
            default -> "SKIPPED";
        };
    }

    private String mapArtifactType(String name, String contentType, String path) {
        String lowerName = name == null ? "" : name.toLowerCase();
        String lowerType = contentType == null ? "" : contentType.toLowerCase();
        String lowerPath = path == null ? "" : path.toLowerCase();
        if (lowerName.contains("trace") || lowerPath.endsWith(".zip")) {
            return "TRACE";
        }
        if (lowerType.startsWith("video/") || lowerPath.endsWith(".webm")) {
            return "VIDEO";
        }
        if (lowerType.startsWith("image/") || lowerPath.endsWith(".png")) {
            return "SCREENSHOT";
        }
        return "REPORT_FILE";
    }
}
```

- [ ] **Step 4: 跑解析服务测试**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=TaskCaseResultParseServiceTest test
```

Expected: PASS。

- [ ] **Step 5: 提交解析服务**

Run:

```bash
git -C /Users/bytedance/test_platform/playwright-platform-server add src/main/java/com/example/platform/task/parser src/main/java/com/example/platform/task/service/TaskCaseResultParseService.java src/main/java/com/example/platform/task/service/TaskCaseResultParseServiceImpl.java src/test/java/com/example/platform/task/TaskCaseResultParseServiceTest.java src/test/resources/playwright-results/minimal-results.json
git -C /Users/bytedance/test_platform/playwright-platform-server commit -m "feat: parse playwright case results"
```

Expected: 生成解析服务提交。

## Task 4: 实现 case_result 持久化与附件归属

**Files:**
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskCaseResultPersistenceService.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskCaseResultPersistenceServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/model/CaseResultJpaRepository.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/model/ArtifactJpaRepository.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskArtifactArchiveService.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskArtifactArchiveServiceImpl.java`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`

- [ ] **Step 1: 先补执行链路失败测试**

Update `TaskExecutionServiceTest.java` to add:

```java
@Test
void shouldPersistCaseResultsAndBindArtifactsToCaseResults() {
    // mock parse service -> one ParsedCaseResult + one TRACE binding
    // mock persistence service -> historyId to caseResultId map
    // verify archive service receives binding with real caseResultId
}
```

Minimum verification:

```java
Mockito.verify(taskArtifactArchiveService).archiveArtifacts(
        Mockito.eq(101L),
        Mockito.eq(Path.of("/tmp/task-101")),
        Mockito.anyList(),
        Mockito.anyMap());
```

Also add:

```java
@Test
void shouldMarkTaskFailedWhenCaseResultParsingFails() {
    // parse service throws IllegalStateException("results index missing")
    // assert result status FAILED and logUrl contains the message
}
```

- [ ] **Step 2: 跑测试确认链路尚未接入**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=TaskExecutionServiceTest test
```

Expected: FAIL，提示新服务未接入或方法不存在。

- [ ] **Step 3: 写最小持久化与归档实现**

Create `TaskCaseResultPersistenceService.java`:

```java
package com.example.platform.task.service;

import com.example.platform.task.parser.ParsedCaseResult;
import java.util.List;
import java.util.Map;

public interface TaskCaseResultPersistenceService {
    Map<String, Long> persist(List<ParsedCaseResult> parsedCaseResults);
}
```

Create `TaskCaseResultPersistenceServiceImpl.java`:

```java
package com.example.platform.task.service;

import com.example.platform.task.model.CaseResultEntity;
import com.example.platform.task.model.CaseResultJpaRepository;
import com.example.platform.task.parser.ParsedCaseResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TaskCaseResultPersistenceServiceImpl implements TaskCaseResultPersistenceService {
    private final CaseResultJpaRepository repository;

    public TaskCaseResultPersistenceServiceImpl(CaseResultJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<String, Long> persist(List<ParsedCaseResult> parsedCaseResults) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (ParsedCaseResult parsed : parsedCaseResults) {
            CaseResultEntity entity = new CaseResultEntity();
            entity.setTaskId(parsed.taskId());
            entity.setHistoryId(parsed.historyId());
            entity.setFullName(parsed.fullName());
            entity.setSuiteName(parsed.suiteName());
            entity.setStoryName(parsed.storyName());
            entity.setStatus(parsed.status());
            entity.setDurationMs(parsed.durationMs());
            entity.setProjectName(parsed.projectName());
            CaseResultEntity saved = repository.save(entity);
            result.put(parsed.historyId(), saved.getId());
        }
        return result;
    }
}
```

Update `ArtifactJpaRepository.java`:

```java
List<ArtifactEntity> findAllByCaseResultIdOrderByIdAsc(Long caseResultId);
```

Update `TaskArtifactArchiveService.java`:

```java
List<ArtifactEntity> archiveArtifacts(
        Long taskId,
        Path workspace,
        List<String> reportRelativeRoots,
        Map<String, ArtifactBindingTarget> bindingTargets);
```

Add helper record `ArtifactBindingTarget` inside the same file:

```java
record ArtifactBindingTarget(Long caseResultId, String artifactType) {}
```

Update `TaskArtifactArchiveServiceImpl.java` so that:

```java
artifact.setCaseResultId(bindingTarget == null ? null : bindingTarget.caseResultId());
artifact.setArtifactType(bindingTarget == null ? "REPORT_FILE" : bindingTarget.artifactType());
```

and write unbound artifact object keys under:

```java
"runs/" + taskId + "/artifacts/unassigned/" + relativePath
```

- [ ] **Step 4: 重新跑执行链路测试**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=TaskExecutionServiceTest test
```

Expected: PASS。

- [ ] **Step 5: 提交持久化与归档关联**

Run:

```bash
git -C /Users/bytedance/test_platform/playwright-platform-server add src/main/java/com/example/platform/task/service/TaskCaseResultPersistenceService.java src/main/java/com/example/platform/task/service/TaskCaseResultPersistenceServiceImpl.java src/main/java/com/example/platform/task/model/CaseResultJpaRepository.java src/main/java/com/example/platform/task/model/ArtifactJpaRepository.java src/main/java/com/example/platform/task/service/TaskArtifactArchiveService.java src/main/java/com/example/platform/task/service/TaskArtifactArchiveServiceImpl.java src/test/java/com/example/platform/task/TaskExecutionServiceTest.java
git -C /Users/bytedance/test_platform/playwright-platform-server commit -m "feat: persist case results and bind artifacts"
```

Expected: 生成结果持久化与附件绑定提交。

## Task 5: 接入任务执行链路与新增查询接口

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskService.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/controller/TaskController.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/dto/CaseResultResponse.java`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/task/TaskControllerTest.java`

- [ ] **Step 1: 先写控制器失败测试**

Update `TaskControllerTest.java` to add:

```java
@Test
void shouldListTaskCaseResults() throws Exception {
    CaseResultEntity entity = new CaseResultEntity();
    entity.setId(1L);
    entity.setTaskId(101L);
    entity.setHistoryId("chromium::checkout::should pay successfully");
    entity.setFullName("checkout :: should pay successfully");
    entity.setSuiteName("checkout");
    entity.setStoryName("should pay successfully");
    entity.setStatus("PASSED");
    entity.setDurationMs(321L);
    entity.setProjectName("chromium");

    Mockito.when(taskService.listCaseResults(101L)).thenReturn(List.of(entity));
    Mockito.when(taskService.listArtifactsByCaseResult(1L)).thenReturn(List.of(new ArtifactEntity()));

    mockMvc.perform(get("/api/tasks/101/cases"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].fullName").value("checkout :: should pay successfully"))
            .andExpect(jsonPath("$[0].artifactCount").value(1));
}
```

and:

```java
@Test
void shouldListArtifactsByCaseResult() throws Exception {
    ArtifactEntity artifact = new ArtifactEntity();
    artifact.setId(11L);
    artifact.setCaseResultId(1L);
    artifact.setArtifactType("TRACE");
    artifact.setObjectKey("runs/101/artifacts/1/trace.zip");
    artifact.setUrl("http://minio/presigned/trace");

    Mockito.when(taskService.listArtifactsByCaseResult(1L)).thenReturn(List.of(artifact));

    mockMvc.perform(get("/api/tasks/101/cases/1/artifacts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].artifactType").value("TRACE"));
}
```

- [ ] **Step 2: 跑控制器测试确认接口尚不存在**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=TaskControllerTest test
```

Expected: FAIL。

- [ ] **Step 3: 写最小服务与控制器实现**

Create `CaseResultResponse.java`:

```java
package com.example.platform.task.dto;

import com.example.platform.task.model.CaseResultEntity;

public record CaseResultResponse(
        Long id,
        Long taskId,
        String historyId,
        String fullName,
        String suiteName,
        String storyName,
        String status,
        Long durationMs,
        String projectName,
        int artifactCount) {
    public static CaseResultResponse from(CaseResultEntity entity, int artifactCount) {
        return new CaseResultResponse(
                entity.getId(),
                entity.getTaskId(),
                entity.getHistoryId(),
                entity.getFullName(),
                entity.getSuiteName(),
                entity.getStoryName(),
                entity.getStatus(),
                entity.getDurationMs(),
                entity.getProjectName(),
                artifactCount);
    }
}
```

Update `TaskService.java`:

```java
List<CaseResultEntity> listCaseResults(Long taskId);
List<ArtifactEntity> listArtifactsByCaseResult(Long caseResultId);
```

Update `TaskServiceImpl.java`:

```java
@Override
public List<CaseResultEntity> listCaseResults(Long taskId) {
    return caseResultRepository.findAllByTaskIdOrderByIdAsc(taskId);
}

@Override
public List<ArtifactEntity> listArtifactsByCaseResult(Long caseResultId) {
    return artifactRepository.findAllByCaseResultIdOrderByIdAsc(caseResultId).stream()
            .map(this::withAccessibleArtifactUrl)
            .collect(Collectors.toList());
}
```

Also extend `createAndRun()` to:

```java
Path resultsIndex = workspace.resolve(repository.getResultsIndexRelativePath());
ParsedTaskResults parsed = taskCaseResultParseService.parse(task.getId(), resultsIndex, workspace);
Map<String, Long> caseResultIds = taskCaseResultPersistenceService.persist(parsed.caseResults());
Map<String, ArtifactBindingTarget> targets = parsed.artifactBindings().stream()
        .collect(Collectors.toMap(
                ParsedArtifactBinding::relativePath,
                binding -> new ArtifactBindingTarget(caseResultIds.get(binding.caseHistoryId()), binding.artifactType())));
taskArtifactArchiveService.archiveArtifacts(
        task.getId(),
        workspace,
        List.of(repository.getArtifactRootRelativePath(), repository.getReportRelativePath()),
        targets);
```

Update `TaskController.java`:

```java
@GetMapping("/api/tasks/{taskId}/cases")
public List<CaseResultResponse> listTaskCases(@PathVariable Long taskId) {
    return taskService.listCaseResults(taskId).stream()
            .map(caseResult -> CaseResultResponse.from(
                    caseResult,
                    taskService.listArtifactsByCaseResult(caseResult.getId()).size()))
            .toList();
}

@GetMapping("/api/tasks/{taskId}/cases/{caseResultId}/artifacts")
public List<ArtifactEntity> listCaseArtifacts(@PathVariable Long taskId, @PathVariable Long caseResultId) {
    return taskService.listArtifactsByCaseResult(caseResultId);
}
```

- [ ] **Step 4: 跑控制器测试**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=TaskControllerTest test
```

Expected: PASS。

- [ ] **Step 5: 提交接口改动**

Run:

```bash
git -C /Users/bytedance/test_platform/playwright-platform-server add src/main/java/com/example/platform/task/service/TaskService.java src/main/java/com/example/platform/task/service/TaskServiceImpl.java src/main/java/com/example/platform/task/controller/TaskController.java src/main/java/com/example/platform/task/dto/CaseResultResponse.java src/test/java/com/example/platform/task/TaskControllerTest.java
git -C /Users/bytedance/test_platform/playwright-platform-server commit -m "feat: expose task case results and case artifacts"
```

Expected: 生成接口提交。

## Task 6: 完成全量验证与烟雾测试

**Files:**
- Verify: `playwright_framework/playwright.config.ts`
- Verify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
- Verify: `playwright-platform-server/src/main/java/com/example/platform/task/controller/TaskController.java`

- [ ] **Step 1: 跑后端相关测试**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=RepositoryControllerTest,TaskCaseResultParseServiceTest,TaskExecutionServiceTest,TaskControllerTest test
```

Expected: PASS。

- [ ] **Step 2: 跑后端全量测试**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn test
```

Expected: PASS。

- [ ] **Step 3: 启动或复用本地后端实例**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn spring-boot:run
```

Expected: 服务监听 `8080`。

- [ ] **Step 4: 准备最小 smoke 仓库并执行真实任务**

Smoke repo command should generate:

```bash
mkdir -p test-results .playwright-artifacts/checkout
cat > test-results/.playwright-results.json <<'EOF'
{"suites":[{"title":"checkout","specs":[{"title":"should pay successfully","tests":[{"projectName":"chromium","results":[{"status":"passed","duration":321,"attachments":[{"name":"trace","contentType":"application/zip","path":".playwright-artifacts/checkout/trace.zip"},{"name":"screenshot","contentType":"image/png","path":".playwright-artifacts/checkout/failure.png"}]}]}]}]}]}
EOF
printf 'trace' > .playwright-artifacts/checkout/trace.zip
printf 'png' > .playwright-artifacts/checkout/failure.png
mkdir -p playwright-report
printf '<html>ok</html>' > playwright-report/index.html
```

Then run task through platform API and verify:

```bash
curl -s http://127.0.0.1:8080/api/tasks/2/cases
curl -s http://127.0.0.1:8080/api/tasks/2/cases/1/artifacts
```

Expected:
- `cases` 非空
- 至少有一条 `TRACE` 或 `SCREENSHOT`
- 附件返回预签名 URL

- [ ] **Step 5: 查看最终工作区状态**

Run:

```bash
git -C /Users/bytedance/test_platform/playwright_framework status --short
git -C /Users/bytedance/test_platform/playwright-platform-server status --short
```

Expected: 只剩本轮相关改动。

## Self-Review Checklist

- [ ] 设计文档中的“平台模式契约、结果索引、附件保留、case_result、artifact 关联、查询接口、失败策略、烟雾验证”都已被任务 1-6 覆盖。
- [ ] 计划中未出现 `TODO`、`TBD`、`实现细节稍后补` 等占位描述。
- [ ] 名称保持一致：`resultsIndexRelativePath`、`artifactRootRelativePath`、`TaskCaseResultParseService`、`TaskCaseResultPersistenceService`、`CaseResultResponse`、`archiveArtifacts()`。
