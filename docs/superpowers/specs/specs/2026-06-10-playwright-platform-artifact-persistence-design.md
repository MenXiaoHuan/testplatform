# Playwright Platform Artifact Persistence Design

## Background

截至当前版本，平台已经具备以下能力：

1. 可执行场景并生成任务。
2. 可归档 `playwright-report` 静态报告目录。
3. 可通过任务列表、任务详情和报告接口返回预签名 `reportUrl`。

当前缺口也很明确：

1. 任务执行成功后不会扫描本地产物文件。
2. 平台不会把报告目录中的文件写入 `artifact` 表。
3. `GET /api/tasks/{id}/artifacts` 缺少真实执行产物来源。

本设计聚焦补齐“真实 artifact 采集和落库”的最小闭环，不扩展到 Playwright 原始结果解析或 `case_result` 级别的附件关联。

## Goals

本轮设计目标如下：

1. 在任务执行成功后扫描报告目录中的真实文件。
2. 将扫描到的文件上传到 MinIO。
3. 为每个已上传文件写入一条 `artifact` 元数据记录。
4. 让 `GET /api/tasks/{id}/artifacts` 返回真实任务产物，而非空列表。
5. 保持现有 `reportUrl` 预签名访问行为不变。

## Non-goals

以下内容不在本轮范围内：

1. 不解析 Playwright 原始 JSON 结果。
2. 不新增 `case_result` 落库逻辑。
3. 不做 trace、video、screenshot 的细粒度类型识别。
4. 不改动前端页面结构。
5. 不引入异步任务、消息队列或批处理框架。

## Chosen Approach

采用“任务级最小闭环”方案：

1. 任务成功后，仅扫描仓库配置中的 `reportRelativePath`。
2. 将目录下所有常规文件按相对路径上传到对象存储。
3. 为每个文件生成一条 `artifact` 记录，统一使用任务级类型。
4. 继续保留 `task.reportUrl` 作为报告首页入口。

选择该方案的原因：

1. 与当前系统能力最贴合，改动集中在后端执行链路。
2. 能最快打通采集、上传、落库、查询、访问的完整链路。
3. 为后续扩展 `test-results`、`case_result` 关联保留清晰边界。

## Scope Boundary

### Included

1. 报告目录扫描。
2. 文件上传 MinIO。
3. `artifact` 表落库。
4. 任务查询时返回预签名附件 URL。
5. 自动化测试和烟雾验证。

### Excluded

1. 报告内容解析。
2. 用例级附件归属。
3. 失败重试机制。
4. 附件去重。
5. 前端筛选、分组或图标增强。

## Design

### 1. New Service Boundary

新增一个任务产物归档服务，职责单一：

1. 接收 `taskId`、本地工作目录、报告相对目录。
2. 遍历本地报告目录中的所有文件。
3. 将每个文件上传到 MinIO。
4. 组装并保存 `ArtifactEntity`。

该服务不负责：

1. 创建任务。
2. 执行命令。
3. 解析报告内容。
4. 生成预签名 URL。

### 2. Execution Flow

任务执行链路调整为：

1. `TaskServiceImpl.createAndRun()` 创建任务并准备工作区。
2. 安装依赖成功后执行场景命令。
3. 测试命令成功后继续归档报告首页目录。
4. 调用任务产物归档服务扫描并上传报告目录文件。
5. 将产物记录写入 `artifact` 表。
6. 更新任务结束时间、耗时和状态。

只有当测试命令执行成功时，才进入 artifact 采集步骤。

### 3. Scan Rule

扫描规则保持最小化：

1. 扫描根目录为 `workspace.resolve(reportRelativePath)`。
2. 仅处理常规文件，不处理目录本身。
3. 保留相对路径层级。
4. 若目录不存在，则直接返回空列表，不抛出异常。

示例：

1. 本地文件 `playwright-report/index.html`
2. 对象键 `runs/<taskId>/artifacts/index.html`

1. 本地文件 `playwright-report/data/trace.zip`
2. 对象键 `runs/<taskId>/artifacts/data/trace.zip`

### 4. Storage Mapping

对象存储路径约定为：

1. 报告首页继续沿用 `runs/<taskId>/report/index.html`
2. artifact 文件统一使用 `runs/<taskId>/artifacts/<relativePath>`

这样拆分的原因：

1. `report` 路径继续服务报告首页入口。
2. `artifacts` 路径统一服务列表检索和下载访问。
3. 后续即使接入 `test-results`，也可继续在 `artifacts` 下分层扩展。

### 5. Database Mapping

每个上传文件生成一条 `artifact` 记录，字段约定如下：

1. `taskId`：当前任务 ID。
2. `caseResultId`：本轮固定为空。
3. `artifactType`：固定为 `REPORT_FILE`。
4. `bucket`：当前平台配置的 bucket。
5. `objectKey`：`runs/<taskId>/artifacts/<relativePath>`。
6. `contentType`：从文件类型探测结果写入，无法识别时允许为空。
7. `size`：本地文件字节数。
8. `url`：上传完成后保存对象原始地址，读取时再转预签名 URL。

`REPORT_FILE` 是本轮新增的任务级产物类型，用于明确区分它不是未来的 `TRACE`、`VIDEO` 或 `SCREENSHOT`。

### 6. Read Behavior

读取侧保持当前设计：

1. `listArtifacts()` 仍然从数据库查询。
2. 若记录中存在 `bucket` 和 `objectKey`，则动态生成预签名 GET URL。
3. 前端无需改造，继续直接使用返回的 `url` 字段。

### 7. Failure Policy

失败策略采用“原子优先”原则：

1. 报告目录不存在：不视为 artifact 采集失败，返回空列表。
2. 扫描目录时发生异常：任务标记失败。
3. 任一文件上传失败：任务标记失败。
4. 任一 `artifact` 落库失败：任务标记失败。

原因如下：

1. 本轮目标是建立真实、可信的附件索引。
2. 若允许部分成功，会导致对象存储与数据库元数据不一致。
3. 对于 MVP，明确失败比隐式数据缺失更容易排查。

## Implementation Impact

预计涉及以下后端组件：

1. `task-service`
   - 在任务执行链路中接入 artifact 采集。
2. `storage-service`
   - 复用现有单文件上传能力。
3. `artifact-repository`
   - 保存真实记录。

预计新增或调整的实现点：

1. 新增任务产物归档服务接口与实现。
2. 调整 `TaskServiceImpl.createAndRun()` 的成功后处理顺序。
3. 为 `TaskExecutionServiceTest` 增加新的失败测试与成功测试。

## Testing Strategy

### 1. Unit Tests

新增服务层测试，验证：

1. 报告目录存在多个文件时，会逐个上传并落库。
2. 生成的 `artifactType`、`bucket`、`objectKey`、`size` 正确。
3. 目录不存在时，不落库且不报错。
4. 上传失败时，任务状态为 `FAILED`。

### 2. Contract Tests

保持现有接口契约并补充断言：

1. `GET /api/tasks/{id}/artifacts` 返回真实记录。
2. 返回的 `url` 为预签名地址。

### 3. Smoke Test

使用最小本地仓库进行运行时验证：

1. 执行命令生成 `playwright-report/index.html` 与额外静态文件。
2. 任务成功后调用 `GET /api/tasks/{id}/artifacts`。
3. 验证列表非空，且链接可访问。

## Future Evolution

本设计为后续扩展留出以下演进路径：

1. 新增 `testResultsRelativePath` 后扫描原始结果目录。
2. 解析 Playwright 结果文件并写入 `case_result`。
3. 根据文件命名或结果元数据，将附件类型细化为 `TRACE`、`VIDEO`、`SCREENSHOT`。
4. 将 `caseResultId` 从空值升级为真实关联。

## Acceptance Criteria

完成本轮后，应满足以下验收条件：

1. 成功任务至少能为报告目录中的真实文件生成 `artifact` 记录。
2. `GET /api/tasks/{id}/artifacts` 对真实任务返回非空结果。
3. 返回的每条 artifact 都包含可用的预签名访问链接。
4. 报告入口 `reportUrl` 行为与当前版本保持兼容。
5. 自动化测试覆盖成功、空目录和失败场景。
