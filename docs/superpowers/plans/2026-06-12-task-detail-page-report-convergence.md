# Task Detail Page Report Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收敛任务列表、任务详情、任务报告三处职责，让任务详情页成为主查看页，并让成功/失败任务都统一展示视频、trace、截图、日志等执行证据。

**Architecture:** 先补任务列表的 `detailAvailable` 语义，锁定运行中不可进详情的入口规则；再扩后端详情与报告聚合字段，让前端可以直接渲染任务详情页；最后把 `TaskDetailView` 重构为主查看页，把 `TaskReportView` 降级为深度报告页。前端优先复用现有 `ListPageShell` 和 task store，后端优先扩现有 `/tasks/:id`、`/tasks/:id/report-summary`、`/tasks/:id/cases`，避免第一阶段就引入新接口。

**Tech Stack:** Vue 3, TypeScript, Pinia, Vue Router, Element Plus, Vitest, Spring Boot, JPA, JUnit 5, Mockito

---

## File Map

### Frontend

- Modify: `playwright-platform-web/src/views/task/TaskListView.vue`
  - 任务列表入口规则，运行中禁用详情，按钮提示，头部左右插槽布局
- Modify: `playwright-platform-web/src/views/task/TaskDetailView.vue`
  - 主任务详情页，承载基础信息、结果总览、证据区、用例结果、附件归档
- Modify: `playwright-platform-web/src/views/task/TaskReportView.vue`
  - 调整为深度报告页，保留更高密度排障视图
- Modify: `playwright-platform-web/src/components/list/ListPageShell.vue`
  - 头部左右插槽布局稳定化，确保左按钮贴左、右按钮贴右
- Modify: `playwright-platform-web/src/stores/task.ts`
  - 增加详情聚合拉取逻辑，统一 store 状态
- Modify: `playwright-platform-web/src/types/task.ts`
  - 扩详情、附件、用例结果字段
- Modify: `playwright-platform-web/src/types/report.ts`
  - 扩任务报告摘要和 artifact 统计字段
- Modify: `playwright-platform-web/src/api/task.ts`
  - 补任务 case 列表、详情聚合或扩展现有接口类型
- Modify: `playwright-platform-web/src/router/index.ts`
  - 如需给详情页增加 query 返回来源，统一路由约定
- Test: `playwright-platform-web/tests/unit/views/task/TaskListView.test.ts`
- Test: `playwright-platform-web/tests/unit/views/task/TaskDetailView.test.ts`
- Test: `playwright-platform-web/tests/unit/views/task/TaskReportView.test.ts`
- Test: `playwright-platform-web/tests/unit/stores/task.test.ts`
- Test: `playwright-platform-web/tests/unit/components/list/ListPageShell.test.ts`

### Backend

- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/controller/TaskController.java`
  - 扩任务详情、任务报告摘要、任务 case 列表返回字段；必要时增加详情聚合接口
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskService.java`
  - 增加详情聚合查询接口声明
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
  - 实现任务详情、报告摘要、artifact 统计、detailAvailable、case 证据链接
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/dto/TaskDetailResponse.java`
  - 扩 sceneName、repositoryName、detailAvailable、时间与环境统计
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/dto/TaskReportSummaryResponse.java`
  - 扩 reportStatus、artifactSummary、projectStats
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/dto/CaseResultResponse.java`
  - 扩 errorMessage、videoUrl、traceUrl、screenshotUrls、logUrl
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/dto/CaseArtifactLinkResponse.java`
  - 统一 artifactType、label、scope、case/project 元信息
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/dto/SceneTaskListResponse.java`
  - 增加 detailAvailable；必要时补 sceneName/repositoryName
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/dto/TaskArtifactSummaryResponse.java`
  - 视频、trace、截图、日志、其他附件计数
- Create: `playwright-platform-server/src/main/java/com/example/platform/task/dto/TaskProjectStatResponse.java`
  - Project 维度统计
- Optional Create: `playwright-platform-server/src/main/java/com/example/platform/task/dto/TaskDetailSummaryResponse.java`
  - 若决定新增 `/tasks/:id/detail-summary`，承载 task/report/artifacts/caseResults 聚合返回
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskControllerTest.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskArtifactArchiveServiceTest.java`

---

## Phase 1: 锁定任务列表入口规则

**目标：** 先完成运行中任务不可进入详情的业务约束，并修正 `ListPageShell` 头部左右布局。

### Frontend Checklist

- [ ] 修改 `playwright-platform-web/src/components/list/ListPageShell.vue`
  - 保证 `.content-panel__header-left` 使用 `justify-content: flex-start`
  - 保证 `.content-panel__header-right` 使用 `justify-content: flex-end`
  - 头部整体维持 `space-between`，但左右槽各自宽度稳定
- [ ] 修改 `playwright-platform-web/src/views/task/TaskListView.vue`
  - 新增 `canOpenDetail(row)`：仅 `row.detailAvailable === true` 时可点击
  - `RUNNING` 时 `详情` 按钮禁用
  - `title` 提示统一为 `任务执行中，完成后可查看详情`
  - 跳转详情时补返回来源 query，例如 `?from=scene&sceneId=11`
- [ ] 修改 `playwright-platform-web/src/types/task.ts`
  - 在 `TaskRecord` 增加 `detailAvailable?: boolean`
  - 可选增加 `sceneName?: string | null`
  - 可选增加 `repositoryName?: string | null`
- [ ] 修改 `playwright-platform-web/src/api/task.ts`
  - 更新列表接口返回类型到新 `TaskRecord`

### Backend Checklist

- [ ] 修改 `playwright-platform-server/src/main/java/com/example/platform/task/dto/SceneTaskListResponse.java`
  - 新增 `boolean detailAvailable`
  - 计算规则：`RUNNING -> false`；其余结束态 -> `true`
- [ ] 修改 `playwright-platform-server/src/main/java/com/example/platform/task/controller/TaskController.java`
  - `/api/scenes/{sceneId}/tasks` 返回扩展后的 `SceneTaskListResponse`
  - `/api/tasks` 建议同步切到 DTO，不再直接返回 `TaskEntity`
- [ ] 修改 `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
  - 统一列表 DTO 映射规则，避免前后端各自判断状态

### Tests

- [ ] 更新 `playwright-platform-web/tests/unit/views/task/TaskListView.test.ts`
  - 增加 `RUNNING` 行详情按钮禁用断言
  - 增加 `title` 提示断言
  - 增加 `SUCCESS/FAILED` 可点击断言
- [ ] 更新 `playwright-platform-web/tests/unit/components/list/ListPageShell.test.ts`
  - 断言左右头部容器同时存在，返回按钮在左槽，执行按钮在右槽
- [ ] 更新 `playwright-platform-server/src/test/java/com/example/platform/task/TaskControllerTest.java`
  - 断言 scene task list 返回 `detailAvailable`

### Acceptance

- [ ] `/scenes/:id/tasks` 中运行中任务的 `详情` 按钮不可点击
- [ ] `返回场景中心` 固定显示在头部左侧
- [ ] 列表接口直接返回前端所需详情可用状态

---

## Phase 2: 扩后端详情与报告字段

**目标：** 不引入大重构，先让现有接口具备支撑任务详情页的字段密度。

### DTO / Interface Changes

- [ ] 修改 `playwright-platform-server/src/main/java/com/example/platform/task/dto/TaskDetailResponse.java`
  - 增加：
    - `String sceneName`
    - `String repositoryName`
    - `boolean detailAvailable`
    - `Integer environmentVariableCount`
    - 保留现有 `resolvedBrowser / resolvedMatchValue / resolvedRunCommand / startedAt / finishedAt / durationMs`
- [ ] 修改 `playwright-platform-server/src/main/java/com/example/platform/task/dto/TaskReportSummaryResponse.java`
  - 增加：
    - `String reportStatus`
    - `TaskArtifactSummaryResponse artifactSummary`
    - `List<TaskProjectStatResponse> projectStats`
- [ ] 创建 `playwright-platform-server/src/main/java/com/example/platform/task/dto/TaskArtifactSummaryResponse.java`
  - 字段：
    - `int videoCount`
    - `int traceCount`
    - `int screenshotCount`
    - `int logCount`
    - `int otherCount`
- [ ] 创建 `playwright-platform-server/src/main/java/com/example/platform/task/dto/TaskProjectStatResponse.java`
  - 字段：
    - `String projectName`
    - `int total`
- [ ] 修改 `playwright-platform-server/src/main/java/com/example/platform/task/dto/CaseArtifactLinkResponse.java`
  - 补 `scope`
  - 补稳定 `label`
  - 如已有 `artifactType`，统一枚举值语义：`VIDEO / TRACE / SCREENSHOT / LOG / REPORT / OTHER`
- [ ] 修改 `playwright-platform-server/src/main/java/com/example/platform/task/dto/CaseResultResponse.java`
  - 增加：
    - `String errorMessage`
    - `String videoUrl`
    - `String traceUrl`
    - `List<String> screenshotUrls`
    - `String logUrl`
  - 成功和失败都允许返回这些字段

### Service Changes

- [ ] 修改 `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
  - 在 `get(taskId)` 组装详情时补 scene / repo 名称
  - 在 `getReportSummary(taskId)` 内补：
    - `reportStatus`
    - `artifactSummary`
    - `projectStats`
    - case 级别证据链接
  - 列表 artifacts 时，不要按失败状态过滤 `video / trace / screenshot`
  - 如果 `reportReady = false` 但 artifact 已存在，仍返回 artifacts 和 caseResults 中的链接

### Controller Changes

- [ ] 修改 `playwright-platform-server/src/main/java/com/example/platform/task/controller/TaskController.java`
  - `/api/tasks/{taskId}` 返回扩展后的 `TaskDetailResponse`
  - `/api/tasks/{taskId}/report-summary` 返回扩展后的 `TaskReportSummaryResponse`
  - `/api/tasks/{taskId}/cases` 返回扩展后的 `CaseResultResponse`

### Tests

- [ ] 更新 `playwright-platform-server/src/test/java/com/example/platform/task/TaskControllerTest.java`
  - 断言任务详情含 `sceneName / repositoryName / detailAvailable`
  - 断言 `report-summary` 含 `reportStatus / artifactSummary / projectStats`
  - 断言 case 结果含 `videoUrl / traceUrl / screenshotUrls / logUrl`
- [ ] 更新 `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`
  - 增加成功任务也保留证据链接的断言
- [ ] 更新 `playwright-platform-server/src/test/java/com/example/platform/task/TaskArtifactArchiveServiceTest.java`
  - 断言 artifact 类型映射与 `scope` 分类稳定

### Acceptance

- [ ] `/tasks/:id` 单接口即可渲染详情首屏基础信息
- [ ] `/tasks/:id/report-summary` 可提供结果总览、附件统计、project 统计
- [ ] `/tasks/:id/cases` 返回成功/失败任务的统一证据字段

---

## Phase 3: 重构任务详情页为主查看页

**目标：** `TaskDetailView` 成为任务查看主入口，承接结果、视频、trace、截图、日志。

### Frontend Files

- [ ] 修改 `playwright-platform-web/src/types/task.ts`
  - 增加 `TaskDetailRecord` 或扩展 `TaskRecord`
  - 增加 `TaskArtifactSummary`
  - 增加 `TaskProjectStat`
  - 增加 case 级别的 `errorMessage / videoUrl / traceUrl / screenshotUrls / logUrl`
- [ ] 修改 `playwright-platform-web/src/types/report.ts`
  - 让 `TaskReportSummary` 包含：
    - `reportStatus`
    - `artifactSummary`
    - `projectStats`
    - `caseResults` 的统一证据字段
- [ ] 修改 `playwright-platform-web/src/api/task.ts`
  - 补 `listTaskCases(taskId)`
  - 若决定第一版不新增聚合接口，则保留 `getTask + getTaskReportSummary + listTaskCases`
- [ ] 修改 `playwright-platform-web/src/stores/task.ts`
  - 新增 `caseResults` 状态
  - 新增 `fetchTaskDetailPage(taskId)`，并行请求：
    - `getTask(taskId)`
    - `getTaskReportSummary(taskId)`
    - `listTaskCases(taskId)`
  - 对局部失败做降级，不因为报告失败导致 `current` 被清空
- [ ] 修改 `playwright-platform-web/src/views/task/TaskDetailView.vue`
  - 顶部操作区：
    - 左：返回场景中心 / 返回任务列表
    - 右：重新执行
  - 任务基础信息卡：
    - 状态、场景、仓库、浏览器、触发方式、执行器、分支、命令、匹配值、时间、耗时
  - 结果总览卡：
    - 报告状态、总数、通过/失败/跳过、project 统计、artifactSummary
  - 执行证据快捷区：
    - 打开视频
    - 打开 trace
    - 打开截图
    - 打开日志
    - 打开完整报告
  - 用例结果区：
    - 状态筛选
    - 每条 case 下展示 video / trace / screenshot / log 入口
  - 附件归档区：
    - task 级 / case 级 artifacts 分组展示

### Interaction Rules

- [ ] 成功态和失败态都展示 `video / trace / screenshot / log`
- [ ] 失败态额外高亮 `errorMessage`
- [ ] `reportStatus !== READY` 时，若有附件，仍允许打开附件
- [ ] 若无任何 evidence，显示 `暂无执行产物`

### Tests

- [ ] 创建或更新 `playwright-platform-web/tests/unit/views/task/TaskDetailView.test.ts`
  - 断言左返回、右重新执行
  - 断言成功态显示视频/trace/截图
  - 断言失败态同样显示视频/trace/截图
  - 断言 `reportStatus = NOT_READY` 但 artifacts 存在时仍展示证据区
- [ ] 创建或更新 `playwright-platform-web/tests/unit/stores/task.test.ts`
  - 断言 `fetchTaskDetailPage()` 会并行更新 `current/reportSummary/caseResults`
  - 断言部分请求失败时保留已成功返回的数据

### Acceptance

- [ ] 任务详情页成为主查看页
- [ ] 成功/失败任务的证据展示规则一致
- [ ] 任务详情不依赖任务报告页才能完成基础排障

---

## Phase 4: 降级任务报告页为深度报告页

**目标：** 让 `TaskReportView` 成为次级深入页，避免与详情页职责重复。

### Frontend Changes

- [ ] 修改 `playwright-platform-web/src/views/task/TaskReportView.vue`
  - 去掉“它是主报告入口”的表达
  - 保留高密度失败分析、附件全量列表、过滤查看
  - 顶部返回入口改为返回任务详情页
- [ ] 修改 `playwright-platform-web/src/views/task/TaskDetailView.vue`
  - 仅在详情页中提供 `查看深度报告` 或 `打开完整报告`

### Tests

- [ ] 更新 `playwright-platform-web/tests/unit/views/task/TaskReportView.test.ts`
  - 断言返回详情页入口存在
  - 断言报告页仍保留失败用例聚合和附件清单

### Acceptance

- [ ] 列表页 -> 详情页 -> 报告页 的职责链清晰
- [ ] 用户不必进报告页也能拿到视频、trace、截图、日志

---

## Optional Phase 5: 新增详情聚合接口

**目标：** 在前后端第一版稳定后，再决定是否把详情页收敛成单请求。

### Backend

- [ ] 创建 `playwright-platform-server/src/main/java/com/example/platform/task/dto/TaskDetailSummaryResponse.java`
  - 字段：
    - `TaskDetailResponse task`
    - `TaskReportSummaryResponse report`
    - `List<CaseResultResponse> caseResults`
- [ ] 修改 `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskService.java`
  - 增加 `TaskDetailSummaryResponse getDetailSummary(Long taskId)`
- [ ] 修改 `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
  - 聚合调用现有 `get/getReportSummary/listCaseResults`
- [ ] 修改 `playwright-platform-server/src/main/java/com/example/platform/task/controller/TaskController.java`
  - 新增 `GET /api/tasks/{taskId}/detail-summary`

### Frontend

- [ ] 修改 `playwright-platform-web/src/api/task.ts`
  - 新增 `getTaskDetailSummary(taskId)`
- [ ] 修改 `playwright-platform-web/src/stores/task.ts`
  - 将 `fetchTaskDetailPage()` 切换为单请求

### Acceptance

- [ ] 详情页首屏请求数从 3 条降为 1 条
- [ ] 前后端字段语义保持不变，仅调用方式收敛

---

## Verification Commands

### Frontend

- [ ] 运行任务列表与详情相关测试

```bash
cd /Users/bytedance/test_platform/playwright-platform-web
npm test -- tests/unit/components/list/ListPageShell.test.ts tests/unit/views/task/TaskListView.test.ts tests/unit/views/task/TaskDetailView.test.ts tests/unit/views/task/TaskReportView.test.ts tests/unit/stores/task.test.ts
```

- [ ] 检查前端诊断

```bash
cd /Users/bytedance/test_platform/playwright-platform-web
npm run build
```

### Backend

- [ ] 运行任务接口相关测试

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=TaskControllerTest,TaskExecutionServiceTest,TaskArtifactArchiveServiceTest test
```

### Manual

- [ ] 启动前后端后验证以下场景
  - 运行中任务在任务列表中 `详情` 不可点
  - 成功任务详情页展示视频、trace、截图、日志
  - 失败任务详情页同样展示视频、trace、截图、日志，并高亮错误摘要
  - 报告未就绪但 evidence 已归档时，详情页仍能打开 evidence
  - 报告页可从详情页进入并返回详情页

---

## Rollout Order

1. Phase 1: 列表入口规则 + ListPageShell 头部对齐
2. Phase 2: 后端详情 / 报告 / case 字段补齐
3. Phase 3: 任务详情页重构为主查看页
4. Phase 4: 任务报告页降级为深度报告页
5. Optional Phase 5: 详情聚合接口

---

## Notes

- 第一版不要同时做“新接口 + 详情页大改 + 报告页完全重写”，风险太高。
- 第一版优先复用现有 `/tasks/:id`、`/tasks/:id/report-summary`、`/tasks/:id/cases`。
- 只有在前后端字段稳定后，再决定是否新增 `/tasks/:id/detail-summary`。
