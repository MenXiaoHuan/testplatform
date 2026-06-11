# Playwright Platform Flow Design

## 背景

当前平台已经具备仓库、场景、任务、报告的基本链路，但现有数据模型和页面交互仍混合了两套思路：

- 一套是“仓库定义默认执行方式，场景只补充运行参数”的结构化配置思路。
- 另一套是“仓库和场景分别持有完整命令字符串，运行时直接执行”的字符串透传思路。

这导致以下问题：

- 场景字段偏多，存在 `testSelectorType`、`projectName`、`runCommand` 等冗余字段。
- 前端已经开始隐藏部分字段，但后端执行链路尚未完成语义收敛。
- 报告只有外链入口，没有单独的任务报告视图。
- 历史任务缺少足够的执行快照，后续难以准确回溯“当时实际跑了什么”。

本设计以“仓库 -> 场景 -> 任务 -> 报告”的明确业务流程为中心，对前后端数据结构、接口语义和页面职责进行统一收敛。

## 目标

- 让仓库只负责定义“这个代码仓库如何运行 Playwright”。
- 让场景只负责定义“在该仓库上，本次如何筛选和运行测试”。
- 让任务完整记录一次真实执行的快照。
- 让报告在第一阶段具备独立的任务级摘要界面，而不是只打开外链。
- 为后续扩展 case 级报告、trace 深度查看保留清晰演进路径。

## 非目标

- 第一阶段不实现 trace.zip 在线解析。
- 第一阶段不实现 case 级报告详情页。
- 第一阶段不实现复杂的 grep/tag 选择能力。
- 第一阶段不实现多套执行器或多运行环境编排。

## 已确认约束

- 场景中的“精细化匹配用例”第一阶段只支持“路径/目录”。
- 匹配路径相对于仓库配置的 `testRoot`。
- 如果场景未填写匹配值，则默认执行 `testRoot` 下全部测试文件。
- 报告界面第一阶段只做任务级摘要，不做 trace 深度解析。
- 测试执行命令以仓库统一入口为准，场景不允许自由输入命令。
- 浏览器先按固定枚举收敛，优先支持 `chromium`、`firefox`、`webkit`。

## 当前链路问题

### 仓库层

- `packageManager` 当前主要是记录信息，不参与真实执行决策。
- `nodeVersion` 已不适合作为用户输入项，建议由后端统一默认。
- `installCommand` 和 `runCommandTemplate` 是真正影响执行的核心配置，应保留。

### 场景层

- `testSelectorType` 在第一阶段不再需要，因为只支持路径/目录匹配。
- `projectName` 不再作为用户显式输入项。
- `runCommand` 不应继续作为场景持久字段，而应在执行前动态生成。
- `testSelectorValue` 的业务语义需要重命名，以避免遗留实现语义混淆。

### 任务层

- 当前任务记录状态、时间、报告地址，但对“本次到底如何执行”的快照不完整。
- 如果继续仅依赖场景当前配置，后续编辑场景会污染历史任务的可解释性。

### 报告层

- 当前只有 `reportUrl` 外链，没有单独的前端任务报告视图。
- 后端已经具备任务、用例、附件数据基础，但前端没有形成任务级报告聚合展示。

## 目标业务流程

### 1. 配置仓库

仓库配置定义项目级默认执行方式：

- 仓库名称
- Git 地址
- 默认分支
- 测试目录
- 报告目录
- 安装命令
- 测试执行命令

其中：

- `测试目录` 用于场景匹配值的相对基准目录。
- `报告目录` 用于测试完成后报告归档。
- `安装命令` 用于依赖安装。
- `测试执行命令` 是统一的 Playwright 运行入口。

### 2. 配置场景

场景配置定义一次可复用的测试运行意图：

- 关联仓库
- 场景名称
- 场景描述
- 浏览器
- 环境变量 JSON
- 用例路径/目录匹配值
- 是否启用
- 是否启用定时
- Cron 表达式

规则：

- `用例路径/目录匹配值` 可为空。
- 为空时，默认运行仓库 `testRoot` 下全部测试。
- 不为空时，按相对于 `testRoot` 的文件或目录执行。

### 3. 触发任务

任务是一次真实执行记录：

- 拉取仓库代码
- 切换到目标分支
- 执行依赖安装
- 根据仓库 + 场景配置动态生成最终运行命令
- 执行 Playwright 测试
- 生成并归档报告
- 解析视频、截图、trace、日志等附件

### 4. 查看报告

第一阶段任务报告页展示：

- 任务基本信息
- 执行状态与时间
- 运行命令快照
- 浏览器与环境变量快照
- 外部报告入口
- 视频、截图、trace、日志附件列表

## 数据模型调整

### 仓库模型

#### 保留字段

- `id`
- `name`
- `gitUrl`
- `defaultBranch`
- `testRoot`
- `reportRelativePath`
- `installCommand`
- `runCommandTemplate`
- `enabled`

#### 废弃或移除字段

- `packageManager`
- `nodeVersion`

#### 后端内部保留字段

以下字段可以继续保留为后端内部执行和归档配置，但不作为第一阶段前端核心编辑项：

- `resultsIndexRelativePath`
- `artifactRootRelativePath`

### 场景模型

#### 保留字段

- `id`
- `repoId`
- `name`
- `description`
- `browser`
- `envJson`
- `matchValue`
- `enabled`
- `scheduleEnabled`
- `cronExpression`
- `lastRunAt`
- `lastTaskStatus`

#### 废弃或移除字段

- `branch`
- `testSelectorType`
- `testSelectorValue`
- `projectName`
- `runCommand`

说明：

- `branch` 不再作为场景编辑项，直接继承仓库默认分支，必要时由任务快照记录。
- `testSelectorValue` 应迁移为更清晰的 `matchValue`。
- `runCommand` 不再持久化到场景，改为执行时动态生成。

### 任务模型

#### 保留字段

- `id`
- `sceneId`
- `repoId`
- `status`
- `triggerType`
- `triggerUser`
- `startedAt`
- `finishedAt`
- `durationMs`
- `runnerName`
- `reportUrl`
- `logUrl`

#### 建议新增快照字段

- `resolvedBranch`
- `resolvedBrowser`
- `resolvedEnvJson`
- `resolvedMatchValue`
- `resolvedTestRoot`
- `resolvedRunCommand`
- `reportReady`
- `artifactCount`

说明：

- 任务应成为一次执行的完整事实快照，而不是简单引用场景当前值。
- `resolvedRunCommand` 用于报告页和问题排查。
- `resolvedMatchValue` 用于说明本次跑的是全部测试、单个文件还是某个目录。

### 报告模型

第一阶段不新增独立 `ReportEntity`，仍然以任务为聚合根。

对前端新增“任务报告摘要”响应模型即可，包含：

- 任务基本信息
- 报告地址
- 任务级附件列表
- 附件统计

## 命令构建规则

后端不再直接执行场景持久化的 `runCommand`，改为按以下规则动态生成最终执行命令：

- 基础命令来自仓库 `runCommandTemplate`
- 默认分支来自仓库 `defaultBranch`
- 浏览器来自场景 `browser`
- 环境变量来自场景 `envJson`
- 匹配值来自场景 `matchValue`
- 如果 `matchValue` 为空，则不追加目标过滤参数
- 如果 `matchValue` 非空，则按“相对测试目录的文件或目录”追加目标参数

第一阶段建议约束：

- 浏览器仅允许固定枚举
- 匹配值仅支持路径/目录
- 不支持 grep/tag
- 不支持场景自定义命令片段

## 接口调整建议

### 仓库接口

#### 前端提交字段

- `name`
- `gitUrl`
- `defaultBranch`
- `testRoot`
- `reportRelativePath`
- `installCommand`
- `runCommandTemplate`
- `enabled`

#### 后端兼容策略

- 第一阶段后端可继续兼容接收 `packageManager`、`nodeVersion`
- 如果未传则自动补默认值
- 第二阶段完成数据库与代码清理后彻底移除

### 场景接口

#### 新增/调整字段

- 新增或重命名：`matchValue`
- 保留：`browser`、`envJson`、`enabled`、`scheduleEnabled`、`cronExpression`

#### 移除字段

- `testSelectorType`
- `projectName`
- `runCommand`
- `branch`

#### 兼容策略

- 第一阶段后端对旧字段继续容忍，但不再作为主要语义字段
- 旧数据读取时，若存在 `testSelectorValue`，迁移映射到 `matchValue`

### 任务接口

建议新增任务报告摘要接口：

- `GET /api/tasks/{taskId}/report-summary`

返回内容建议包括：

- 任务状态与耗时
- 快照字段
- `reportUrl`
- 附件列表
- 附件统计

现有接口可以继续保留：

- `POST /api/tasks/scenes/{sceneId}/run`
- `GET /api/tasks`
- `GET /api/tasks/scenes/{sceneId}`
- `GET /api/tasks/{taskId}`
- `GET /api/tasks/{taskId}/report`
- `GET /api/tasks/{taskId}/artifacts`

## 前端页面改造

### 仓库页

#### 表单字段

- 保留：仓库名称、Git 地址、默认分支、测试目录、报告目录、安装命令、测试执行命令
- 移除：包管理器、Node 版本

#### 文案优化

- `执行命令模板` 改为 `测试执行命令`

### 场景页

#### 表单字段

- 保留：所属仓库、场景名称、场景描述、浏览器、环境参数 JSON、用例路径/目录、启用定时、Cron 表达式
- 移除：选择方式、Playwright Project

#### 交互规则

- `用例路径/目录` 支持为空
- 为空时展示辅助说明“默认执行测试目录下全部测试”
- 占位提示使用相对路径示例，如 `login.spec.ts`、`regression/`

### 任务页

任务列表页继续保留，主要承接：

- 查看执行历史
- 跳转任务详情
- 跳转任务报告页

### 报告页

第一阶段新增独立任务报告页，展示：

- 任务状态摘要
- 执行快照
- 报告入口
- 视频、截图、trace、日志附件列表

不做：

- case 级详情页
- trace 在线展开解析
- 失败步骤时间线

## 后端改造重点

### 场景收敛

- 场景实体、DTO、接口从“命令持久化”改为“执行参数持久化”
- 让 `matchValue` 成为唯一匹配字段

### 执行组装

- 在任务执行服务中新增命令组装逻辑
- 在入库前把最终执行参数写入任务快照字段

### 任务摘要

- 保持任务是报告、附件、日志的聚合根
- 在任务详情或报告摘要接口中直接返回前端需要的聚合结果

### 报告聚合

- 第一阶段继续复用现有报告归档和附件归档能力
- 通过新的摘要接口减少前端多接口拼装复杂度

## 数据迁移建议

### 仓库数据

- 保留历史列，后端写默认值兼容
- 前端不再编辑 `packageManager`、`nodeVersion`

### 场景数据

- 新增 `matchValue` 列，或在服务层把旧 `testSelectorValue` 映射为新语义
- 对历史数据做一次迁移：
  - `matchValue = testSelectorValue`
  - `browser` 为空时补默认枚举值

### 任务数据

- 新增快照列后，新任务按新模型写入
- 历史任务允许快照字段为空，前端做兼容展示

## 分阶段实施

### 阶段 1：字段与页面收敛

- 前端仓库页移除包管理器和 Node 版本
- 前端场景页只保留结构化参数
- 后端接口兼容旧字段，但以内聚后的新语义为准

### 阶段 2：执行链路重构

- 后端移除场景 `runCommand` 主逻辑
- 任务执行时动态组装命令
- 任务落库补齐执行快照

### 阶段 3：任务报告页

- 前端新增任务报告页
- 后端新增报告摘要接口
- 统一展示报告入口和任务级附件

### 阶段 4：旧字段清理

- 清理 `packageManager`
- 清理 `nodeVersion`
- 清理 `testSelectorType`
- 清理 `projectName`
- 清理 `runCommand`

## 风险与注意事项

- 旧接口和旧数据需要一段时间兼容，否则会影响历史页面和已有数据。
- 如果执行命令模板格式不统一，后端命令组装会变得脆弱，因此需要先约束仓库命令入口。
- 浏览器改为固定枚举后，需要明确和 Playwright project 的对应关系，避免后续回滚到自由字符串。
- 任务快照字段必须优先落地，否则报告页很难解释执行结果来源。

## 当前阶段仍需用户补充的信息

当前设计已足够进入实施计划编写，第一阶段没有阻塞性信息缺口。

后续实施前，只建议再确认两项产品约束：

- 浏览器枚举最终是否只保留 `chromium/firefox/webkit`
- 测试执行命令模板是否统一收敛到 Playwright framework 的固定入口

如果这两项保持当前默认假设，则可以直接进入实施计划。
