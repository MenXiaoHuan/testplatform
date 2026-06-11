# Playwright Platform Phase 3 Task Report Summary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为任务新增独立的报告摘要能力，第一阶段提供任务级报告页面，展示任务状态、执行快照、外部报告入口和附件列表。

**Architecture:** 后端新增聚合式 `report-summary` 接口，把任务、报告和附件汇总到一个响应中；前端新增任务报告页和路由，通过单接口加载任务级摘要，避免前端手动拼多个接口。现有任务列表和任务详情页继续保留，并增加跳转入口。

**Tech Stack:** Vue 3, TypeScript, Vue Router, Pinia, Vitest, Spring Boot, JUnit

---

## File Structure

- Create: `playwright-platform-server/src/main/java/com/example/platform/task/dto/TaskReportSummaryResponse.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/controller/TaskController.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskService.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/task/TaskControllerTest.java`
- Create: `playwright-platform-web/src/types/report.ts`
- Modify: `playwright-platform-web/src/api/task.ts`
- Modify: `playwright-platform-web/src/stores/task.ts`
- Modify: `playwright-platform-web/src/router/index.ts`
- Modify: `playwright-platform-web/src/views/task/TaskListView.vue`
- Modify: `playwright-platform-web/src/views/task/TaskDetailView.vue`
- Create: `playwright-platform-web/src/views/task/TaskReportView.vue`
- Create: `playwright-platform-web/tests/unit/views/task/TaskReportView.test.ts`
- Modify: `playwright-platform-web/tests/unit/router/index.test.ts`

### Task 1: 后端新增任务报告摘要 DTO 和接口

**Files:**
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/dto/TaskReportSummaryResponse.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/controller/TaskController.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskService.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskControllerTest.java`

- [ ] **Step 1: 写失败测试，确认存在新的 `report-summary` 接口**

```java
@Test
void getTaskReportSummary_returnsTaskSnapshotAndArtifacts() throws Exception {
    mockMvc.perform(get("/api/tasks/101/report-summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.task.id").value(101))
            .andExpect(jsonPath("$.data.task.resolvedRunCommand").exists())
            .andExpect(jsonPath("$.data.reportUrl").exists())
            .andExpect(jsonPath("$.data.artifacts").isArray());
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=TaskControllerTest test`

Expected: FAIL，提示 `/api/tasks/{taskId}/report-summary` 不存在

- [ ] **Step 3: 增加 DTO、服务方法和控制器**

```java
public record TaskReportSummaryResponse(
        TaskDetailResponse task,
        String reportUrl,
        List<ArtifactEntity> artifacts,
        int artifactCount,
        boolean reportReady) {}
```

```java
TaskReportSummaryResponse getReportSummary(Long taskId);
```

```java
@Override
public TaskReportSummaryResponse getReportSummary(Long taskId) {
    TaskEntity task = get(taskId);
    List<ArtifactEntity> artifacts = listArtifacts(taskId);
    return new TaskReportSummaryResponse(
            TaskDetailResponse.from(task, artifacts.size()),
            getReportUrl(taskId),
            artifacts,
            artifacts.size(),
            task.getReportUrl() != null && !task.getReportUrl().isBlank());
}
```

```java
@GetMapping("/api/tasks/{taskId}/report-summary")
public ApiResponse<TaskReportSummaryResponse> getTaskReportSummary(@PathVariable Long taskId) {
    return ApiResponse.ok(taskService.getReportSummary(taskId));
}
```

- [ ] **Step 4: 重新运行控制器测试**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=TaskControllerTest test`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-server/src/main/java/com/example/platform/task/dto/TaskReportSummaryResponse.java \
  playwright-platform-server/src/main/java/com/example/platform/task/controller/TaskController.java \
  playwright-platform-server/src/main/java/com/example/platform/task/service/TaskService.java \
  playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java \
  playwright-platform-server/src/test/java/com/example/platform/task/TaskControllerTest.java
git commit -m "feat: add task report summary endpoint"
```

### Task 2: 前端新增报告摘要类型、API 和 store

**Files:**
- Create: `playwright-platform-web/src/types/report.ts`
- Modify: `playwright-platform-web/src/api/task.ts`
- Modify: `playwright-platform-web/src/stores/task.ts`
- Test: `playwright-platform-web/tests/unit/stores/task.test.ts`

- [ ] **Step 1: 写失败测试，确认 store 可以加载任务报告摘要**

```ts
it('fetches task report summary from the new endpoint', async () => {
  vi.mocked(getTaskReportSummary).mockResolvedValue({
    task: {
      id: 101,
      sceneId: 11,
      repoId: 7,
      status: 'SUCCESS',
      triggerType: 'MANUAL',
      branch: 'main',
      resolvedRunCommand: 'node ./scripts/run-e2e.cjs --project chromium',
    },
    reportUrl: 'https://example.com/report/index.html',
    artifacts: [],
    artifactCount: 0,
    reportReady: true,
  })

  const summary = await store.fetchReportSummary(101)

  expect(summary.reportReady).toBe(true)
  expect(summary.task.resolvedRunCommand).toContain('chromium')
})
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- tests/unit/stores/task.test.ts`

Expected: FAIL，提示缺少 `getTaskReportSummary` 或 `fetchReportSummary`

- [ ] **Step 3: 新增类型与 API**

```ts
import type { ArtifactRecord, TaskRecord } from './task'

export interface TaskReportSummary {
  task: TaskRecord
  reportUrl: string | null
  artifacts: ArtifactRecord[]
  artifactCount: number
  reportReady: boolean
}
```

```ts
export const getTaskReportSummary = async (taskId: number) => {
  return get<TaskReportSummary>(`/tasks/${taskId}/report-summary`)
}
```

```ts
async fetchReportSummary(taskId: number) {
  return getTaskReportSummary(taskId)
}
```

- [ ] **Step 4: 重新运行 store 测试**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- tests/unit/stores/task.test.ts`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-web/src/types/report.ts \
  playwright-platform-web/src/api/task.ts \
  playwright-platform-web/src/stores/task.ts \
  playwright-platform-web/tests/unit/stores/task.test.ts
git commit -m "feat: add task report summary client"
```

### Task 3: 新增任务报告页和路由入口

**Files:**
- Create: `playwright-platform-web/src/views/task/TaskReportView.vue`
- Modify: `playwright-platform-web/src/router/index.ts`
- Modify: `playwright-platform-web/src/views/task/TaskListView.vue`
- Modify: `playwright-platform-web/src/views/task/TaskDetailView.vue`
- Test: `playwright-platform-web/tests/unit/views/task/TaskReportView.test.ts`
- Test: `playwright-platform-web/tests/unit/router/index.test.ts`

- [ ] **Step 1: 写失败测试，确认路由和页面展示任务级摘要**

```ts
it('registers a task report route', () => {
  expect(router.getRoutes().some((route) => route.path === '/tasks/:id/report')).toBe(true)
})
```

```ts
it('renders task report summary and artifact list', async () => {
  taskStoreState.fetchReportSummary.mockResolvedValue({
    task: {
      id: 101,
      sceneId: 11,
      repoId: 7,
      status: 'SUCCESS',
      triggerType: 'MANUAL',
      branch: 'main',
      resolvedRunCommand: 'node ./scripts/run-e2e.cjs --project chromium',
      reportReady: true,
    },
    reportUrl: 'https://example.com/report/index.html',
    artifacts: [
      { id: 1, taskId: 101, artifactType: 'TRACE', bucket: 'runs', objectKey: 'trace.zip', url: 'https://example.com/trace.zip' },
    ],
    artifactCount: 1,
    reportReady: true,
  })

  const wrapper = mount(TaskReportView)
  await flushPromises()

  expect(wrapper.text()).toContain('任务报告')
  expect(wrapper.text()).toContain('SUCCESS')
  expect(wrapper.text()).toContain('node ./scripts/run-e2e.cjs --project chromium')
  expect(wrapper.text()).toContain('TRACE')
})
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- tests/unit/views/task/TaskReportView.test.ts tests/unit/router/index.test.ts`

Expected: FAIL，提示缺少报告页组件或路由

- [ ] **Step 3: 实现报告页与路由**

```ts
{
  path: '/tasks/:id/report',
  name: 'task-report',
  component: () => import('../views/task/TaskReportView.vue'),
}
```

```vue
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useTaskStore } from '../../stores/task'

const route = useRoute()
const taskStore = useTaskStore()
const summary = ref()
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  summary.value = await taskStore.fetchReportSummary(Number(route.params.id))
  loading.value = false
})

const artifacts = computed(() => summary.value?.artifacts ?? [])
</script>

<template>
  <section class="page-grid">
    <div class="glass-card content-panel">
      <div class="content-panel__header">
        <h2>任务报告</h2>
      </div>
      <div v-if="summary">
        <p>{{ summary.task.status }}</p>
        <p>{{ summary.task.resolvedRunCommand }}</p>
        <a v-if="summary.reportUrl" :href="summary.reportUrl" target="_blank" rel="noreferrer">打开外部报告</a>
        <ul>
          <li v-for="artifact in artifacts" :key="artifact.id">
            {{ artifact.artifactType }}
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>
```

```vue
<el-button link type="primary" @click="openReport(row.id)">任务报告</el-button>
```

- [ ] **Step 4: 重新运行视图与路由测试**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- tests/unit/views/task/TaskReportView.test.ts tests/unit/router/index.test.ts`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-web/src/views/task/TaskReportView.vue \
  playwright-platform-web/src/router/index.ts \
  playwright-platform-web/src/views/task/TaskListView.vue \
  playwright-platform-web/src/views/task/TaskDetailView.vue \
  playwright-platform-web/tests/unit/views/task/TaskReportView.test.ts \
  playwright-platform-web/tests/unit/router/index.test.ts
git commit -m "feat: add task report summary page"
```

### Task 4: 执行阶段 3 回归

**Files:**
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskControllerTest.java`
- Test: `playwright-platform-web/tests/unit/stores/task.test.ts`
- Test: `playwright-platform-web/tests/unit/views/task/TaskReportView.test.ts`
- Test: `playwright-platform-web/tests/unit/views/task/TaskDetailView.test.ts`
- Test: `playwright-platform-web/tests/unit/views/task/TaskListView.test.ts`

- [ ] **Step 1: 运行后端任务控制器测试**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=TaskControllerTest test`

Expected: PASS

- [ ] **Step 2: 运行前端 task store 测试**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- tests/unit/stores/task.test.ts`

Expected: PASS

- [ ] **Step 3: 运行报告页与任务详情相关测试**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- tests/unit/views/task/TaskReportView.test.ts tests/unit/views/task/TaskDetailView.test.ts tests/unit/views/task/TaskListView.test.ts`

Expected: PASS

- [ ] **Step 4: 检查最近修改文件诊断**

Run diagnostics for:

```text
playwright-platform-web/src/views/task/TaskReportView.vue
playwright-platform-web/src/router/index.ts
playwright-platform-web/src/api/task.ts
playwright-platform-web/src/stores/task.ts
```

Expected: no new diagnostics

- [ ] **Step 5: 提交回归验证**

```bash
cd /Users/bytedance/test_platform
git add .
git commit -m "test: verify task report summary flow"
```

### Task 5: 报告页增强体验与结果分析

**Files:**
- Modify: `playwright-platform-web/src/views/task/TaskReportView.vue`
- Modify: `playwright-platform-web/src/types/report.ts`
- Test: `playwright-platform-web/tests/unit/views/task/TaskReportView.test.ts`

- [ ] **Step 1: 写失败测试，确认报告页支持增强交互与结果分析**

```ts
it('renders failed cases first, supports status filtering, and exposes per-artifact actions', async () => {
  taskStoreState.reportSummary = {
    task: { id: 101, sceneId: 11, repoId: 7, status: 'FAILED', triggerType: 'MANUAL', branch: 'main' },
    reportUrl: 'https://example.com/report/index.html',
    artifacts: [
      { id: 1, taskId: 101, artifactType: 'TRACE', bucket: 'runs', objectKey: 'trace.zip', url: 'https://example.com/trace.zip' },
    ],
    caseResults: [
      { id: 1, taskId: 101, fullName: 'checkout :: should pay', status: 'FAILED', projectName: 'chromium', artifactCount: 1 },
      { id: 2, taskId: 101, fullName: 'checkout :: should refund', status: 'PASSED', projectName: 'firefox', artifactCount: 0 },
    ],
    caseSummary: { passed: 1, failed: 1, skipped: 0, total: 2 },
    artifactCount: 1,
    reportReady: true,
  }

  const wrapper = mount(TaskReportView)
  await flushPromises()

  expect(wrapper.text()).toContain('失败用例')
  expect(wrapper.text()).toContain('checkout :: should pay')
  expect(wrapper.text()).toContain('chromium')
  expect(wrapper.text()).toContain('firefox')
  expect(wrapper.text()).toContain('打开')
  expect(wrapper.text()).toContain('下载')
})
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- tests/unit/views/task/TaskReportView.test.ts`

Expected: FAIL，提示缺少失败优先区、筛选逻辑或逐条附件操作

- [ ] **Step 3: 实现平衡版增强**

实现内容：

- 附件表格增加逐条 `打开 / 下载`
- 增加失败用例优先区
- 增加状态筛选 `全部 / FAILED / PASSED / SKIPPED`
- 增加按 `projectName` 的轻量统计展示
- 保持所有筛选和统计基于现有 `report-summary` 结果在前端本地完成

- [ ] **Step 4: 重新运行报告页相关测试**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- tests/unit/views/task/TaskReportView.test.ts tests/unit/views/task/TaskDetailView.test.ts tests/unit/views/task/TaskListView.test.ts`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-web/src/views/task/TaskReportView.vue \
  playwright-platform-web/src/types/report.ts \
  playwright-platform-web/tests/unit/views/task/TaskReportView.test.ts
git commit -m "feat: enhance task report experience"
```
