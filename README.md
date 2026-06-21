# Playwright Test Platform

一个用于管理 Playwright 自动化测试仓库、执行场景任务、查看用例结果与运行产物的测试平台。

当前仓库实际包含两个子项目：

- `playwright-platform-server`：Spring Boot 后端
- `playwright-platform-web`：Vue 3 + Vite 前端

## 功能概览

- 管理测试仓库配置：Git 地址、默认分支、工作目录、安装命令、测试执行命令、测试目录等
- 管理 E2E 场景：关联仓库、浏览器、测试选择器、环境变量、定时执行配置
- 手动触发场景执行，按场景查看任务历史
- 查看任务详情、阶段状态、阶段日志、用例结果
- 展示运行产物，包括截图、视频、Trace 等
- 支持任务取消和任务重新执行

## 技术栈

- 前端：Vue 3、TypeScript、Vite、Pinia、Element Plus、Vitest
- 后端：Spring Boot 3.5、Spring Web、注解式 MyBatis、Flyway、Spring Data Redis
- 存储与依赖：MySQL、Redis、MinIO

### 持久层

后端只使用注解式 MyBatis，Flyway 管理 schema，无 JPA/XML mapper。

## 目录结构

```text
.
├── README.md
├── playwright-platform-server
│   ├── pom.xml
│   └── src
└── playwright-platform-web
    ├── package.json
    └── src
```

## 环境要求

- Node.js 20+
- npm
- Java 21
- Maven 3.9+
- MySQL 8+
- Redis 7+
- MinIO

## Docker Compose 开发环境

本地已安装 Docker 后，可以使用 Compose 一键启动 MySQL、MinIO、后端和前端。首次启动前先复制环境变量模板：

```bash
cp .env.example .env
docker compose up --build
```

启动后可访问：

- 前端：`http://localhost:5173`
- 后端：`http://localhost:8080`
- MinIO Console：`http://localhost:9001`

本地 Compose 会读取 `.env`。仓库只提交 `.env.example` 模板，不提交真实账号或密码；首次启动前请复制并替换其中的 `change-me-*` 占位值：

- MySQL：账号和密码来自 `PLATFORM_DB_USERNAME`、`PLATFORM_DB_PASSWORD`
- Redis：连接地址和密码来自 `PLATFORM_REDIS_HOST`、`PLATFORM_REDIS_PORT`、`PLATFORM_REDIS_PASSWORD`
- MinIO：账号和密码来自 `PLATFORM_MINIO_ACCESS_KEY`、`PLATFORM_MINIO_SECRET_KEY`
- Bucket：`qa-report`

停止服务：

```bash
docker compose down
```

如需同时清理 MySQL、MinIO 和依赖缓存数据卷：

```bash
docker compose down -v
```

该 Compose 配置用于本地开发，会启用后端 `dev` profile。生产环境请显式注入敏感配置，不要使用 `dev` profile。

### Docker Runner

Compose 开发环境默认启用 `PLATFORM_RUNNER_MODE=docker`。后端会通过 Docker socket 启动短生命周期 Runner 容器来执行安装和测试命令，任务工作区位于 `.runner-workspaces/`。

该模式会把宿主机 `/var/run/docker.sock` 挂载给 server 容器。Docker socket 具备较高权限，仅建议用于本地开发或受控环境。Runner 容器本身不会挂载 Docker socket，只挂载当前任务 workspace。

如需回退到本地执行模式，在 `.env` 中设置：

```bash
PLATFORM_RUNNER_MODE=local
```

## 快速开始

### 1. 克隆仓库

```bash
git clone <your-repo-url>
cd test_platform
```

### 2. 准备基础依赖

先启动本地 MySQL、Redis 和 MinIO。

本地开发可启用后端 `dev` profile。配置文件只读取环境变量，不内置数据库、Redis 或对象存储连接地址：

- MySQL 连接：通过 `PLATFORM_DB_URL` 注入
- 用户名：通过 `PLATFORM_DB_USERNAME` 注入
- 密码：通过 `PLATFORM_DB_PASSWORD` 注入
- Redis：通过 `PLATFORM_REDIS_HOST`、`PLATFORM_REDIS_PORT`、`PLATFORM_REDIS_PASSWORD` 注入
- MinIO：通过 `PLATFORM_MINIO_ENDPOINT` 注入
- Bucket：`qa-report`

如需覆盖，使用环境变量：

```bash
export PLATFORM_DB_URL='<your-db-jdbc-url>'
export PLATFORM_DB_USERNAME='<your-db-username>'
export PLATFORM_DB_PASSWORD='<your-db-password>'
export PLATFORM_REDIS_HOST='<your-redis-host>'
export PLATFORM_REDIS_PORT='<your-redis-port>'
export PLATFORM_REDIS_PASSWORD='<your-redis-password>'
export PLATFORM_MINIO_ENDPOINT='<your-minio-endpoint>'
export PLATFORM_MINIO_ACCESS_KEY='<your-minio-access-key>'
export PLATFORM_MINIO_SECRET_KEY='<your-minio-secret-key>'
export PLATFORM_STORAGE_BUCKET='qa-report'
```

如果使用本机 Redis，请先给 Redis 配置密码，或直接使用 Compose 中已启用 `requirepass` 的 Redis。真实密码只写入本地 `.env` 或本机 Redis 配置，不提交到代码库。

本机 Redis 尚未设置密码时，可任选一种方式：

- 推荐：使用 `docker compose up redis` 启动 Compose Redis，并在本地 `.env` 中填写 `PLATFORM_REDIS_PASSWORD`
- 临时：执行 `redis-cli CONFIG SET requirepass '<your-redis-password>'`，重启 Redis 后可能失效
- 持久：在本机 `redis.conf` 中配置 `requirepass <your-redis-password>`，然后重启 Redis

### 3. 初始化数据库

后端启动时会通过 Flyway 自动执行 `playwright-platform-server/src/main/resources/db/migration` 下的版本化迁移脚本。

`SCHEMA_OVERVIEW.sql` 仅作为结构参考，不再是首选初始化方式。

### 4. 安装前端依赖

```bash
cd playwright-platform-web
npm install
cd ..
```

### 5. 启动后端

```bash
cd playwright-platform-server
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

默认监听 `http://localhost:8080`。

### 6. 启动前端

```bash
cd playwright-platform-web
npm run dev
```

Vite 默认访问地址通常为 `http://localhost:5173`。如果需要固定端口，可以这样启动：

```bash
cd playwright-platform-web
npm run dev -- --host 0.0.0.0 --port 4173
```

前端开发服务器会将 `/api` 代理到 `http://localhost:8080`。

## 生产配置

生产环境必须显式注入以下配置，不要依赖本地开发示例值，也不要启用 `dev` profile：

- `PLATFORM_DB_URL`
- `PLATFORM_DB_USERNAME`
- `PLATFORM_DB_PASSWORD`
- `PLATFORM_REDIS_HOST`
- `PLATFORM_REDIS_PORT`
- `PLATFORM_REDIS_PASSWORD`
- `PLATFORM_MINIO_ENDPOINT`
- `PLATFORM_MINIO_ACCESS_KEY`
- `PLATFORM_MINIO_SECRET_KEY`
- `PLATFORM_STORAGE_BUCKET`

### Redis 详情缓存

仓库详情、场景详情和任务详情使用 Redis 缓存，降低高频查询对 MySQL 的压力。写入路径采用写后失效策略，仓库、场景和任务的新增、更新、取消、状态流转、完成归档等变更会删除相关详情缓存。

缓存治理配置由 `platform.cache` 管理：

- `detail-ttl`：正常详情缓存 TTL，默认 `${PLATFORM_CACHE_DETAIL_TTL:5m}`
- `null-ttl`：空值缓存 TTL，默认 `${PLATFORM_CACHE_NULL_TTL:1m}`，用于防穿透
- `jitter-seconds`：随机 TTL 抖动秒数，默认 `${PLATFORM_CACHE_JITTER_SECONDS:60}`，用于防雪崩
- `mutex-ttl`：缓存未命中加载互斥锁 TTL，默认 `${PLATFORM_CACHE_MUTEX_TTL:5s}`，用于防击穿

### 事务策略

平台只在短写操作上开启事务边界，例如仓库、场景、任务取消、任务状态更新、场景摘要更新和启动恢复补偿。长时间执行的 Playwright 安装、测试、解析和产物归档不放在单个数据库事务中，避免外部命令运行期间占用连接和锁。

### 线程池治理

长任务执行继续使用自定义 `taskExecutionExecutor`，不会占用 Spring Web/Tomcat 请求线程执行 Playwright 命令。线程池参数由 `platform.task.execution` 管理：

- `core-pool-size`：核心执行线程数，默认 `2`
- `max-pool-size`：最大执行线程数，默认 `4`
- `queue-capacity`：等待队列容量，默认 `50`
- `keep-alive-seconds`：非核心线程空闲存活时间，默认 `60`
- `install-timeout-seconds`：依赖安装阶段超时，默认 `600`
- `test-timeout-seconds`：测试执行阶段超时，默认 `1800`
- `monitor-log-interval-seconds`：线程池监控日志输出间隔，默认 `30`

`taskExecutionExecutor` 使用 fail-fast 拒绝策略。线程池满载时会记录 active、pool、max、queue 等指标日志，便于日志告警系统按 `Task execution rejected` 关键字触发告警；业务侧捕获拒绝异常后将任务补偿为 `FAILED`，`resultCode=SYSTEM_BUSY`，并同步刷新场景摘要和详情缓存。

启动恢复会扫描历史 `QUEUED/RUNNING` 任务。未取消的 `QUEUED` 任务会重新派发，重启前已进入 `RUNNING` 或已取消的任务会按失败/取消路径补偿，避免服务重启后任务长期卡在执行中。

### Tomcat 请求线程

HTTP 请求线程由内嵌 Tomcat 管理，只负责接收请求和提交后台任务。可通过以下环境变量调整请求线程池和连接积压队列：

- `SERVER_TOMCAT_THREADS_MAX`：最大请求线程数，默认 `200`
- `SERVER_TOMCAT_THREADS_MIN_SPARE`：最小空闲请求线程数，默认 `10`
- `SERVER_TOMCAT_ACCEPT_COUNT`：请求线程耗尽时的连接等待队列长度，默认 `100`

## 开发命令

### 后端

```bash
cd playwright-platform-server
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn test
```

### 前端

```bash
cd playwright-platform-web
npm install
npm run dev
npm run build
npm test
```

## CI

项目使用 GitHub Actions 作为主干质量门禁。每次 `push` 和 `pull_request` 会自动执行：

- 后端：`mvn test`（同步生成 JaCoCo 覆盖率报告）
- 前端：`npm ci`
- 前端：`npm test -- --coverage`（生成 Vitest v8 覆盖率报告）
- 前端：`npm run build`
- 前端：`npm audit --audit-level=high`（信息性检查，不阻塞构建）

运行结束后，覆盖率报告会通过 GitHub Actions artifact 上传，可在 CI 运行详情中下载查看。

### 覆盖率报告

**后端（JaCoCo）**

运行 `mvn test` 后，报告位于 `playwright-platform-server/target/site/jacoco/index.html`。

**前端（Vitest + v8）**

运行 `npm test -- --coverage` 后，报告位于 `playwright-platform-web/coverage/index.html`。同时生成 `lcov.info` 便于后续对接代码覆盖率平台。

### TypeScript 严格模式

前端 `tsconfig.app.json` 已启用 `"strict": true`，确保所有新增代码在类型层面被严格检查。`npm run build` 使用 `vue-tsc -b` 进行类型检查。

## 核心页面与能力

### 仓库管理

- 新增、编辑、复制、删除测试仓库
- 启用或停用仓库
- 配置工作目录、安装命令、执行命令模板、测试目录、结果索引文件和产物目录

### 场景管理

- 创建和编辑 E2E 场景
- 选择浏览器、分支、用例路径或目录
- 配置环境变量 JSON
- 配置定时执行 Cron 表达式

### 任务管理

- 手动触发场景执行
- 查看任务列表、状态、阶段、耗时、结果归因
- 查看任务详情、阶段日志、用例结果
- 查看截图、视频、Trace 等产物
- 取消运行中的任务，或对历史任务重新执行

## 主要接口

后端当前暴露的核心接口包括：

- `GET /api/repos`
- `POST /api/repos`
- `GET /api/scenes`
- `POST /api/scenes`
- `POST /api/scenes/{sceneId}/run`
- `GET /api/tasks`
- `GET /api/scenes/{sceneId}/tasks`
- `GET /api/tasks/{taskId}`
- `POST /api/tasks/{taskId}/cancel`
- `GET /api/tasks/{taskId}/logs`

## 仓库接入说明

平台中的“测试仓库”指被平台拉取并执行的 Playwright 自动化项目。接入时建议使用以下约定：

- `工作目录`：单仓库项目可留空；Monorepo 可填写子目录
- `安装命令`：例如 `npm install && npx playwright install`
- `测试执行命令`：例如 `npx playwright test`
- 如果使用 npm script 包装测试命令，必须保持参数透传，例如 `npm run test:e2e --`
- `测试目录`：相对工作目录，例如 `tests`
- `结果索引文件`：相对工作目录，例如 `test-results/.playwright-results.json`
- `运行产物目录`：相对工作目录，例如 `.playwright-artifacts`

## 测试

```bash
cd playwright-platform-server
mvn test

cd ../playwright-platform-web
npm test
```

## 注意事项

- 前端项目内默认 `README.md` 仍是 Vite 模板文件，不代表当前平台说明
- 本仓库当前并不包含 `playwright_framework` 子目录，相关旧说明已不适用
- 后端任务执行依赖 MySQL、MinIO 以及可被拉取和执行的 Playwright 测试仓库
