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
- 后端：Spring Boot 3.5、Spring Web、Spring Data JPA、Flyway
- 存储与依赖：MySQL、MinIO

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

默认开发账号与本地配置保持一致：

- MySQL：`root` / `12345678`
- MinIO：`minioadmin` / `minioadmin`
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

## 快速开始

### 1. 克隆仓库

```bash
git clone <your-repo-url>
cd test_platform
```

### 2. 准备基础依赖

先启动本地 MySQL 和 MinIO。

本地开发可启用后端 `dev` profile，示例配置来自 `playwright-platform-server/src/main/resources/application-dev.yml`：

- MySQL：`jdbc:mysql://localhost:3306/playwright_platform`
- 用户名：`root`
- 密码：`12345678`
- MinIO：`http://127.0.0.1:9000`
- Bucket：`qa-report`

如需覆盖，使用环境变量：

```bash
export PLATFORM_DB_URL='jdbc:mysql://localhost:3306/playwright_platform?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true&serverTimezone=UTC'
export PLATFORM_DB_USERNAME='root'
export PLATFORM_DB_PASSWORD='12345678'
export PLATFORM_MINIO_ENDPOINT='http://127.0.0.1:9000'
export PLATFORM_MINIO_ACCESS_KEY='minioadmin'
export PLATFORM_MINIO_SECRET_KEY='minioadmin'
export PLATFORM_STORAGE_BUCKET='qa-report'
```

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
- `PLATFORM_MINIO_ENDPOINT`
- `PLATFORM_MINIO_ACCESS_KEY`
- `PLATFORM_MINIO_SECRET_KEY`
- `PLATFORM_STORAGE_BUCKET`

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

- 后端：`mvn test`
- 前端：`npm ci`
- 前端：`npm test`
- 前端：`npm run build`

这些检查用于确保主干代码始终可测试、依赖可复现安装，并且前端可以完成生产构建。

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
