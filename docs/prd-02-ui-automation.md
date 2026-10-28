# PRD-02 · UI 自动化测试模块

> 版本 v1.0 · 2026-10-28 · **对应现有代码模块**：`repository` / `scene` / `task` / `runner` / `storage` / `schedule`
> 
> 本 PRD 聚焦已完成的 UI 自动化模块，梳理其完整需求闭环。后续「接口自动化」「性能测试」作为同一能力线上的独立 PRD。

---

## 一、需求目标

### 1.1 为什么做

传统 Playwright UI 自动化测试分散在不同仓库、不同机器、不同命令行环境中。执行记录、日志、截图、视频、Trace 和结果文件也无法集中追踪。项目组需要一个统一平台来：

| 问题 | 平台解决什么 |
|------|-------------|
| 测试仓库散落各处 | 统一管理仓库配置（Git 地址、凭据、执行命令） |
| 每次手动 `git pull && npm ci && npx playwright test` | 平台一键执行，含定时触发 |
| 测试产物（截图/视频/Trace）散落文件系统 | 自动归档到 MinIO，统一查看/下载/分享 |
| 多测试人员协作时相互干扰 | Space 多租户隔离 + RBAC 权限 |
| 测试失败排查依赖 ssh 进机器看日志 | 页面直接看阶段日志 + AI 智能助手辅助分析 |

### 1.2 范围边界（明确做什么 / 不做什么）

| 做 | 不做 |
|----|------|
| Node.js / TypeScript / JavaScript `@playwright/test` 项目 | Python Playwright、Java/.NET Playwright、Cucumber、CodeceptJS |
| Docker Runner 短生命周期容器执行 | 宿主机直接执行（local runner 已废弃）、K8s Runner |
| 公有仓库 + 私有仓库（HTTPS Token / SSH Deploy Key） | OAuth 集成 GitLab/GitHub App、Webhook 触发 |
| 手动触发 + Cron 定时触发 + Agent 对话触发 | GitHub Actions 触发集成、Jenkins Pipeline 集成 |
| Playwright JSON reporter 结果解析 | Allure Report、JUnit XML、自定义报告格式 |
| MinIO 产物归档 + Trace 分享链接 | 在线 Trace Viewer（直接下载 Playwright trace.zip 再用 Playwright CLI 打开） |
| 任务取消 + 重试 | 失败后自动回滚代码、自动修复依赖 |

### 1.3 成功标准

| 指标 | 目标 |
|------|------|
| 仓库接入 | 5 分钟内完成一个现有 Playwright 仓库的配置并首次执行 |
| 执行可靠性 | Docker Runner 启动成功率 ≥ 99%；Playwright 命令正常执行率 ≥ 95% |
| 产物归档 | 任务完成后 30s 内所有产物（截图/视频/Trace/日志）可在页面查看和下载 |
| 定时调度 | Cron 触发与实际执行偏差 ≤ 30s；租约机制防止并发重复触发 |
| 任务取消 | 手动取消后 10s 内 Docker 容器停止并清理；任务状态变为 CANCELED |

---

## 二、核心对象模型

### 2.1 对象关系

```
Space（多租户隔离边界）
  └── TestRepository（测试仓库配置）
        └── Scene（测试场景 = 仓库 + 分支 + 选择器 + 浏览器 + 环境变量 + Cron）
              └── Task（一次真实执行记录，带状态机）
                    ├── CaseResult（每条 Playwright 用例结果）
                    ├── Artifact（产物元数据；文件本体在 MinIO）
                    └── TaskStageLog（执行阶段日志索引；文件本体在 MinIO）
```

### 2.2 TestRepository（测试仓库）

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | varchar(128) | 仓库名称，Space 内唯一 |
| `git_url` | varchar(512) | Git clone 地址 |
| `auth_type` | enum | `PUBLIC` / `HTTPS_TOKEN` / `SSH_KEY` |
| `credential_secret` | blob | AES-256-GCM 密文；**API 永不回显明文或密文** |
| `default_branch` | varchar(128) | 默认分支 |
| `working_directory` | varchar(256) | 工作目录（Monorepo 子目录） |
| `install_command` | varchar(256) | 安装命令，建议 `npm ci` |
| `run_command_template` | varchar(512) | 执行命令模板；支持 `{target}` / `{project}` / `{testRoot}` 占位符 |
| `test_root` | varchar(256) | 测试目录相对路径 |
| `results_index_relative_path` | varchar(256) | Playwright JSON reporter 输出路径 |
| `artifact_root_relative_path` | varchar(256) | Playwright 产物目录 |

### 2.3 Scene（测试场景）

| 字段 | 类型 | 说明 |
|------|------|------|
| `repo_id` | bigint | 关联的仓库 |
| `branch` | varchar(128) | 执行分支（可覆盖仓库默认） |
| `test_selector_type` | enum | `GLOB` / `REGEX` / `TITLE` |
| `test_selector_value` | varchar(512) | Playwright test selection 参数值 |
| `project_name` | varchar(64) | Playwright `--project` 参数 |
| `browser` | varchar(64) | 浏览器类型（chromium/firefox/webkit） |
| `env_json` | json | 环境变量 |
| `schedule_enabled` | tinyint | 是否开启定时 |
| `cron_expression` | varchar(64) | Cron 表达式（Spring cron 格式） |

### 2.4 Task（任务状态机）

```
QUEUED ──► RUNNING ──► SUCCESS
    │        │
    │        ├──► FAILED
    │        └──► CANCELED（用户手动取消或 Runner 超时）
    │
    └──► FAILED（Executor 拒绝 / 系统忙）
```

**Task 记录的关键快照字段**（在 `TaskCreationService` 创建时写入，避免场景后续修改影响历史记录）：

| 快照字段 | 说明 |
|----------|------|
| `resolved_branch` | 执行时实际使用的分支 |
| `resolved_browser` | 实际浏览器 |
| `resolved_env_json` | 实际环境变量 |
| `resolved_run_command` | 实际执行的完整命令 |
| `resolved_test_root` | 实际测试目录 |
| `commit_sha` | 拉取代码后解析的 commit SHA |

**为什么要快照？** 场景配置后续可能被修改，但历史任务必须可重现。

### 2.5 执行阶段（Task.current_stage）

```
PREPARING_WORKSPACE   → 准备 workspace 目录
CLONING_REPO          → git clone + checkout
INSTALLING_DEPS       → 执行 install_command
RUNNING_TESTS         → 执行 run_command_template
PARSING_RESULTS       → 解析 Playwright JSON reporter
ARCHIVING_ARTIFACTS   → 上传截图/视频/Trace/日志到 MinIO
CLEANING_UP           → 清理 Docker 容器和 workspace
```

每个阶段的 stdout/stderr 写入独立的 `TaskStageLog`（文件存 MinIO，MySQL 只存 object_key + preview_text + line_count）。

---

## 三、核心流程

### 3.1 手动执行流程

```
用户点击「执行场景」
  │
  ▼
POST /api/spaces/{spaceId}/scenes/{sceneId}/run
  │
  ├── 1. Scene 权限校验（用户在 space 内角色 ≥ DEVELOPER）
  ├── 2. TaskCreationService 创建 task，状态 QUEUED，写快照字段
  ├── 3. 提交到 taskExecutionExecutor（全局线程池，大小可配）
  │
  ▼
TaskExecutionOrchestrator.executeTask()
  │
  ├── 线程池满？── 是 ──► 更新 task FAILED + result_code=SYSTEM_BUSY
  │
  ├── PREPARING_WORKSPACE → 创建 .runner-workspaces/task-{id}/
  ├── CLONING_REPO → git clone；私有仓库临时注入凭据（不进命令行）
  │     ├── HTTPS_TOKEN：写临时 GIT_ASKPASS + 环境变量
  │     └── SSH_KEY：私钥写 0600 临时文件 + GIT_SSH_COMMAND
  ├── INSTALLING_DEPS → DockerRunnerCommandExecutor 启动容器执行
  ├── RUNNING_TESTS ──────── 同上
  ├── PARSING_RESULTS → 读 results_index_relative_path → 解析为 case_result 记录
  ├── ARCHIVING_ARTIFACTS → artifact_root_relative_path 下文件逐个上传 MinIO
  └── CLEANING_UP → docker stop + docker rm；删除临时凭据文件
```

### 3.2 定时调度流程

```
Spring @Scheduled(fixedDelay=30s) 扫描到期场景
  │
  ▼
SceneSchedulerServiceImpl
  │
  ├── 查 scene_schedule_state 表，对每个到期场景获取租约（version + lease_until 乐观锁）
  │     └── 多实例部署时保证只有一个实例真正触发（租约机制）
  │
  ├── 租约抢到 → 创建 schedule_event（幂等键 = scene_id + planned_fire_at）
  │     └── 已存在 → 跳过，避免重复触发
  │
  └── 复用手动执行链路创建 SCHEDULED 类型 task
```

### 3.3 任务取消流程

```
用户点击「取消任务」
  │
  ▼
POST /api/spaces/{spaceId}/tasks/{taskId}/cancel
  │
  ├── 写 task.cancel_requested=1 + cancel_requested_at + cancel_requested_by
  │
  ▼
TaskExecutionOrchestrator 各阶段检查 cancel_requested
  │
  ├── RUNNING_TESTS 阶段感知 → docker stop + docker rm
  └── 后续阶段看到 cancel_requested → 跳过，直接进入 CLEANING_UP
  │
  ▼
Task 更新状态 CANCELED
```

### 3.4 Trace 分享流程

```
用户在任务详情页点击「分享 Trace」
  │
  ▼
POST /api/spaces/{spaceId}/tasks/{taskId}/artifacts/{traceArtifactId}/trace-share
  │
  ├── 创建一条公开可访问的分享记录（随机 token + 过期时间）
  └── 返回分享 URL：/api/public/traces/download?token=xxx
  │
  ▼
接收方打开 URL → 直接下载 Playwright trace.zip
  └── 不需要登录，不需要 space 权限
```

---

## 四、API 接口

### 4.1 仓库管理

| 接口 | 方法 | 权限 | 输入 | 输出 |
|------|------|------|------|------|
| 仓库列表 | `GET /api/spaces/{spaceId}/repos` | 成员 | spaceId | Repo[] |
| 创建仓库 | `POST /api/spaces/{spaceId}/repos` | DEVELOPER+ | name, gitUrl, authType, credential, defaultBranch, installCommand, runCommandTemplate, testRoot, resultsIndexPath, artifactRootPath | Repo |
| 仓库详情 | `GET /api/spaces/{spaceId}/repos/{repoId}` | 成员 | spaceId, repoId | Repo（**不含明文/密文凭据**，只有 credentialConfigured=true/false） |
| 更新仓库 | `PUT /api/spaces/{spaceId}/repos/{repoId}` | DEVELOPER+ | 同创建（凭据留空 = 保留原值；auth_type 切 PUBLIC = 清除凭据） | Repo |
| 删除仓库 | `DELETE /api/spaces/{spaceId}/repos/{repoId}` | ADMIN | spaceId, repoId | void（级联删 Scene） |

### 4.2 场景管理

| 接口 | 方法 | 权限 | 输入 | 输出 |
|------|------|------|------|------|
| 场景列表 | `GET /api/spaces/{spaceId}/scenes` | 成员 | spaceId, repoId? | Scene[] |
| 创建场景 | `POST /api/spaces/{spaceId}/scenes` | DEVELOPER+ | repoId, name, branch, testSelectorType, testSelectorValue, browser, envJson, scheduleEnabled, cronExpression | Scene |
| 更新场景 | `PUT /api/spaces/{spaceId}/scenes/{sceneId}` | DEVELOPER+ | 同创建 | Scene |
| 删除场景 | `DELETE /api/spaces/{spaceId}/scenes/{sceneId}` | ADMIN | spaceId, sceneId | void |
| **执行场景** | `POST /api/spaces/{spaceId}/scenes/{sceneId}/run` | DEVELOPER+ | spaceId, sceneId | **Task（QUEUED 状态 + taskId）** |

### 4.3 任务管理

| 接口 | 方法 | 权限 | 输入 | 输出 |
|------|------|------|------|------|
| 任务列表 | `GET /api/spaces/{spaceId}/tasks` | 成员 | spaceId, sceneId?, status? | Task[] |
| 任务详情 | `GET /api/spaces/{spaceId}/tasks/{taskId}` | 成员 | spaceId, taskId | Task + 用例统计 + 产物统计 |
| 取消任务 | `POST /api/spaces/{spaceId}/tasks/{taskId}/cancel` | DEVELOPER+ | spaceId, taskId | void |
| **诊断信息** | `GET /api/spaces/{spaceId}/tasks/{taskId}/diagnostics` | 成员 | spaceId, taskId | 执行环境诊断（Runner 日志摘要、Playwright stderr 尾部） |
| 用例列表 | `GET /api/spaces/{spaceId}/tasks/{taskId}/cases` | 成员 | spaceId, taskId, status? | CaseResult[] |
| 阶段日志 | `GET /api/spaces/{spaceId}/tasks/{taskId}/logs` | 成员 | spaceId, taskId | TaskStageLog[] |
| 下载阶段日志 | `GET /api/spaces/{spaceId}/tasks/{taskId}/logs/{logId}/download` | 成员 | spaceId, taskId, logId | 文件流（从 MinIO 代理下载） |
| 产物列表 | `GET /api/spaces/{spaceId}/tasks/{taskId}/artifacts` | 成员 | spaceId, taskId | Artifact[] |
| 下载产物 | `GET /api/spaces/{spaceId}/tasks/{taskId}/artifacts/{artifactId}/download` | 成员 | spaceId, taskId, artifactId | 文件流 |
| **Trace 分享** | `POST /api/spaces/{spaceId}/tasks/{taskId}/artifacts/{artifactId}/trace-share` | 成员 | spaceId, taskId, artifactId | { token, url, expiresAt } |
| 公开 Trace 下载 | `GET /api/public/traces/download` | **无需登录** | token | trace.zip 文件流 |

### 4.4 调度事件

| 接口 | 方法 | 权限 | 输入 | 输出 |
|------|------|------|------|------|
| 调度事件列表 | `GET /api/spaces/{spaceId}/schedule-events` | 成员 | spaceId, scheduleType?(CRON/AGENT/MANUAL), status? | ScheduleEvent[] |
| 重试调度事件 | `POST /api/spaces/{spaceId}/schedule-events/{eventId}/retry` | DEVELOPER+ | spaceId, eventId | void |

---

## 五、技术约束

### C1 执行隔离

所有 Playwright 命令**必须**在短生命周期 Docker 容器中执行。禁止在后端服务宿主机直接执行用户仓库命令（local runner 已废弃）。

### C2 凭据安全

- 私有仓库凭据用 AES-256-GCM 加密，密钥来自 `PLATFORM_CREDENTIAL_KEY`（32-byte base64）
- API 不返回明文、不返回密文，只返回 `credentialConfigured=true/false`
- clone 完成后立即删除临时 askpass 脚本和 SSH 私钥文件
- 日志和错误消息统一经过 `SensitiveDataRedactor`

### C3 长任务线程池

- HTTP 请求线程**不得**执行 Playwright 命令
- 自定义 `taskExecutionExecutor` 线程池承载长任务
- 线程池满载时 fail-fast（task → FAILED + SYSTEM_BUSY），不无限排队

### C4 事务边界

- 任务执行全过程**不**包大事务（外部 IO 无法回滚）
- 状态变更用短事务独立提交
- Playwright 安装、测试执行、MinIO 上传**均不在数据库事务内**

### C5 结果解析格式

平台**只**支持 Playwright JSON reporter（`--reporter=json`），结果文件路径固定为仓库配置中的 `results_index_relative_path`。Allure、JUnit XML 等格式**不做**。

### C6 Runner 资源限制

Docker Runner 启动时必须配置：
- 内存上限：默认 2g（`PLATFORM_RUNNER_DOCKER_MEMORY`）
- CPU 上限：默认 2（`PLATFORM_RUNNER_DOCKER_CPUS`）
- workspace：`PLATFORM_RUNNER_HOST_WORKSPACE_ROOT` 挂载到容器内 `/workspace/task`

### C7 数据隔离

所有仓库、场景、任务、用例结果、产物**必须**通过 `space_id` 关联 Space。跨 Space 查询后端直接 403。

---

## 六、运行方式

### 6.1 本地开发环境

```bash
# 启动所有服务（MySQL + Redis + MinIO + server + web）
docker compose up -d --build

# 前端：http://localhost:5173
# 后端：http://localhost:8080
```

非 Docker 方式（不推荐）：
```bash
# 先手动启动 MySQL/Redis/MinIO，然后导出 .env 里的变量
cd playwright-platform-server && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd playwright-platform-web && npm ci && npm run dev
```

### 6.2 生产部署

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

生产 Compose 特性：
- 后端用多阶段 `Dockerfile`（Maven 打包 jar + JRE 运行）
- 前端用 Nginx 托管 `dist`
- MySQL、Redis、MinIO 只在 Docker 内部网络暴露
- 所有服务 `restart: unless-stopped`

### 6.3 Runner 配置注入

Runner 相关配置全部通过 `.env` 注入：

```env
PLATFORM_RUNNER_DOCKER_IMAGE=mcr.microsoft.com/playwright:v1.61.1-noble
PLATFORM_RUNNER_DOCKER_NETWORK=bridge
PLATFORM_RUNNER_DOCKER_MEMORY=2g
PLATFORM_RUNNER_DOCKER_CPUS=2
PLATFORM_RUNNER_HOST_WORKSPACE_ROOT=/opt/testplatform/.runner-workspaces
```

---

## 七、验收方式

| 验收项 | 说明 |
|--------|------|
| AC1 仓库接入 | 创建公有仓库 → git clone 成功 → 执行 `npm ci` → 执行 `npx playwright test` → 任务 SUCCESS |
| AC2 私有仓库 HTTPS Token | 创建私有仓库配 HTTPS Token → clone 成功；**抓包/日志里看不到 token 明文** |
| AC3 私有仓库 SSH Deploy Key | 创建私有仓库配 SSH Key → clone 成功；容器内无残留私钥文件 |
| AC4 场景执行快照 | 场景 branch 改成 v1.0 → 触发执行 → 任务 resolved_branch 为 v1.0 → 场景再改 v2.0 → 查看旧任务仍显示 v1.0 |
| AC5 用例解析 | 3 个 Playwright 用例（1 pass + 1 fail + 1 skip）→ case_result 表 3 条记录，status 对应 |
| AC6 产物归档 | 任务 SUCCESS → MinIO 中存在 screenshot、video、trace、log 文件 → artifact 表元数据正确 |
| AC7 Trace 分享 | 创建分享 token → 用另一浏览器（退出登录）打开分享 URL → 直接下载 trace.zip |
| AC8 定时调度 | 创建场景配 cron `0 * * * *` → 10 分钟内自动触发 10 条任务 → schedule_event 表有 10 条记录 |
| AC9 租约幂等 | 模拟多实例同时扫描 → 每个 `scene_id + planned_fire_at` 只有 1 条 schedule_event |
| AC10 任务取消 | RUNNING 状态任务 → 点击取消 → 10s 内任务 CANCELED + Docker 容器消失 |
| AC11 线程池满拒 | 同时提交超过 `taskExecutionExecutor` 大小的任务 → 多余任务直接 FAILED + SYSTEM_BUSY（不无限排队） |
| AC12 跨 Space 隔离 | Space A 的成员 → 调 Space B 的仓库列表接口 → 403 |

---

## 八、测试方式

### 8.1 单元测试（后端，`mvn test`）

| 模块 | 覆盖重点 |
|------|----------|
| RepositoryService | 创建/更新/删除 + 凭据加密 + 切换 auth_type 时清除凭据 |
| SceneService | CRUD + cron 表达式校验 |
| TaskService | 创建快照字段 + cancel_requested 写入 |
| CredentialCipher | AES-256-GCM 加解密正确 |
| RunnerCommandBuilder | 占位符替换正确；Playwright CLI 自动追加 target/project |
| SensitiveDataRedactor | HTTPS Token / SSH 私钥被替换为 `***` |

### 8.2 集成测试

| 场景 | 覆盖重点 |
|------|----------|
| DockerRunnerCommandExecutor | 启动容器 → 执行命令 → 收集 stdout/stderr → 退出码正确 |
| 完整手动执行链路 | 创建仓库 → 创建场景 → 执行 → 解析结果 → 归档产物 |
| 完整定时调度链路 | 创建场景配 cron → 等待触发 → 租约机制生效 |
| 取消链路 | RUNNING 中取消 → 容器停止 → 状态 CANCELED |
| 凭据安全 | clone 过程中抓包无明文 token；临时文件 clone 后删除 |

### 8.3 手动测试 Checklist

- [ ] 创建公有仓库 → 执行成功 → 查看产物
- [ ] 创建私有仓库配 HTTPS Token → 执行成功
- [ ] 创建私有仓库配 SSH Deploy Key → 执行成功
- [ ] 场景配 cron → 自动触发
- [ ] 执行中取消 → 容器停止
- [ ] 任务 SUCCESS → 点「分享 Trace」→ 退出登录 → 打开分享链接 → 下载
- [ ] 跨 Space 访问 → 403
- [ ] 任务详情看阶段日志 → 能看到各阶段 stdout/stderr
- [ ] 任务详情看用例列表 → PASS/FAIL/SKIP 状态正确

### 8.4 CI 自动执行

GitHub Actions 在每次 `push` 和 `pull_request` 时：
1. 后端 `mvn test` + JaCoCo 覆盖率
2. 前端 `npm test -- --coverage` + `npm run build` + `npm audit`

---

## 九、已知限制与后续迭代

| 当前限制 | 后续规划 |
|----------|----------|
| 只支持 `@playwright/test` | 扩展到 Pytest + Playwright、Cypress、Selenium |
| 只支持 Docker Runner | 扩展到 K8s Runner（多节点并行执行） |
| Playwright CLI 自动追加 `--target` | 用户可以选择禁用自动追加 |
| 定时调度只有 Cron | 增加 GitHub Webhook、Git push 触发 |
| 产物归档到 MinIO | 增加在线 Trace Viewer（集成 `playwright show-trace` 后端服务） |
| 失败后手动重试 | 增加失败重试策略（自动重试 N 次 + 指数退避） |
