# Playwright Platform Case Result Design

## Background

截至当前阶段，平台已经具备以下能力：

1. 可执行场景并生成任务。
2. 可归档 `playwright-report` 静态报告目录。
3. 可把报告和任务级附件上传到 MinIO，并通过预签名 URL 返回给前端。
4. 任务详情页已经能展示任务级摘要和附件列表。

当前仍存在一个明显缺口：

1. 平台只能看见“任务执行产生了哪些文件”，还看不见“具体有哪些测试用例、各自状态如何、每条附件属于哪个用例”。
2. `case_result` 表和 `artifact.case_result_id` 已经预留，但还没有真实解析和写入逻辑。
3. 当前附件仍是任务级扫描，`artifactType` 固定为 `REPORT_FILE`，无法表达 `TRACE`、`VIDEO`、`SCREENSHOT` 等更细粒度语义。

本设计聚焦补齐“Playwright 原生产物驱动的 case_result 解析与附件归属闭环”，优先完成后端能力，不展开前端页面实现。

## Goals

本轮设计目标如下：

1. 基于 Playwright 原生产物解析测试用例结果并写入 `case_result` 表。
2. 将 `trace`、`video`、`screenshot` 等附件关联到具体 `case_result`。
3. 保持现有任务级报告入口与任务级附件读取能力继续可用。
4. 新增后端查询接口，为后续前端展示用例结果做准备。
5. 在真实运行链路中验证解析、落库、附件关联和预签名读取全部打通。

## Non-goals

以下内容不在本轮范围内：

1. 不基于 Allure 结果做用例结果解析。
2. 不修改前端任务详情页以展示用例结果列表。
3. 不做失败重试统计、趋势统计、历史对比等高级分析。
4. 不做批量下载、附件预览器、截图缩略图等前端体验增强。
5. 不要求所有附件都必须识别并绑定到具体用例；无法可靠归属的附件允许降级为任务级附件。

## Scope Boundary

### Included

1. Playwright 原始结果索引文件产出约定。
2. Playwright 原始附件目录保留约定。
3. `case_result` 解析与落库。
4. `artifact.case_result_id` 真实写值。
5. 附件类型细分为 `TRACE`、`VIDEO`、`SCREENSHOT`、`REPORT_FILE`。
6. 后端结果查询接口与自动化测试。

### Excluded

1. Allure 结果兼容解析。
2. 跨任务聚合统计。
3. 用例重试详情的完整链路建模。
4. 前端页面改造。

## Current Constraints

当前代码和框架约束会直接影响设计：

1. `playwright-platform-server` 目前只会扫描 `reportRelativePath`，不会扫描 `test-results` 或 Playwright 原始附件目录。
2. `playwright_framework` 现有 `playwright.config.ts` 只配置了 `list` 和 `allure-playwright` reporter，没有稳定的机器可读结果索引文件。
3. `playwright_framework/scripts/lib/run-e2e-core.cjs` 在测试和报告都成功后会清理 `./.playwright-artifacts`，这会导致后端在任务结束后无法再解析和上传原始附件。

因此，本轮后端闭环不仅依赖服务端解析逻辑，还依赖一条明确的平台运行契约：平台模式下必须保留原始结果索引和原始附件目录。

## Chosen Approach

采用“Playwright 原始结果索引 + 原始附件目录”双输入方案：

1. 平台运行模式下，由测试仓库产出一份稳定的 Playwright JSON 结果索引文件。
2. 平台运行模式下，保留 Playwright 原始附件目录，不在成功后立刻清理。
3. 后端先解析 JSON 结果索引，得到用例结果和附件引用。
4. 再根据解析结果上传附件文件，并将每条附件绑定到对应 `case_result`。

选择该方案的原因：

1. 用例结果和附件归属来自同一份结构化源，稳定性最高。
2. 附件关联不依赖文件名猜测，避免误绑。
3. 后续即使前端展示变复杂，底层结果模型也不需要重做。

## Runtime Contract

### 1. Platform Mode

新增平台运行模式约定：

1. 平台执行 Playwright 时注入 `PLAYWRIGHT_PLATFORM_MODE=true`。
2. 当该环境变量存在时，测试仓库必须：
   - 产出一份机器可读的结果索引文件。
   - 保留原始附件目录，直到平台后端完成归档。

### 2. Result Index File

本轮统一约定结果索引文件路径为：

1. `test-results/.playwright-results.json`

该文件是 Playwright JSON reporter 的输出文件，作为 `case_result` 解析主输入。

### 3. Attachment Root

本轮统一约定原始附件目录路径为：

1. `.playwright-artifacts`

原因如下：

1. 当前 `playwright_framework/playwright.config.ts` 已将 `outputDir` 固定为 `./.playwright-artifacts`。
2. 与现有框架保持一致，能减少仓库侧额外改造。
3. Playwright JSON 结果中的附件路径可以稳定映射到该目录下的真实文件。

### 4. Framework Adjustment

为了满足以上运行契约，`playwright_framework` 需要配合做两项改造：

1. 在 `PLAYWRIGHT_PLATFORM_MODE=true` 时，为 reporter 额外打开 JSON 输出，并写到 `./test-results/.playwright-results.json`。
2. 在 `PLAYWRIGHT_PLATFORM_MODE=true` 时，跳过 `run-e2e-core.cjs` 中对 `./.playwright-artifacts` 的成功后清理。

如果测试仓库不遵守此契约，则本轮 `case_result` 解析视为不支持。

## Data Model

### 1. Test Repository Configuration

为避免把路径约定写死在后端代码里，本轮新增两个仓库级配置字段：

1. `resultsIndexRelativePath`
   - 默认值：`test-results/.playwright-results.json`
   - 含义：Playwright 结构化结果索引文件的相对路径
2. `artifactRootRelativePath`
   - 默认值：`.playwright-artifacts`
   - 含义：Playwright 原始附件根目录的相对路径

这样设计的原因：

1. 支持后续不同仓库按统一协议进行小范围定制。
2. 避免把框架实现细节散落在服务端常量里。
3. 便于未来接入非当前模板仓库时做平滑迁移。

### 2. Case Result

本轮继续复用现有 `case_result` 表与 `CaseResultEntity`，不新增新表。

每条解析结果至少写入：

1. `taskId`
2. `historyId`
3. `fullName`
4. `suiteName`
5. `storyName`
6. `status`
7. `durationMs`
8. `projectName`

字段映射原则：

1. `historyId`
   - 优先使用 Playwright 结果中的稳定测试标识。
   - 若结果中缺失稳定标识，则退化为 `projectName + fullName` 派生值。
2. `fullName`
   - 使用能唯一表达用例路径的完整名称。
3. `suiteName`
   - 使用最接近顶层 describe 或文件分组的名称。
4. `storyName`
   - 使用最接近叶子场景名称的可读标题。

本轮不扩展 `case_result` 表来保存完整错误堆栈；失败详情继续以任务日志和报告为主入口。

### 3. Artifact

继续复用现有 `artifact` 表与 `ArtifactEntity`。

本轮关键变化如下：

1. `artifact.case_result_id` 从预留字段升级为真实写值。
2. `artifactType` 从单一 `REPORT_FILE` 扩展为：
   - `REPORT_FILE`
   - `TRACE`
   - `VIDEO`
   - `SCREENSHOT`

判定规则：

1. 优先使用 Playwright 附件元数据中的 `contentType`、`name`、`path`。
2. 当元数据不足时，使用文件扩展名辅助判定。
3. 无法可靠判定的文件继续降级为 `REPORT_FILE` 或保留任务级附件，不强制映射到用例级。

## Service Design

### 1. New Service Boundary

本轮新增两个后端服务边界：

1. `TaskCaseResultParseService`
   - 职责：解析 Playwright JSON 结果索引，生成用例结果和附件归属映射。
2. `TaskCaseResultPersistenceService`
   - 职责：保存 `case_result`，并将持久化后的 `caseResultId` 反向映射给附件归档阶段。

现有 `TaskArtifactArchiveService` 升级为消费“附件归属映射”，不再自己猜测用例关系。

### 2. Parse Output Model

`TaskCaseResultParseService` 返回一份内存中的中间结果，至少包含：

1. `parsedCases`
   - 表示准备落库的用例结果集合
2. `artifactBindings`
   - 键：原始附件文件路径
   - 值：归属到哪条解析结果以及应使用什么 `artifactType`

这样做的原因：

1. 将“理解 Playwright 结果”的复杂度隔离在解析服务内部。
2. 让持久化服务和附件归档服务只处理清晰的结构化结果。
3. 便于单元测试独立验证解析逻辑。

### 3. Parsing Strategy

解析流程如下：

1. 读取 `resultsIndexRelativePath` 指向的 JSON 文件。
2. 遍历其中的 suite / spec / test / result 结构。
3. 为每条最终需要展示的测试结果构造一条 `ParsedCaseResult`。
4. 从该测试结果的附件列表中提取 `path`、`contentType`、`name`。
5. 将附件路径归一化到工作区相对路径，用于后续上传和绑定。

状态映射采用最小闭环策略：

1. `passed` -> `PASSED`
2. `failed` -> `FAILED`
3. `timedOut` -> `TIMEOUT`
4. `skipped` / `interrupted` -> `SKIPPED`

对于 Playwright 的多次 retry：

1. 本轮仅保留最终有效结果。
2. 不单独落 retry 记录。

## Execution Flow

任务执行链路调整为：

1. `TaskServiceImpl.createAndRun()` 创建任务并准备工作区。
2. 安装依赖成功后执行测试命令。
3. 测试命令成功后，先归档 `reportRelativePath` 对应的静态报告目录。
4. 解析 `resultsIndexRelativePath` 对应的 Playwright JSON 结果索引。
5. 持久化 `case_result` 记录。
6. 归档 `artifactRootRelativePath` 和 `reportRelativePath` 中需要保留的附件文件。
7. 对解析出的 trace / video / screenshot 写入真实 `caseResultId`。
8. 对无法可靠识别归属的文件，保留任务级附件写法。
9. 最后更新任务结束时间、耗时和状态。

其中第 4 到第 8 步构成“结果中心闭环”，任何关键步骤失败都不应被静默吞掉。

## Storage Mapping

### 1. Report Files

现有静态报告路径保持不变：

1. `runs/<taskId>/report/...`

### 2. Raw Attachments

用例级原始附件统一写入：

1. `runs/<taskId>/artifacts/<caseResultId>/<relativePath>`

当附件无法可靠绑定到某条 `case_result` 时，降级路径为：

1. `runs/<taskId>/artifacts/unassigned/<relativePath>`

这样设计的原因：

1. 让对象路径本身就体现“已绑定 / 未绑定”。
2. 便于后续人工排查无法识别的附件。
3. 与当前任务级 artifact 路径保持兼容演进关系。

## API Design

为了形成“后端闭环”，本轮新增两个只读接口：

1. `GET /api/tasks/{taskId}/cases`
   - 返回该任务下的 `case_result` 列表
2. `GET /api/tasks/{taskId}/cases/{caseResultId}/artifacts`
   - 返回属于该用例结果的附件列表

### 1. Case Result Response

每条用例结果返回以下字段：

1. `id`
2. `taskId`
3. `historyId`
4. `fullName`
5. `suiteName`
6. `storyName`
7. `status`
8. `durationMs`
9. `projectName`
10. `artifactCount`

### 2. Artifact Response

继续沿用现有 artifact 返回模型，但读取时对 `url` 统一做预签名转换。

## Failure Policy

失败策略采用“结果可信优先”原则：

1. 测试命令执行失败：任务状态 `FAILED`，不进入 `case_result` 解析。
2. 结果索引文件不存在：任务状态 `FAILED`，并记录明确错误信息。
3. JSON 结果解析失败：任务状态 `FAILED`。
4. `case_result` 落库失败：任务状态 `FAILED`。
5. 已识别且应关联的关键附件上传失败：任务状态 `FAILED`。
6. 单个附件无法识别具体归属：不视为失败，降级为任务级附件。

原因如下：

1. 用例结果是平台后续查询与展示的核心数据。
2. 若结果索引缺失或解析异常却仍把任务标记为成功，会产生错误信号。
3. “无法识别归属”和“无法解析结果”是两个不同级别的问题，必须区分处理。

## Testing Strategy

### 1. Parser Unit Tests

新增解析器单测，验证：

1. 能从最小 Playwright JSON 样例中提取 1 条或多条 `case_result`。
2. 能正确解析 `status`、`durationMs`、`projectName`、`fullName`。
3. 能从附件元数据中识别 `TRACE`、`VIDEO`、`SCREENSHOT`。
4. 能生成路径归一化后的附件归属映射。

### 2. Service Tests

新增任务执行链路测试，验证：

1. 测试成功后会调用结果解析服务。
2. `case_result` 会被真实保存。
3. 附件归档会写入 `caseResultId`。
4. 结果索引缺失或解析失败时，任务状态变为 `FAILED`。

### 3. Contract Tests

新增接口契约测试，验证：

1. `GET /api/tasks/{taskId}/cases` 返回正确的用例结果列表。
2. `GET /api/tasks/{taskId}/cases/{caseResultId}/artifacts` 只返回属于该用例的附件。
3. 附件 `url` 返回预签名地址。

### 4. Smoke Test

使用一个最小 Playwright 仓库做真实联调验证：

1. 运行测试后产出 `test-results/.playwright-results.json`。
2. 运行测试后保留 `.playwright-artifacts`。
3. 后端成功写入 `case_result`。
4. 至少有一条 `TRACE` 或 `SCREENSHOT` 附件被写入并关联到对应 `caseResultId`。
5. 查询接口可返回对应结果与附件。

## Acceptance Criteria

完成本轮后，应满足以下验收条件：

1. 成功任务可基于 Playwright 原始结果索引生成 `case_result` 记录。
2. 至少 `trace / video / screenshot` 三类附件可以按用例结果进行真实关联。
3. 无法识别归属的附件不会被误绑，而是安全降级为任务级附件。
4. 新增后端接口可查询任务下的用例结果列表以及用例级附件。
5. 所有附件读取继续返回可访问的预签名 URL。
6. 自动化测试覆盖解析成功、解析失败、附件归属和接口查询场景。

## Future Evolution

本设计为后续扩展留出以下演进路径：

1. 为 `case_result` 增加失败消息、错误堆栈、重试次数等更细字段。
2. 支持 Allure 结果兼容解析，作为非标准仓库的降级路径。
3. 将 `artifactBindings` 扩展为支持网络日志、HAR、控制台日志等更多附件类型。
4. 在前端任务详情页中增加用例结果列表、状态筛选和用例级附件展示。
