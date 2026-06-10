# Playwright Platform Artifact Frontend Design

## Background

截至当前阶段，后端已经完成真实 artifact 采集与落库：

1. 成功任务会扫描 `reportRelativePath` 目录。
2. 报告文件会上传到 MinIO 并写入 `artifact` 表。
3. `GET /api/tasks/{id}/artifacts` 已返回真实记录和预签名 URL。

当前前端任务详情页已经接入该接口，但附件区域仍然偏原始：

1. 直接显示 `artifactType`、`bucket`、`objectKey`。
2. 只有一个“打开”链接。
3. 没有文件名和文件大小等更直观的信息。
4. 空态说明不够明确。

这导致真实归档已经可用，但页面上的使用体验仍然停留在调试视角，而不是面向平台用户的操作视角。

## Goals

本轮设计目标如下：

1. 提升任务详情页附件列表的可读性。
2. 为每个附件提供“打开”和“下载”入口。
3. 将后端已有的 `artifact` 元数据转换成更友好的前端展示文案。
4. 保持当前页面结构和后端 API 不变。

## Non-goals

以下内容不在本轮范围内：

1. 不改动后端接口结构。
2. 不增加附件筛选和分组功能。
3. 不做批量下载。
4. 不做复制链接、分享链接等增强操作。
5. 不引入新的全局 store 字段或新的页面路由。

## Chosen Approach

采用“在现有表格基础上增强交互”的方案：

1. 保留当前任务详情页双卡片布局。
2. 继续使用 `el-table` 展示附件列表。
3. 增加轻量格式化函数，将原始字段映射成更友好的类型、文件名和大小展示。
4. 在操作列中同时提供“打开”和“下载”。

选择该方案的原因：

1. 复用现有页面结构，风险最低。
2. 不需要改动接口和 store 协议。
3. 能最快把后端已完成的 artifact 归档能力转化为真实可用的页面能力。

## UI Design

### 1. Layout

页面继续保持两块区域：

1. 基础信息卡片。
2. 附件列表卡片。

本轮只增强附件列表卡片，不调整整体布局层级。

### 2. Artifact Table Columns

附件表格调整为以下列：

1. `类型`
   - 将原始 `artifactType` 转换为中文文案。
   - 当前至少支持：
     - `REPORT_FILE` -> `报告文件`
     - 其他未知类型 -> 原值回显

2. `文件名`
   - 从 `objectKey` 中提取 basename。
   - 例如 `runs/2/artifacts/data/trace.zip` 显示为 `trace.zip`。

3. `大小`
   - 使用 `size` 字段格式化为 `B`、`KB`、`MB`。
   - `null` 或 `undefined` 时显示 `-`。

4. `对象路径`
   - 保留原始 `objectKey`，便于排查和对照存储路径。

5. `操作`
   - `打开`：新窗口访问预签名 URL。
   - `下载`：基于同一预签名 URL 触发下载。

### 3. Empty State

当附件列表为空时，表格空态文案从“暂无附件”提升为更明确的提示：

1. `任务未产出附件，或附件仍在归档中`

这样可以更好解释空列表的两种常见原因，而不把问题简单归结为“无数据”。

## Interaction Design

### 1. Open Behavior

“打开”操作保持与当前报告按钮一致：

1. 通过 `window.open(url, '_blank', 'noopener')` 打开预签名地址。
2. 当 `url` 为空时禁用操作。

### 2. Download Behavior

“下载”操作使用浏览器原生下载行为：

1. 动态创建 `<a>` 元素。
2. 设置 `href` 为预签名 URL。
3. 设置 `download` 为推导出的文件名。
4. 触发点击后移除临时节点。

这样不需要新依赖，也不要求后端额外返回下载专用接口。

## Frontend Structure

### 1. View Layer

主要变更文件：

1. `playwright-platform-web/src/views/task/TaskDetailView.vue`

职责：

1. 新增附件格式化展示。
2. 新增打开和下载操作。
3. 优化附件空态文案。

### 2. Utility Layer

建议新增轻量工具文件：

1. `playwright-platform-web/src/utils/artifact.ts`

职责：

1. `formatArtifactType(type: string): string`
2. `formatFileSize(size?: number | null): string`
3. `getArtifactFileName(objectKey: string): string`

原因如下：

1. 避免把格式化逻辑塞进页面组件。
2. 让单元测试更聚焦。
3. 为后续类型扩展留下统一入口。

## Testing Strategy

### 1. Utility Tests

新增工具测试，验证：

1. `REPORT_FILE` 会显示为 `报告文件`。
2. 未知类型会原样返回。
3. 文件大小会正确格式化。
4. 能从 `objectKey` 提取文件名。

### 2. View Tests

更新任务详情页测试，验证：

1. 附件列表显示中文类型文案。
2. 附件列表显示文件名。
3. 附件列表显示格式化大小。
4. 页面上存在“打开”和“下载”入口。
5. 空态文案正确。

## Acceptance Criteria

完成本轮后，应满足以下验收条件：

1. 用户能在任务详情页直接识别附件类型、文件名和大小。
2. 用户能对单个附件执行“打开”和“下载”。
3. 现有后端 API 和 store 数据结构保持兼容。
4. 页面测试覆盖附件展示和操作入口。
