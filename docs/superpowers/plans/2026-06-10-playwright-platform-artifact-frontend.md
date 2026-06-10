# Playwright Platform Artifact Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在任务详情页把已落库的 artifact 提升为可读、可打开、可下载的前端附件列表。

**Architecture:** 保持现有 `TaskDetailView` 和任务 store 的接口不变，仅在前端新增轻量 artifact 格式化工具，并增强附件表格的列和操作。页面继续消费现有 `artifacts` 数组，不引入新的后端字段，也不改变路由结构。

**Tech Stack:** Vue 3, TypeScript, Pinia, Element Plus, Vitest, Vue Test Utils.

---

## File Map

- Create: `playwright-platform-web/src/utils/artifact.ts`
- Create: `playwright-platform-web/src/utils/artifact.test.ts`
- Modify: `playwright-platform-web/src/views/task/TaskDetailView.vue`
- Modify: `playwright-platform-web/src/views/task/TaskDetailView.test.ts`

## Task 1: 新增附件格式化工具

**Files:**
- Create: `playwright-platform-web/src/utils/artifact.ts`
- Create: `playwright-platform-web/src/utils/artifact.test.ts`

- [ ] **Step 1: 先写工具失败测试**

Create `playwright-platform-web/src/utils/artifact.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { formatArtifactType, formatFileSize, getArtifactFileName } from './artifact'

describe('artifact utils', () => {
  it('formats artifact type labels', () => {
    expect(formatArtifactType('REPORT_FILE')).toBe('报告文件')
    expect(formatArtifactType('TRACE')).toBe('TRACE')
  })

  it('formats file sizes', () => {
    expect(formatFileSize(null)).toBe('-')
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(1536)).toBe('1.5 KB')
    expect(formatFileSize(1048576)).toBe('1.0 MB')
  })

  it('extracts file names from object keys', () => {
    expect(getArtifactFileName('runs/2/artifacts/data/trace.zip')).toBe('trace.zip')
    expect(getArtifactFileName('index.html')).toBe('index.html')
    expect(getArtifactFileName('')).toBe('-')
  })
})
```

- [ ] **Step 2: 跑测试确认工具尚不存在**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-web
npm run test -- src/utils/artifact.test.ts
```

Expected: FAIL，提示 `artifact.ts` 或导出函数不存在。

- [ ] **Step 3: 写最小工具实现**

Create `playwright-platform-web/src/utils/artifact.ts`:

```ts
export function formatArtifactType(type: string): string {
  if (type === 'REPORT_FILE') {
    return '报告文件'
  }
  return type
}

export function formatFileSize(size?: number | null): string {
  if (size == null || Number.isNaN(size)) {
    return '-'
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

export function getArtifactFileName(objectKey: string): string {
  if (!objectKey) {
    return '-'
  }
  const segments = objectKey.split('/').filter(Boolean)
  return segments.at(-1) ?? '-'
}
```

- [ ] **Step 4: 跑测试验证工具通过**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-web
npm run test -- src/utils/artifact.test.ts
```

Expected: PASS，3 个测试全部通过。

- [ ] **Step 5: 提交工具层**

Run:

```bash
git -C /Users/bytedance/test_platform/playwright-platform-web add src/utils/artifact.ts src/utils/artifact.test.ts
git -C /Users/bytedance/test_platform/playwright-platform-web commit -m "feat: add artifact display utilities"
```

Expected: 生成只包含附件格式化工具的提交。

## Task 2: 增强任务详情页附件展示

**Files:**
- Modify: `playwright-platform-web/src/views/task/TaskDetailView.vue`
- Modify: `playwright-platform-web/src/views/task/TaskDetailView.test.ts`

- [ ] **Step 1: 先写页面失败测试**

Update `playwright-platform-web/src/views/task/TaskDetailView.test.ts`:

```ts
const taskStoreState = reactive({
  current: {
    id: 1,
    sceneId: 2,
    repoId: 3,
    status: 'SUCCESS',
    triggerType: 'MANUAL',
    branch: 'main',
    durationMs: 1234,
    runnerName: 'centralized-runner',
    reportUrl: 'http://localhost:9000/report/1/index.html',
    logUrl: null,
    artifactCount: 2,
    hasArtifacts: true,
    reportReady: true,
  },
  report: {
    taskId: 1,
    reportUrl: 'http://localhost:9000/report/1/index.html',
  },
  artifacts: [
    {
      id: 1,
      taskId: 1,
      artifactType: 'REPORT_FILE',
      bucket: 'qa',
      objectKey: 'runs/1/artifacts/index.html',
      size: 512,
      url: 'http://localhost:9000/index.html',
    },
    {
      id: 2,
      taskId: 1,
      artifactType: 'REPORT_FILE',
      bucket: 'qa',
      objectKey: 'runs/1/artifacts/data/trace.zip',
      size: 1536,
      url: 'http://localhost:9000/trace.zip',
    },
  ],
  fetchDetail: vi.fn(async () => undefined),
})
```

```ts
it('should render artifact display metadata and actions', async () => {
  const wrapper = mount(TaskDetailView, {
    global: {
      stubs: {
        'el-card': { template: '<div><slot name="header" /><slot /></div>' },
        'el-button': { template: '<button><slot /></button>' },
        'el-empty': { template: '<div>{{ description }}</div>', props: ['description'] },
        'el-table': { template: '<div><slot /></div>' },
        'el-table-column': { template: '<div><slot :row="row" /></div>', props: ['row'] },
        'el-link': { template: '<a :href="href"><slot /></a>', props: ['href'] },
      },
    },
  })

  await wrapper.vm.$nextTick()

  expect(wrapper.text()).toContain('报告文件')
  expect(wrapper.text()).toContain('index.html')
  expect(wrapper.text()).toContain('trace.zip')
  expect(wrapper.text()).toContain('512 B')
  expect(wrapper.text()).toContain('1.5 KB')
  expect(wrapper.text()).toContain('打开')
  expect(wrapper.text()).toContain('下载')
})
```

Append an empty-state test:

```ts
it('should render improved artifact empty state', async () => {
  taskStoreState.artifacts = []
  taskStoreState.current = {
    ...taskStoreState.current!,
    artifactCount: 0,
    hasArtifacts: false,
  }

  const wrapper = mount(TaskDetailView, {
    global: {
      stubs: {
        'el-card': { template: '<div><slot name="header" /><slot /></div>' },
        'el-button': { template: '<button><slot /></button>' },
        'el-empty': { template: '<div>{{ description }}</div>', props: ['description'] },
        'el-table': { template: '<div><slot /></div>' },
        'el-table-column': { template: '<div><slot :row="row" /></div>', props: ['row'] },
        'el-link': { template: '<a :href="href"><slot /></a>', props: ['href'] },
      },
    },
  })

  await wrapper.vm.$nextTick()

  expect(wrapper.text()).toContain('任务未产出附件，或附件仍在归档中')
})
```

- [ ] **Step 2: 跑测试确认页面尚未满足新展示**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-web
npm run test -- src/views/task/TaskDetailView.test.ts
```

Expected: FAIL，提示缺少中文类型、大小、下载入口或新空态文案。

- [ ] **Step 3: 写最小页面实现**

Update `playwright-platform-web/src/views/task/TaskDetailView.vue`:

```ts
import { formatArtifactType, formatFileSize, getArtifactFileName } from '../../utils/artifact'
```

```ts
function openArtifact(url?: string | null) {
  if (url) {
    window.open(url, '_blank', 'noopener')
  }
}

function downloadArtifact(url?: string | null, objectKey?: string) {
  if (!url) {
    return
  }
  const link = document.createElement('a')
  link.href = url
  link.download = getArtifactFileName(objectKey ?? '')
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}
```

Replace the artifact table section with:

```vue
<el-table :data="artifacts" empty-text="任务未产出附件，或附件仍在归档中">
  <el-table-column label="类型" width="140">
    <template #default="{ row }">
      {{ formatArtifactType(row.artifactType) }}
    </template>
  </el-table-column>
  <el-table-column label="文件名" min-width="180">
    <template #default="{ row }">
      {{ getArtifactFileName(row.objectKey) }}
    </template>
  </el-table-column>
  <el-table-column label="大小" width="120">
    <template #default="{ row }">
      {{ formatFileSize(row.size) }}
    </template>
  </el-table-column>
  <el-table-column prop="objectKey" label="对象路径" min-width="260" />
  <el-table-column label="操作" width="180">
    <template #default="{ row }">
      <el-button link type="primary" :disabled="!row.url" @click="openArtifact(row.url)">打开</el-button>
      <el-button link type="success" :disabled="!row.url" @click="downloadArtifact(row.url, row.objectKey)">下载</el-button>
    </template>
  </el-table-column>
</el-table>
```

- [ ] **Step 4: 跑测试验证页面通过**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-web
npm run test -- src/views/task/TaskDetailView.test.ts src/utils/artifact.test.ts
```

Expected: PASS，工具层和页面测试全部通过。

- [ ] **Step 5: 提交页面增强**

Run:

```bash
git -C /Users/bytedance/test_platform/playwright-platform-web add src/views/task/TaskDetailView.vue src/views/task/TaskDetailView.test.ts
git -C /Users/bytedance/test_platform/playwright-platform-web commit -m "feat: improve task artifact display and actions"
```

Expected: 生成任务详情页附件增强提交。

## Task 3: 做前端联调验证

**Files:**
- Verify: `playwright-platform-web/src/views/task/TaskDetailView.vue`

- [ ] **Step 1: 跑前端相关测试**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-web
npm run test -- src/views/task/TaskDetailView.test.ts src/utils/artifact.test.ts
```

Expected: PASS，相关测试全部通过。

- [ ] **Step 2: 启动前端并打开页面联调**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-web
npm run dev -- --host 0.0.0.0 --port 4173
```

Expected: 开发服务器启动成功。

- [ ] **Step 3: 打开任务详情页验证附件展示**

Manual check against the running page:

1. 进入任务详情页。
2. 确认看到 `报告文件` 类型文案。
3. 确认看到文件名和格式化大小。
4. 确认“打开”和“下载”按钮存在且未禁用。

- [ ] **Step 4: 提交联调完成状态**

Run:

```bash
git -C /Users/bytedance/test_platform/playwright-platform-web status --short
```

Expected: 只剩本轮前端相关改动，便于继续后续开发或提交。

## Self-Review Checklist

- [ ] 设计文档中的“中文类型、文件名、大小、打开、下载、空态文案”都已在任务 1-3 覆盖。
- [ ] 计划中未出现 `TODO`、`TBD`、`类似 Task N` 之类占位内容。
- [ ] 方法名和文件名保持一致：`formatArtifactType()`、`formatFileSize()`、`getArtifactFileName()`、`downloadArtifact()`。
