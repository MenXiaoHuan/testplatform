# Playwright Platform Step-by-Step Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在当前已完成后端基础能力和前端页面骨架的基础上，分阶段把 Playwright 测试平台推进到可演示、可录入、可执行、可查看结果的 MVP 状态。

**Architecture:** 当前平台采用独立前后端架构：`playwright-platform-server` 负责仓库、场景、任务、Runner、报告归档与对象存储元数据；`playwright-platform-web` 负责仓库、场景、任务的管理页面和报告入口。后续工作不再从 0 开始搭架子，而是在当前可运行基线上继续补齐接口契约、表单校验、删除能力、任务详情摘要、对象存储联通和交付文档。

**Tech Stack:** Vue 3, TypeScript, Vite, Pinia, Vue Router, Element Plus, Vitest, Java 21, Spring Boot 3.x, Spring Data JPA, Flyway, MySQL, MinIO.

---

## 当前基线

- [x] 后端已具备仓库、场景、任务基础接口与本地 MySQL 启动能力
- [x] 后端已具备任务报告与附件查询接口
- [x] 前端已具备管理台壳子、路由、仓库/场景/任务/详情页面骨架
- [x] 前端已接入后端 API，并完成本地代理联调
- [x] 前端已有基础错误提示兜底

## 本轮范围

本计划只覆盖“从当前基线继续完善 MVP”的剩余工作，不再重复已经完成的建库、建表、基础页面和基础 CRUD。

## Task 1: 冻结当前文档基线

**Files:**
- Modify: `playwright_framework/docs/superpowers/specs/2026-06-10-playwright-platform-mvp-design.md`
- Create: `playwright_framework/docs/superpowers/specs/2026-06-10-playwright-platform-current-state.md`
- Create: `playwright_framework/docs/superpowers/plans/2026-06-10-playwright-platform-step-by-step.md`

- [ ] **Step 1: 写当前状态文档**

Create `playwright_framework/docs/superpowers/specs/2026-06-10-playwright-platform-current-state.md`:

```md
# Playwright Platform Current State

## Backend
1. 已支持仓库新增、列表、详情、更新
2. 已支持场景新增、列表、详情、更新
3. 已支持任务执行、列表、详情、报告、附件

## Frontend
1. 已支持仓库页、场景页、任务页、任务详情页
2. 已支持通过 `/api` 代理访问本地后端

## Risks
1. 仍缺删除接口
2. 缺少统一 DTO 与统一异常结构
3. MinIO 仅完成配置层，未完成真实链路验证
```

- [ ] **Step 2: 更新设计文档的“当前进展”段落**

Append to `playwright_framework/docs/superpowers/specs/2026-06-10-playwright-platform-mvp-design.md`:

```md
## Current Progress

截至 2026-06-10，MVP 基础骨架已完成：

1. `playwright-platform-server` 已支持仓库、场景、任务基本流程。
2. `playwright-platform-web` 已完成管理台骨架与基础联调。
3. 后续重点转为体验完善、接口收口、对象存储联通和交付文档。
```

- [ ] **Step 3: 自检文档一致性**

Run:

```bash
grep -n "Current Progress" /Users/bytedance/test_platform/playwright_framework/docs/superpowers/specs/2026-06-10-playwright-platform-mvp-design.md
ls /Users/bytedance/test_platform/playwright_framework/docs/superpowers/specs
```

Expected: 能看到 `Current Progress` 段落和 `2026-06-10-playwright-platform-current-state.md`

- [ ] **Step 4: 提交文档基线**

Run:

```bash
git -C /Users/bytedance/test_platform/playwright_framework add docs/superpowers/specs/2026-06-10-playwright-platform-mvp-design.md docs/superpowers/specs/2026-06-10-playwright-platform-current-state.md docs/superpowers/plans/2026-06-10-playwright-platform-step-by-step.md
git -C /Users/bytedance/test_platform/playwright_framework commit -m "docs: freeze playwright platform step-by-step plan"
```

Expected: 生成仅包含文档的提交

### Task 2: 后端接口收口与删除能力

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/controller/RepositoryController.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryService.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/controller/SceneController.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneService.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneServiceImpl.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/common/GlobalExceptionHandler.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/common/ApiErrorResponse.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/repository/RepositoryControllerTest.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/scene/SceneControllerTest.java`

- [ ] **Step 1: 先写删除接口失败测试**

Append to `playwright-platform-server/src/test/java/com/example/platform/repository/RepositoryControllerTest.java` and `SceneControllerTest.java`:

```java
mockMvc.perform(delete("/api/repos/1"))
        .andExpect(status().isNoContent());

mockMvc.perform(delete("/api/scenes/1"))
        .andExpect(status().isNoContent());
```

- [ ] **Step 2: 跑测试确认缺少删除能力**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=RepositoryControllerTest,SceneControllerTest test
```

Expected: FAIL，提示 `DELETE` 端点或 service 方法不存在

- [ ] **Step 3: 补最小实现**

Update service and controller signatures:

```java
public interface RepositoryService {
    void delete(Long id);
}

@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void delete(@PathVariable Long id) {
    repositoryService.delete(id);
}
```

```java
public interface SceneService {
    void delete(Long id);
}

@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void delete(@PathVariable Long id) {
    sceneService.delete(id);
}
```

- [ ] **Step 4: 增加统一异常返回结构**

Create `ApiErrorResponse.java` and `GlobalExceptionHandler.java`:

```java
public record ApiErrorResponse(String code, String message) {
}
```

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return new ApiErrorResponse("BAD_REQUEST", ex.getMessage());
    }
}
```

- [ ] **Step 5: 跑测试并验证启动**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn test
mvn spring-boot:run
curl -i http://localhost:8080/api/repos
```

Expected: 测试通过，应用成功启动，仓库接口返回 `200`

- [ ] **Step 6: 提交后端收口**

Run:

```bash
git -C /Users/bytedance/test_platform/playwright-platform-server add src/main/java src/test/java
git -C /Users/bytedance/test_platform/playwright-platform-server commit -m "feat: add delete endpoints and api error response"
```

Expected: 生成后端接口收口提交

### Task 3: 前端表单校验与删除交互

**Files:**
- Modify: `playwright-platform-web/src/views/repository/RepositoryListView.vue`
- Modify: `playwright-platform-web/src/views/scene/SceneListView.vue`
- Modify: `playwright-platform-web/src/api/repository.ts`
- Modify: `playwright-platform-web/src/api/scene.ts`
- Modify: `playwright-platform-web/src/stores/repository.ts`
- Modify: `playwright-platform-web/src/stores/scene.ts`
- Create: `playwright-platform-web/src/utils/validators.ts`
- Test: `playwright-platform-web/src/utils/error.test.ts`
- Create: `playwright-platform-web/src/utils/validators.test.ts`

- [ ] **Step 1: 先写校验工具失败测试**

Create `playwright-platform-web/src/utils/validators.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { isRequired, isPositiveId } from './validators'

describe('validators', () => {
  it('should reject blank strings', () => {
    expect(isRequired('   ')).toBe(false)
  })

  it('should reject non-positive ids', () => {
    expect(isPositiveId(0)).toBe(false)
  })
})
```

- [ ] **Step 2: 跑测试确认校验工具不存在**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-web
npm test -- src/utils/validators.test.ts
```

Expected: FAIL，提示 `./validators` 不存在

- [ ] **Step 3: 补最小校验工具**

Create `playwright-platform-web/src/utils/validators.ts`:

```ts
export function isRequired(value: string): boolean {
  return value.trim().length > 0
}

export function isPositiveId(value: number): boolean {
  return value > 0
}
```

- [ ] **Step 4: 接入仓库与场景删除 API**

Update `repository.ts` and `scene.ts`:

```ts
export const deleteRepository = async (id: number) => {
  await http.delete(`/repos/${id}`)
}

export const deleteScene = async (id: number) => {
  await http.delete(`/scenes/${id}`)
}
```

- [ ] **Step 5: 页面里接入表单校验与删除按钮**

Update repository and scene views:

```ts
if (!isRequired(form.name) || !isRequired(form.gitUrl)) {
  ElMessage.warning('请完善仓库名称和 Git 地址')
  return
}
```

```vue
<el-button link type="danger" @click="remove(row)">删除</el-button>
```

- [ ] **Step 6: 跑测试和构建**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-web
npm test -- src/utils/error.test.ts src/utils/validators.test.ts src/router/index.test.ts
npm run build
```

Expected: 所有测试通过，构建成功

- [ ] **Step 7: 提交前端交互完善**

Run:

```bash
git -C /Users/bytedance/test_platform/playwright-platform-web add src package.json
git -C /Users/bytedance/test_platform/playwright-platform-web commit -m "feat: add form validation and delete actions"
```

Expected: 生成前端交互完善提交

### Task 4: 任务详情摘要与对象存储联通

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/controller/TaskController.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskService.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/storage/service/MinioObjectStorageService.java`
- Modify: `playwright-platform-server/src/main/resources/application.yml`
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskControllerTest.java`
- Modify: `playwright-platform-web/src/views/task/TaskDetailView.vue`
- Modify: `playwright-platform-web/src/types/task.ts`

- [ ] **Step 1: 先写任务摘要失败测试**

Append to `TaskControllerTest.java`:

```java
mockMvc.perform(get("/api/tasks/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reportUrl").exists())
        .andExpect(jsonPath("$.status").value("SUCCESS"));
```

- [ ] **Step 2: 跑任务控制器测试确认缺口**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn -Dtest=TaskControllerTest test
```

Expected: FAIL 或者字段内容不足以支撑详情摘要展示

- [ ] **Step 3: 后端补任务详情聚合字段**

Update task response assembly:

```java
return Map.of(
    "taskId", taskId,
    "reportUrl", taskService.getReportUrl(taskId),
    "artifactCount", taskService.listArtifacts(taskId).size()
);
```

- [ ] **Step 4: 验证 MinIO 真实连通**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
curl -i http://127.0.0.1:9000/minio/health/live
mvn test
```

Expected: MinIO 健康检查返回 `200`，后端测试通过

- [ ] **Step 5: 前端详情页展示摘要**

Update `TaskDetailView.vue`:

```vue
<div><span>附件数量</span><strong>{{ artifacts.length }}</strong></div>
<div><span>报告地址</span><strong>{{ report?.reportUrl ?? '暂无' }}</strong></div>
```

- [ ] **Step 6: 联调任务页**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-web
npm run build
curl -i http://localhost:4173/api/tasks
```

Expected: 前端构建通过，前端代理任务接口返回 `200`

- [ ] **Step 7: 提交任务详情完善**

Run:

```bash
git -C /Users/bytedance/test_platform/playwright-platform-server add src/main/java src/test/java src/main/resources/application.yml
git -C /Users/bytedance/test_platform/playwright-platform-server commit -m "feat: enrich task detail and verify minio integration"
git -C /Users/bytedance/test_platform/playwright-platform-web add src
git -C /Users/bytedance/test_platform/playwright-platform-web commit -m "feat: improve task detail summary"
```

Expected: 生成后端与前端各自的任务详情完善提交

### Task 5: 交付文档与演示脚本

**Files:**
- Create: `playwright_framework/docs/superpowers/specs/2026-06-10-playwright-platform-runbook.md`
- Create: `playwright_framework/docs/superpowers/specs/2026-06-10-playwright-platform-demo-script.md`

- [ ] **Step 1: 写本地启动 Runbook**

Create `playwright_framework/docs/superpowers/specs/2026-06-10-playwright-platform-runbook.md`:

```md
# Playwright Platform Runbook

## Start Backend
```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn spring-boot:run
```

## Start Frontend
```bash
cd /Users/bytedance/test_platform/playwright-platform-web
npm run dev -- --host 0.0.0.0 --port 4173
```
```

- [ ] **Step 2: 写 5 分钟演示脚本**

Create `playwright_framework/docs/superpowers/specs/2026-06-10-playwright-platform-demo-script.md`:

```md
# Demo Script

1. 打开仓库页，展示新增仓库
2. 打开场景页，展示新增场景
3. 点击执行，触发任务
4. 打开任务详情，查看报告与附件
```

- [ ] **Step 3: 校验文档存在**

Run:

```bash
ls /Users/bytedance/test_platform/playwright_framework/docs/superpowers/specs
```

Expected: 可以看到 runbook 和 demo script

- [ ] **Step 4: 提交交付文档**

Run:

```bash
git -C /Users/bytedance/test_platform/playwright_framework add docs/superpowers/specs/2026-06-10-playwright-platform-runbook.md docs/superpowers/specs/2026-06-10-playwright-platform-demo-script.md
git -C /Users/bytedance/test_platform/playwright_framework commit -m "docs: add platform runbook and demo script"
```

Expected: 生成交付文档提交

## 自检结论

1. 规格覆盖：本计划覆盖文档冻结、后端收口、前端交互、任务详情增强、对象存储联通与交付文档。
2. 无占位符：所有任务都给出了明确文件、命令和示例代码。
3. 命名一致：仓库、场景、任务三个主模块沿用当前项目命名，没有重新发明目录结构。

## 执行顺序建议

1. 先完成 Task 1，冻结文档基线。
2. 再完成 Task 2 和 Task 3，补齐可操作的管理能力。
3. 然后完成 Task 4，提升任务结果可读性。
4. 最后完成 Task 5，准备演示与交付。

Plan complete and saved to `docs/superpowers/plans/2026-06-10-playwright-platform-step-by-step.md`. Two execution options:

**1. Subagent-Driven (recommended)** - 我按任务逐个派发执行，阶段性回报结果

**2. Inline Execution** - 我在当前会话里按这个计划连续执行，每完成一段给你回报

Which approach?
