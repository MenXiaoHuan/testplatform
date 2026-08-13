# Playwright Test Platform

一个面向 Playwright 自动化测试的执行与管理平台。平台支持维护测试仓库、配置测试场景、手动或定时触发任务，并统一查看任务状态、用例结果、阶段日志、截图、视频、Trace 等运行产物。内置 AI 智能助手，可自动分析失败原因、排查故障链路、查询全平台数据。

---

## 1. 项目定位

传统 Playwright 测试通常分散在不同仓库、机器和命令行环境中，执行记录、日志、截图、视频、Trace 和结果文件也不容易集中追踪。本项目把这些能力平台化：

- 管理测试仓库配置：Git 地址、默认分支、工作目录、安装命令、测试执行命令、结果文件路径和产物目录。
- 管理测试场景：关联仓库、浏览器、测试选择器、环境变量、Cron 定时规则。
- 执行测试任务：支持手动触发、定时触发、任务取消和重新执行。
- 查看执行结果：任务状态、阶段日志、用例结果、截图、视频、Trace 和报告。
- AI 智能助手：基于 ReAct Agent 的故障排查、业务咨询、任务分析、链路回溯。
- 统一运行环境：通过 Docker Compose 启动 MySQL、Redis、MinIO、后端和前端。

---

## 2. 技术栈

| 分层 | 技术 | 说明 |
|---|---|---|
| 前端 | Vue 3、TypeScript、Vite、Pinia、Element Plus、Axios、Vitest | 后台页面、状态管理、接口请求和单元测试 |
| 后端 | Spring Boot 3.5、Spring Web、MyBatis、Flyway、Spring Data Redis | REST API、业务编排、数据库迁移和缓存 |
| AI Agent | Spring AI Alibaba Agent 1.1.2、ReAct 模式、SSE 流式 | 智能助手、工具调用、上下文管理、全链路追踪 |
| 存储 | MySQL、Redis、MinIO | 结构化数据、详情缓存、对象文件 |
| 执行 | Local Runner、Docker Runner、Playwright Runner 镜像 | 执行外部测试仓库命令 |
| 工程化 | Docker、Docker Compose、GitHub Actions、JaCoCo | 本地开发、单机部署、CI 和覆盖率 |

后端持久层只使用注解式 MyBatis，Flyway 管理 schema，不使用 JPA，也不使用 XML Mapper。

---

## 3. 系统架构

```mermaid
flowchart LR
    User[用户浏览器] --> Web[Vue 3 / Nginx or Vite]
    Web -->|/api| Server[Spring Boot 后端]
    Server -->|MyBatis| MySQL[(MySQL)]
    Server -->|RedisTemplate + Trace| Redis[(Redis)]
    Server -->|MinIO SDK| MinIO[(MinIO)]
    Server --> Runner[RunnerExecutionService]
    Runner --> DockerRunner[Docker Runner 容器]
    DockerRunner --> Repo[外部 Playwright 测试仓库]
    Server -->|/api/ai| Agent[AI Agent 服务]
    Agent -->|ReAct 循环| LLM[LLM API]
    Agent -->|Tools| Server
    Agent -->|Trace Logs| Redis
```

核心设计：

- 前端负责配置录入、任务状态展示、日志和产物查看。
- 后端负责业务规则、任务调度、异步执行编排、结果解析和产物归档。
- AI Agent 基于 Spring AI Alibaba Agent 框架，通过 ReAct 循环自动调用平台工具。
- MySQL 保存用户、空间、仓库、场景、任务、用例结果和产物元数据。
- Redis 缓存仓库详情、场景详情和任务详情，并处理缓存穿透、击穿、雪崩风险；同时存储 Agent 全链路 Trace 日志（90 天 TTL）。
- MinIO 保存截图、视频、Trace、阶段日志、头像等对象文件。
- 长任务交给自定义线程池和 Runner 执行，不阻塞 Spring Web 请求线程。

---

## 4. 目录结构

```text
.
├── README.md
├── docker-compose.yml
├── docker-compose.prod.yml
├── docs
│   ├── deployment.md
│   ├── docker.md
│   ├── guide.md
│   └── agent-architecture.md
├── playwright-platform-server
│   ├── Dockerfile
│   ├── Dockerfile.dev
│   ├── pom.xml
│   └── src
│       └── main
│           ├── java/com/example/platform
│           │   ├── ai/
│           │   │   ├── config/          # Agent 配置 (ReactAgentConfig, SystemPromptConfig)
│           │   │   ├── controller/      # AgentController HTTP 接口
│           │   │   ├── service/         # AgentService 对话主入口
│           │   │   ├── session/         # ChatSession + ChatSessionManager
│           │   │   ├── output/          # 输出解析兜底
│           │   │   ├── tools/           # Agent 工具 (TaskTool, SceneTool, ...)
│           │   │   └── AgentObservability.java
│           │   └── ...                  # 业务模块 (space, repo, scene, task)
│           └── resources/
│               ├── AGENT.md             # 系统提示词
│               └── skills/              # Skills 技能文档
│                   ├── error-analysis/
│                   └── business-knowledge/
└── playwright-platform-web
    ├── Dockerfile
    ├── Dockerfile.dev
    ├── package.json
    └── src
        └── components/ai/              # 前端 AI 对话框组件
```

| 路径 | 作用 |
|---|---|
| `playwright-platform-server` | Spring Boot 后端 |
| `playwright-platform-web` | Vue 3 + Vite 前端 |
| `docker-compose.yml` | 本地开发编排 |
| `docker-compose.prod.yml` | 单机生产部署编排 |
| `docs/deployment.md` | 生产部署手册 |
| `docs/docker.md` | Docker 文件职责说明 |
| `docs/guide.md` | 项目架构与技术讲解指南 |
| `docs/agent-architecture.md` | AI Agent 技术架构详解 |
| `playwright-platform-server/src/main/java/com/example/platform/ai/` | AI Agent 核心代码 |
| `playwright-platform-server/src/main/resources/AGENT.md` | Agent 系统提示词 |
| `playwright-platform-server/src/main/resources/skills/` | Agent Skills 技能文档 |
| `playwright-platform-web/src/components/ai/` | 前端 AI 对话组件 |

---

## 5. 快速启动：Docker Compose

推荐本地直接用 Docker Compose 启动完整开发环境。

### 5.1 环境要求

- Docker Desktop 或 Docker Engine。
- Docker Compose Plugin。
- Git。

如果不使用 Docker Compose，本机还需要安装：

- Java 21。
- Maven 3.9+。
- Node.js 20+。
- npm。
- MySQL 8+。
- Redis 7+。
- MinIO。

### 5.2 创建 `.env`

首次启动前，在项目根目录创建本机私有 `.env` 文件。该文件已被 `.gitignore` 忽略，不会上传到 GitHub。

```env
# Frontend
PLATFORM_WEB_HOST_PORT=5173
PLATFORM_WEB_API_PROXY_TARGET=http://server:8080

# Backend - Server
PLATFORM_SERVER_HOST_PORT=8080

# Backend - MySQL
PLATFORM_DB_NAME=playwright_platform
PLATFORM_DB_URL=jdbc:mysql://mysql:3306/playwright_platform?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true&serverTimezone=UTC
PLATFORM_DB_USERNAME=root
PLATFORM_DB_PASSWORD=<your-db-password>
PLATFORM_MYSQL_HOST_PORT=4306

# Backend - Redis
PLATFORM_REDIS_HOST=redis
PLATFORM_REDIS_PORT=6379
PLATFORM_REDIS_HOST_PORT=7379
PLATFORM_REDIS_PASSWORD=<your-redis-password>

# Backend - MinIO
PLATFORM_MINIO_ENDPOINT=http://minio:9000
PLATFORM_MINIO_PUBLIC_ENDPOINT=http://localhost:10000
PLATFORM_MINIO_API_HOST_PORT=10000
PLATFORM_MINIO_CONSOLE_HOST_PORT=10001
PLATFORM_MINIO_ACCESS_KEY=<your-minio-access-key>
PLATFORM_MINIO_SECRET_KEY=<your-minio-secret-key>
PLATFORM_STORAGE_BUCKET=qa-report

# Backend - Runner
PLATFORM_RUNNER_MODE=docker
PLATFORM_RUNNER_WORKSPACE_ROOT=/runner-workspaces
PLATFORM_RUNNER_HOST_WORKSPACE_ROOT=<absolute-path-to-project>/.runner-workspaces
PLATFORM_RUNNER_DOCKER_IMAGE=mcr.microsoft.com/playwright:v1.44.0-jammy
PLATFORM_RUNNER_DOCKER_NETWORK=bridge
PLATFORM_RUNNER_DOCKER_MEMORY=2g
PLATFORM_RUNNER_DOCKER_CPUS=2
PLATFORM_RUNNER_DOCKER_CONTAINER_WORKSPACE_ROOT=/workspace/task
```

注意：

- 不要把真实密码写进代码库。
- `PLATFORM_MINIO_ENDPOINT` 是后端访问 MinIO 的 Docker 内部地址，保持 `http://minio:9000`。
- `PLATFORM_MINIO_PUBLIC_ENDPOINT` 用于生成公网可访问的 MinIO 预签名地址，本地通常是 `http://localhost:10000`；任务产物下载和 Trace 分享仍优先通过后端平台代理接口返回。
- `PLATFORM_RUNNER_HOST_WORKSPACE_ROOT` 建议填写宿主机绝对路径，例如 `/opt/testplatform/.runner-workspaces`。
- 如果 MySQL、Redis 或 MinIO 已经使用 Docker volume 初始化过，修改 `.env` 密码不会自动修改已有 volume 内账号密码。

### 5.3 启动

```bash
docker compose config
docker compose up -d --build
docker compose ps
```

查看日志：

```bash
docker compose logs -f server
docker compose logs -f web
```

访问地址：

| 服务 | 地址 |
|---|---|
| 前端 | `http://localhost:5173` |
| 后端 | `http://localhost:8080` |
| MySQL | `localhost:4306` |
| Redis | `localhost:7379` |
| MinIO API | `http://localhost:10000` |
| MinIO Console | `http://localhost:10001` |

停止服务：

```bash
docker compose down
```

删除容器和数据卷：

```bash
docker compose down -v
```

`down -v` 会删除 MySQL、Redis、MinIO、Maven 缓存和前端依赖数据卷，谨慎执行。

---

## 6. 生产部署

单机生产部署使用 `docker-compose.prod.yml` 和生产 Dockerfile。

```bash
docker compose -f docker-compose.prod.yml config
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps
```

生产环境特点：

- 后端使用 `playwright-platform-server/Dockerfile`，构建 jar 后用 JRE 运行。
- 前端使用 `playwright-platform-web/Dockerfile`，构建 `dist` 后由 Nginx 托管。
- MySQL、Redis、后端默认只在 Docker 内部网络访问。
- 敏感配置通过服务器私有 `.env` 注入。
- 正式对团队开放时，推荐前后端分域名：
  - 前端：`https://test-platform.example.com`
  - 后端 API：`https://api.test-platform.example.com`

完整部署步骤见 `docs/deployment.md`。

---

## 7. Docker 文件说明

| 文件 | 作用 |
|---|---|
| `playwright-platform-server/Dockerfile.dev` | 后端开发镜像，挂载源码后运行 `mvn spring-boot:run` |
| `playwright-platform-server/Dockerfile` | 后端生产镜像，多阶段构建 jar，运行阶段只保留 JRE |
| `playwright-platform-server/.dockerignore` | 排除后端构建产物、IDE 文件等 |
| `playwright-platform-web/Dockerfile.dev` | 前端开发镜像，运行 Vite dev server |
| `playwright-platform-web/Dockerfile` | 前端生产镜像，Node 构建后由 Nginx 托管 |
| `playwright-platform-web/.dockerignore` | 排除 `node_modules`、`dist`、`.vite` 等 |
| `docker-compose.yml` | 本地开发多服务编排 |
| `docker-compose.prod.yml` | 单机生产多服务编排 |

详细说明见 `docs/docker.md`。

---

## 8. 后端核心能力

### 8.1 数据库与迁移

- Flyway 启动时自动执行 `playwright-platform-server/src/main/resources/db/migration` 下的迁移脚本。
- 当前迁移覆盖初始化 schema、调度事件、空间模型、用户会话和自助注册约束。
- `SCHEMA_OVERVIEW.sql` 仅作为结构参考，不是首选初始化方式。

### 8.2 Redis 详情缓存

仓库详情、场景详情和任务详情会先查 Redis。列表接口、任务日志、用例、产物等目前直接查 MySQL 或对象存储相关服务。

缓存策略：

- 空值缓存：降低不存在 ID 的重复数据库查询，防穿透。
- TTL 随机抖动：避免大量 key 同时过期，防雪崩。
- 互斥刷新：热点 key 未命中时减少并发回源，防击穿。
- 写后失效：仓库、场景、任务变更后删除对应详情缓存。

### 8.3 事务策略

- 仓库、场景创建/更新/删除使用短事务。
- 任务创建、取消、状态更新使用短事务。
- Playwright 安装、测试执行、结果解析、产物上传不包在一个大事务里。
- 长任务状态落库由独立 mutation service 分阶段提交，避免长时间占用数据库连接和锁。

### 8.4 多线程与 Runner

- HTTP 请求线程由内嵌 Tomcat 管理，只负责接收请求和快速返回。
- 长任务执行使用自定义 `taskExecutionExecutor`。
- Docker Runner 通过 Docker socket 启动短生命周期 Playwright 容器执行测试。
- Runner workspace 由 `PLATFORM_RUNNER_HOST_WORKSPACE_ROOT` 和 `PLATFORM_RUNNER_WORKSPACE_ROOT` 控制。
- 线程池满载时 fail-fast，任务会被标记为 `FAILED` / `SYSTEM_BUSY`，避免无限排队。

### 8.5 对象存储

- MinIO 保存截图、视频、Trace、阶段日志和头像。
- MySQL 只保存 bucket、object key、content type、size 等元数据。
- 运行产物下载通过后端平台代理接口返回，避免前端直接依赖 MinIO 内网地址。

### 8.6 AI Agent 智能助手

平台集成了基于 Spring AI Alibaba Agent 框架的 AI 智能助手，可为测试团队提供故障排查、业务咨询、任务分析等能力。

#### 架构概览

AI Agent 采用 **ReAct（Reason + Act）** 循环模式：

```
用户提问
  → SystemPromptHook 注入系统提示词 (AGENT.md)
  → SkillsAgentHook 加载技能文档
  → ReAct 循环 (最多 20 次模型调用)
      → Think (思考) → Call Tool (调用工具) → Observe (观察结果) → ... → Answer
  → OutputFormatFallbackService 四层输出解析
  → AgentTraceLogService 写入全链路 Trace
```

#### 核心组件

| 组件 | 职责 |
|---|---|
| `ReactAgent` | ReAct 推理循环，思考→行动→观察 |
| `SystemPromptHook` | 注入 `AGENT.md` 系统提示词 |
| `SkillsAgentHook` | 加载 `error-analysis` 和 `business-knowledge` 技能文档 |
| `ModelCallLimitHook` | 限制最多 20 次模型调用，防止死循环 |
| `ChatSessionManager` | 会话管理（Caffeine 缓存，30min TTL） |
| `ContextCompressionService` | 上下文压缩（结构化摘要 + 滑动窗口） |
| `OutputFormatFallbackService` | 四层输出解析兜底 |
| `InputSanitizer` | 输入清洗 + Prompt 注入检测 |
| `AgentObservability` | 调用量/错误率/Token 用量监控 |
| `AgentTraceLogService` | 全链路 Trace 日志（Redis，90 天 TTL） |

#### Agent 工具

| 工具 | 方法 | 功能 |
|---|---|---|
| **TaskTool** | `getTask(taskId, spaceId)` | 查询任务详情（状态、结果、用例统计） |
| | `listTasks(spaceId, sceneId)` | 列出空间/场景下的任务 |
| | `analyzeTask(taskId, spaceId)` | AI 分析任务根因 |
| **SceneTool** | `listScenes(spaceId)` | 列出空间下的测试场景 |
| | `getSceneDetail(sceneId, spaceId)` | 查询场景详情 |
| **RepositoryTool** | `getRepository(repoId, spaceId)` | 查询仓库配置 |
| | `listRepositories(spaceId)` | 列出空间下的仓库 |
| **LogPreprocessingTool** | `analyzeLogs(taskId, spaceId)` | 分析任务日志，提取错误摘要 |
| **TraceQueryTool** | `queryTrace(traceId)` | 查询 Agent 调用链路 |

#### 上下文管理

- **Token 预算**：最大 8000 tokens，达到 80%（6400）触发压缩。
- **压缩策略**：先尝试智能压缩（摘要 + 滑动窗口，保留最近 3 条消息），超限时降级为暴力压缩。
- **会话存储**：Caffeine 本地缓存，30 分钟 TTL，最多 10000 个会话。
- **Token 估算**：中文 × 1.5 + 英文 × 0.25。

#### 输出格式

Agent 使用 `ChatAssistantResult` 结构化输出：

```java
record ChatAssistantResult(
    String response,              // AI 回复文本 (Markdown)
    List<String> usedTools,       // 使用过的工具名列表
    String confidence,            // 置信度 (HIGH/MEDIUM/LOW)
    String responseType,          // 响应类型 (ANALYSIS/QA/TRACE/...)
    FaultDetail faultDetail       // 故障详情 (可选)
) {
    record FaultDetail(
        String fault_type,         // 故障类型
        String root_cause,         // 根因分析
        String immediate_solution, // 临时解决方案
        String long_term_optimize, // 长期优化建议
        String test_risk,          // 测试风险
        String reproduce_steps     // 复现步骤
    )
}
```

#### 安全与可靠性

| 机制 | 参数 | 说明 |
|---|---|---|
| 输入长度限制 | 10000 字符 | 超长拒绝 |
| Prompt 注入检测 | `InputSanitizer` | 检测绕过系统约束的尝试 |
| 单次调用超时 | 60 秒 | `AgentCallManager` |
| 重试次数 | 2 次 | 指数退避 1s → 2s → 4s |
| 最大模型调用 | 20 次 | `ModelCallLimitHook` 防死循环 |
| SSE 流超时 | 300 秒 | `SseEmitter` |

#### 典型使用场景

- **故障排查**：「帮我分析任务 123 为什么失败了」→ Agent 自动调用 TaskTool + LogPreprocessingTool
- **链路回溯**：「查询 traceId: xxx」→ Agent 调用 TraceQueryTool 返回完整调用链路
- **业务咨询**：「当前空间有哪些测试场景」→ Agent 调用 SceneTool
- **批量分析**：「列出最近 5 个失败任务并分析原因」→ Agent 多轮工具调用

> 详细技术实现见 [`docs/agent-architecture.md`](docs/agent-architecture.md)。

---

## 9. 本地非 Docker 启动

不推荐新手使用这种方式，除非你已经本机准备好了 MySQL、Redis 和 MinIO。

### 9.1 启动后端

```bash
cd playwright-platform-server
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

后端默认监听：

```text
http://localhost:8080
```

### 9.2 启动前端

```bash
cd playwright-platform-web
npm ci
npm run dev
```

Vite 默认访问：

```text
http://localhost:5173
```

---

## 10. 测试与 CI

### 10.1 本地测试

后端：

```bash
cd playwright-platform-server
mvn test
```

前端：

```bash
cd playwright-platform-web
npm test
npm run build
```

### 10.2 GitHub Actions

项目使用 GitHub Actions 作为主干质量门禁。每次 `push` 和 `pull_request` 会执行：

| Job | 步骤 |
|---|---|
| backend | Java 21、Maven cache、`mvn test`、上传 JaCoCo 覆盖率 |
| frontend | Node 20、`npm ci`、`npm test -- --coverage`、`npm run build`、`npm audit` |

覆盖率报告：

- 后端：`playwright-platform-server/target/site/jacoco/index.html`。
- 前端：`playwright-platform-web/coverage/index.html`。

---

## 11. 核心接口

| 接口 | 说明 |
|---|---|
| `GET /api/auth/public-key` | 获取登录加密公钥 |
| `POST /api/auth/login` | 登录 |
| `POST /api/auth/register` | 注册 |
| `GET /api/auth/me` | 获取当前用户 |
| `PUT /api/auth/profile` | 更新当前用户资料 |
| `POST /api/auth/avatar` | 上传当前用户头像 |
| `POST /api/auth/logout` | 退出登录 |
| `GET /api/spaces` | 当前用户空间列表 |
| `GET /api/spaces/plaza` | 空间广场 |
| `POST /api/spaces` | 创建空间 |
| `GET /api/spaces/{spaceId}/access-requests` | 空间加入/权限申请列表 |
| `POST /api/spaces/{spaceId}/access-requests` | 提交空间加入/权限申请 |
| `POST /api/spaces/{spaceId}/access-requests/{requestId}/approve` | 审批通过空间申请 |
| `POST /api/spaces/{spaceId}/access-requests/{requestId}/reject` | 拒绝空间申请 |
| `GET /api/spaces/{spaceId}/repos` | 仓库列表 |
| `POST /api/spaces/{spaceId}/repos` | 创建仓库 |
| `GET /api/spaces/{spaceId}/scenes` | 场景列表 |
| `POST /api/spaces/{spaceId}/scenes` | 创建场景 |
| `POST /api/spaces/{spaceId}/scenes/{sceneId}/run` | 执行场景 |
| `GET /api/spaces/{spaceId}/tasks` | 任务列表 |
| `GET /api/spaces/{spaceId}/scenes/{sceneId}/tasks` | 某个场景的任务 |
| `GET /api/spaces/{spaceId}/tasks/{taskId}` | 任务详情 |
| `POST /api/spaces/{spaceId}/tasks/{taskId}/cancel` | 取消任务 |
| `GET /api/spaces/{spaceId}/tasks/{taskId}/diagnostics` | 任务诊断信息 |
| `GET /api/spaces/{spaceId}/tasks/{taskId}/artifacts` | 任务产物 |
| `GET /api/spaces/{spaceId}/tasks/{taskId}/artifacts/{artifactId}/download` | 下载任务产物 |
| `POST /api/spaces/{spaceId}/tasks/{taskId}/artifacts/{artifactId}/trace-share` | 创建 Trace 分享下载地址 |
| `GET /api/public/traces/download` | 使用分享 token 下载 Trace |
| `GET /api/spaces/{spaceId}/tasks/{taskId}/cases` | 用例结果 |
| `GET /api/spaces/{spaceId}/tasks/{taskId}/cases/{caseResultId}/artifacts` | 某条用例关联产物 |
| `GET /api/spaces/{spaceId}/tasks/{taskId}/logs` | 阶段日志 |
| `GET /api/spaces/{spaceId}/tasks/{taskId}/logs/{logId}/download` | 下载阶段日志 |
| `GET /api/spaces/{spaceId}/schedule-events` | 调度事件列表 |
| `POST /api/spaces/{spaceId}/schedule-events/{eventId}/retry` | 重试调度事件 |
| `POST /api/ai/chat` | 同步 AI 对话 |
| `POST /api/ai/chat/stream` | 流式 AI 对话（SSE） |
| `DELETE /api/ai/session/{sessionId}` | 清理会话 |
| `GET /api/ai/sessions/count` | 查询活跃会话数 |

---

## 12. 测试仓库接入建议

平台中的“测试仓库”指被平台拉取并执行的 Playwright 自动化项目。接入时建议：

- `工作目录`：单仓库项目可留空，Monorepo 填写子目录。
- `安装命令`：建议使用 `npm ci` 或与测试仓库锁文件匹配的安装命令。
- `测试执行命令`：例如 `npx playwright test`。
- 如果用 npm script 包装测试命令，保持参数透传，例如 `npm run test:e2e --`。
- `测试目录`：相对工作目录，例如 `tests`。
- `结果索引文件`：相对工作目录，例如 `test-results/.playwright-results.json`。
- `运行产物目录`：相对工作目录，例如 `.playwright-artifacts`。
- Runner 镜像已包含浏览器环境时，不建议默认执行 `npx playwright install`，避免重复下载浏览器。
- 测试仓库的 `@playwright/test` 版本建议与 Runner 镜像版本对齐。

---

## 13. 文档导航

| 文档 | 内容 |
|---|---|
| `docs/guide.md` | 项目前后端架构、文件职责、核心业务链路、常见问题讲解 |
| `docs/docker.md` | Dockerfile、Dockerfile.dev、.dockerignore 与 Compose 职责划分 |
| `docs/deployment.md` | 单机生产部署、`.env`、验证、备份、回滚、域名 HTTPS、排障 |
| `docs/agent-architecture.md` | AI Agent 技术架构、ReAct 循环、工具、上下文管理、输出兜底、工程约束 |

---

## 14. AI 智能助手使用指南

### 14.1 快速体验

1. 启动 Docker Compose：`docker compose up -d`
2. 访问 `http://localhost:5173`，登录系统
3. 点击右下角 **AI 助手** 图标打开对话框
4. 输入你的问题，例如：
   - 「帮我分析任务 123 为什么失败了」
   - 「当前空间有哪些测试场景」
   - 「查询 traceId: xxx」

### 14.2 功能特性

| 特性 | 说明 |
|---|---|
| 流式响应 | SSE 打字机效果，边生成边显示 |
| 多轮对话 | 会话持久化，支持上下文追问 |
| 智能压缩 | 自动压缩历史消息，控制 Token 总量 |
| 工具调用 | Agent 自动选择和调用平台工具 |
| 故障诊断 | 结构化输出故障类型、根因、解决方案 |
| 全链路追踪 | 每次对话生成 traceId，可 90 天内回溯 |
| 会话管理 | 支持会话清除、历史保留、自动过期 |

### 14.3 配置说明

AI 相关配置通过 `application.yml` 注入：

```yaml
platform:
  ai:
    system-prompt-path: classpath:AGENT.md
    call:
      timeout-seconds: 60
      max-retries: 2
      retry-delay-ms: 1000
    context:
      max-tokens: 8000
      max-messages: 50
      compression-threshold: 0.8
      keep-recent-messages: 3
      max-message-content-length: 4000
```

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `platform.ai.system-prompt-path` | `classpath:AGENT.md` | 系统提示词路径 |
| `platform.ai.call.timeout-seconds` | 60 | 单次调用超时 |
| `platform.ai.call.max-retries` | 2 | 失败重试次数 |
| `platform.ai.context.max-tokens` | 8000 | 上下文 Token 上限 |
| `platform.ai.context.compression-threshold` | 0.8 | 压缩触发阈值 |
| `platform.ai.context.keep-recent-messages` | 3 | 压缩时保留最近消息数 |

### 14.4 系统提示词

系统提示词文件位于 `playwright-platform-server/src/main/resources/AGENT.md`，定义了：

- Agent 角色（测试平台智能助手）
- 输出格式规范（JSON Schema）
- 故障详情结构（FaultDetail）
- 约束规则（安全、格式、行为边界）
- Few-shot 示例

修改 `AGENT.md` 后重启后端即可生效。

### 14.5 Skills 技能

Agent 加载两个 Skills 技能文档：

| 技能 | 路径 | 内容 |
|---|---|---|
| `error-analysis` | `resources/skills/error-analysis/` | 故障诊断方法论、SOP、分类体系 |
| `business-knowledge` | `resources/skills/business-knowledge/` | 业务知识、技术架构、API 速查 |

### 14.6 前端组件

AI 对话前端组件位于 `playwright-platform-web/src/components/ai/`：

| 文件 | 职责 |
|---|---|
| `AiAssistantDialog.vue` | 对话框主组件（消息列表、输入框、工具展示） |
| `ChatMessage.vue` | 单条消息组件 |
| `ErrorCard.vue` | 故障详情卡片 |
| `useAiAssistant.ts` | AI 助手 composable（状态管理、SSE 流处理） |
| `types.ts` | TypeScript 类型定义 |

---

## 15. 注意事项

- `.env` 保存本地或服务器真实配置，不要提交 GitHub。
- `.env.example` 当前按项目要求不保留，创建 `.env` 时参考本文档和 `docs/deployment.md`。
- MySQL、Redis、MinIO 使用 Docker volume 初始化后，修改 `.env` 密码不会自动修改已有数据卷里的账号密码。
- 不要公网开放 MySQL 和 Redis。
- MinIO Console 只建议限制来源 IP 后开放。
- Docker Runner 需要挂载 `/var/run/docker.sock`，该权限较高，只建议用于本地开发或受控服务器。
- 前端项目内如果存在 Vite 模板说明，以本仓库根目录 README 为准。
