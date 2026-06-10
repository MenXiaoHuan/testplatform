# Playwright Platform MVP Design

## Background

当前仓库 `playwright_framework` 的定位已经比较清晰：

1. 它是一个 Playwright 测试框架仓库。
2. 它负责沉淀统一 Runner、codegen、Allure 报告和调试证据能力。
3. 它适合作为业务测试仓库模板，或作为被调度执行的测试仓库示例。

但用户当前要解决的问题，已经不再是“继续增强这个单仓库的测试能力”，而是要做一个平台，支撑更通用的团队使用场景：

1. 接入任意符合约定的 Playwright 测试仓库。
2. 选择指定 Git 分支中的 `tests` 用例。
3. 创建可复用的“场景”。
4. 手动触发场景执行。
5. 执行结束后，在任务列表里随时查看报告和附件。

这说明新的目标是“测试平台产品化”，而不是“继续往测试仓库里叠管理后台功能”。

## Goals

本轮设计目标如下：

1. 定义一个独立的 Playwright 平台项目边界。
2. 让平台支持接入多个 Playwright 测试仓库，而不是只服务当前仓库。
3. 支持基于 Git 仓库和分支的用例执行。
4. 支持“场景”作为执行配置的抽象。
5. 支持“任务”作为一次真实执行记录的抽象。
6. 支持任务完成后查看 Allure 报告、Trace、视频、截图等结果。
7. 为后续演进到对象存储、多执行机、历史趋势分析预留边界。

## Non-goals

以下内容不属于一期 MVP：

1. 不做复杂权限体系。
2. 不做多租户隔离。
3. 不做失败聚类、趋势分析、质量大盘。
4. 不做分布式多节点 Agent 调度。
5. 不要求兼容非 Playwright 测试框架。
6. 不要求在平台内编辑测试代码。

## High-Level Decision

结论如下：

1. 当前 `playwright_framework` 仓库继续保留为测试框架仓库。
2. 平台单独新建两个项目：
   - 前端项目：Vue 3
   - 后端项目：Spring Boot
3. 平台一期采用中心化 Runner 模型。
4. 测试代码来源使用 Git 仓库 + 分支。
5. 报告和附件采用“数据库元数据 + 对象存储文件本体”的分层模型。

## Project Boundaries

### 1. Current Repository Responsibility

当前仓库只负责：

1. 提供 Playwright 测试能力。
2. 提供统一执行入口。
3. 提供报告生成能力。
4. 作为平台可接入的测试仓库示例。

当前仓库不负责：

1. 仓库接入管理。
2. 任务调度。
3. 平台用户界面。
4. 报告中心索引。
5. 多仓库、多分支任务编排。

### 2. New Platform Responsibility

平台负责：

1. 管理测试仓库配置。
2. 管理场景配置。
3. 创建与调度执行任务。
4. 归档执行产物。
5. 对外提供任务列表、场景列表、报告入口。

## Proposed Repositories

建议拆分为以下项目：

### 1. `playwright-platform-web`

技术栈：

1. Vue 3
2. TypeScript
3. Vite
4. Pinia
5. Vue Router
6. Element Plus

职责：

1. 平台前端页面。
2. 仓库、场景、任务、报告入口的用户交互。

### 2. `playwright-platform-server`

技术栈：

1. Java 21
2. Spring Boot 3.x
3. MySQL 8
4. Redis
5. MinIO

职责：

1. 提供 API。
2. 负责任务调度。
3. 管理 Git 仓库执行链路。
4. 归档报告与附件。
5. 管理场景、任务、报告元数据。

### 3. Playwright Test Repositories

这类仓库保持独立，包括当前 `playwright_framework` 在内。

职责：

1. 维护 `tests/`、`pages/`、`utils/` 等测试代码。
2. 提供标准化执行入口。
3. 在 Runner 机器上被平台拉取并执行。

## Architecture

### 1. Frontend

平台前端负责以下页面：

1. 仓库管理页
2. 场景管理页
3. 场景创建页
4. 任务列表页
5. 任务详情页
6. 报告入口页

前端只做管理和展示，不直接执行测试命令。

### 2. Backend

平台后端拆分为以下模块：

1. `repo-service`
   - 管理 Playwright 仓库配置。

2. `scene-service`
   - 管理场景定义。

3. `task-service`
   - 创建任务、更新任务状态、查询任务详情。

4. `runner-service`
   - 在中心化 Runner 上执行 Git 拉取、依赖安装、测试运行。

5. `report-service`
   - 归档报告静态包。
   - 解析报告摘要和用例结果。

6. `artifact-service`
   - 管理 trace、视频、截图、文本附件等对象存储元数据。

### 3. Runner

一期使用中心化 Runner，含义是：

1. 平台后端控制一组固定执行机。
2. 每次任务执行时：
   - 拉取指定 Git 仓库
   - checkout 指定分支
   - 安装依赖
   - 执行指定命令
   - 生成 Allure 报告
   - 上传报告和附件

Runner 可以先和后端部署在一起，但在代码设计上应保留未来独立拆分能力。

## Repository Integration Contract

为了让平台支持“所有 Playwright 框架编写的测试用例”，平台和测试仓库之间需要定义最小接入契约。

建议一期契约如下：

1. 测试仓库必须可通过标准命令安装依赖。
   - 例如 `npm install`

2. 测试仓库必须提供标准执行命令。
   - 例如 `node ./scripts/run-e2e.cjs`
   - 或 `npx playwright test`

3. 测试仓库必须约定报告输出目录。
   - 推荐 `./reports/allure-report`

4. 测试仓库必须约定测试根目录。
   - 例如 `./tests`

5. 测试仓库必须约定 Node 版本和包管理器。

平台不负责理解每个仓库的内部实现，只负责按契约调度。

## Core Domain Model

### 1. Repository

表示一个可被平台接入的 Playwright 仓库。

建议字段：

1. `id`
2. `name`
3. `gitUrl`
4. `defaultBranch`
5. `packageManager`
6. `installCommand`
7. `runCommandTemplate`
8. `testRoot`
9. `reportRelativePath`
10. `nodeVersion`
11. `enabled`

### 2. Scene

场景是“可复用的执行配置”，不是一次运行结果。

建议字段：

1. `id`
2. `repoId`
3. `name`
4. `branch`
5. `testSelectorType`
   - `file`
   - `directory`
   - `grep`
6. `testSelectorValue`
7. `projectName`
8. `browser`
9. `envJson`
10. `runCommand`
11. `enabled`

场景示例：

1. 仓库：`playwright_framework`
2. 分支：`main`
3. 测试文件：`tests/interview_agent/login/login.spec.ts`
4. 项目：`chromium`
5. 命令：`node ./scripts/run-e2e.cjs --target tests/interview_agent/login/login.spec.ts --project chromium`

### 3. Task

任务表示一次真实执行。

建议字段：

1. `id`
2. `sceneId`
3. `repoId`
4. `status`
   - `PENDING`
   - `RUNNING`
   - `SUCCESS`
   - `FAILED`
   - `CANCELED`
5. `triggerType`
6. `triggerUser`
7. `branch`
8. `commitSha`
9. `startedAt`
10. `finishedAt`
11. `durationMs`
12. `runnerName`
13. `reportUrl`
14. `logUrl`

### 4. Case Result

表示从报告中提取出的单条用例结果。

建议字段：

1. `id`
2. `taskId`
3. `historyId`
4. `fullName`
5. `suite`
6. `story`
7. `status`
8. `durationMs`
9. `owner`
10. `severity`
11. `projectName`

### 5. Artifact

表示用例附件或报告附件。

建议字段：

1. `id`
2. `taskId`
3. `caseResultId`
4. `artifactType`
   - `TRACE`
   - `VIDEO`
   - `SCREENSHOT`
   - `TEXT`
   - `REPORT`
3. `bucket`
4. `objectKey`
5. `contentType`
6. `size`
7. `url`

## Storage Model

### 1. Database

数据库负责存结构化元数据：

1. 仓库
2. 场景
3. 任务
4. 用例结果
5. 附件索引

数据库不负责存大文件本体。

### 2. Object Storage

对象存储负责存：

1. `reports/allure-report` 静态包
2. `trace.zip`
3. `video.webm`
4. `png` 截图
5. `txt` 文本附件

建议对象路径：

1. `runs/<taskId>/report/index.html`
2. `runs/<taskId>/report/data/...`
3. `runs/<taskId>/artifacts/<caseResultId>/trace.zip`
4. `runs/<taskId>/artifacts/<caseResultId>/video.webm`

一期对象存储推荐 MinIO。

## Execution Flow

### 1. Create Scene

1. 用户在平台上新增一个仓库。
2. 用户选择某个分支。
3. 用户指定要执行的 `tests` 文件、目录或 grep 规则。
4. 用户保存为一个“场景”。

### 2. Run Scene

1. 用户点击执行场景。
2. 平台创建任务记录，状态为 `PENDING`。
3. Runner 获取任务并切换为 `RUNNING`。
4. Runner 拉取 Git 仓库并 checkout 指定分支。
5. Runner 安装依赖。
6. Runner 执行场景定义的命令。
7. Runner 生成 Allure 报告。
8. Runner 上传报告静态包和附件到对象存储。
9. 平台解析报告摘要与用例结果。
10. 平台更新任务状态。

### 3. View Report

1. 用户打开任务列表。
2. 点击某条任务进入详情页。
3. 平台展示：
   - 任务状态
   - 执行时长
   - 分支
   - commit
   - 场景名称
   - 用例结果摘要
4. 用户点击“查看报告”跳转 Allure 静态报告 URL。
5. 用户点击附件时，由平台提供对象存储访问链接。

## UI Scope

一期前端建议只做以下页面：

### 1. Repository List

展示：

1. 仓库名称
2. Git 地址
3. 默认分支
4. 状态

### 2. Scene List

展示：

1. 场景名称
2. 所属仓库
3. 分支
4. 测试选择器
5. 最近一次执行状态

### 3. Scene Form

支持：

1. 选择仓库
2. 输入分支
3. 选择执行类型：
   - 文件
   - 目录
   - grep
4. 输入命令模板

### 4. Task List

展示：

1. 任务 ID
2. 场景名称
3. 执行状态
4. 开始时间
5. 耗时
6. 报告入口

### 5. Task Detail

展示：

1. 基础信息
2. 用例列表
3. 附件列表
4. 报告入口

## API Scope

一期建议提供如下核心 API：

1. 仓库管理
   - `POST /api/repos`
   - `GET /api/repos`
   - `PUT /api/repos/{id}`

2. 场景管理
   - `POST /api/scenes`
   - `GET /api/scenes`
   - `GET /api/scenes/{id}`
   - `PUT /api/scenes/{id}`

3. 任务管理
   - `POST /api/scenes/{id}/run`
   - `GET /api/tasks`
   - `GET /api/tasks/{id}`

4. 报告与附件
   - `GET /api/tasks/{id}/report`
   - `GET /api/tasks/{id}/artifacts`

## Error Handling

一期必须明确处理以下失败场景：

1. Git 克隆失败
2. 分支不存在
3. `npm install` 失败
4. 测试命令执行失败
5. 报告未生成
6. 对象存储上传失败

错误处理原则：

1. 任务状态必须能明确区分失败阶段。
2. 失败后要保留命令日志。
3. 失败时也尽量保留已生成的中间报告产物。

## Security and Operational Constraints

一期先做最小安全约束：

1. 只允许平台配置白名单 Git 仓库。
2. 命令模板不能允许任意 shell 注入。
3. Runner 机器与对象存储凭证由后端统一管理。
4. 平台不直接暴露 Git 凭证。

## MVP Delivery Strategy

建议分三阶段推进：

### Phase 1. Back-End First MVP

目标：

1. 跑通仓库接入、场景创建、任务执行、报告归档。
2. 前端先只做基础管理页。

### Phase 2. Report Center

目标：

1. 任务详情页可查看用例结果摘要。
2. 可打开 Allure 报告。
3. 可查看附件下载入口。

### Phase 3. Platform Hardening

目标：

1. 增加 Runner 管理。
2. 增加失败原因归类。
3. 增加历史任务过滤与检索。

## Recommended MVP Scope

一期真正要做的最小集合为：

1. 仓库管理
2. 场景管理
3. 手动执行场景
4. 任务列表
5. 任务详情
6. 报告入口
7. MinIO 附件归档

以下内容延后：

1. 定时任务
2. 自动回归编排
3. 多 Runner
4. 权限系统
5. 趋势分析

## Expected Outcome

完成该方案后，平台将具备以下能力：

1. 接入多个 Playwright 测试仓库。
2. 选择指定 Git 分支中的 `tests` 用例。
3. 把执行配置抽象为场景并重复使用。
4. 执行场景并生成任务记录。
5. 在任务列表中查看执行状态和报告入口。
6. 通过对象存储长期保存报告与附件。

同时，当前 `playwright_framework` 仓库仍能保持清晰职责：

1. 继续专注于测试框架能力。
2. 不被平台前后端逻辑污染。
3. 继续作为被平台调度执行的标准 Playwright 仓库示例。

## Current Progress

截至 2026-06-10，MVP 基础骨架已经完成，当前状态如下：

1. `playwright-platform-server` 已支持仓库、场景、任务的基础管理流程。
2. 后端已支持任务报告与附件查询，并完成本地 MySQL 启动验证。
3. `playwright-platform-web` 已完成管理台壳子、基础路由和核心页面骨架。
4. 前端已经接通后端 `/api` 代理，具备基础联调能力。
5. 后续重点转为接口收口、前端交互完善、MinIO 联通验证和交付文档补齐。
