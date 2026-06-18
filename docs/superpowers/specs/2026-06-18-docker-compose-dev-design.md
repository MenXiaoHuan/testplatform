# 第二阶段 Docker Compose 开发环境设计

## 目的

本阶段为项目补齐 Docker Compose 开发环境，让开发者可以通过一条命令启动 MySQL、MinIO、后端和前端。目标是降低本地环境搭建成本，并为后续数据库迁移、配置治理、质量门禁和 Runner 安全隔离提供可重复的基础运行环境。

## 范围

- 新增根目录 `docker-compose.yml`，编排 MySQL、MinIO、MinIO bucket 初始化、Spring Boot 后端和 Vite 前端。
- 新增必要的 Docker 构建上下文配置，避免把 `target/`、`node_modules/`、`dist/` 等本地产物带入镜像构建。
- README 增加“一键开发环境”说明，包含启动、访问、停止和清理数据命令。
- 数据库初始化沿用现有 `playwright-platform-server/src/main/resources/db/schema/SCHEMA_OVERVIEW.sql`。
- MinIO 初始化创建默认 bucket `qa-report`。

## 不在本阶段处理

- 不制作生产发布镜像。
- 不迁移 Flyway migration，数据库仍使用现有 schema SQL 初始化。
- 不清理默认弱口令，生产配置治理放到第三阶段。
- 不引入覆盖率、lint、依赖扫描或发布文档，这些放到第四阶段。
- 不重构 Runner 为容器化或沙箱执行，这放到第五阶段。

## 服务设计

Compose 使用开发模式，服务包括：

- `mysql`：使用 MySQL 8，创建 `playwright_platform` 数据库，并通过 `/docker-entrypoint-initdb.d/` 执行现有 schema SQL。
- `minio`：使用 MinIO server，暴露对象存储端口 `9000` 和控制台端口 `9001`。
- `minio-init`：一次性初始化容器，等待 MinIO 可用后创建 `qa-report` bucket。
- `server`：使用 Maven + Java 21 开发镜像运行 `mvn spring-boot:run`，通过环境变量连接 Compose 内部的 MySQL 和 MinIO。
- `web`：使用 Node 20 开发镜像运行 Vite dev server，暴露 `5173`，并通过 Vite 代理访问后端。

## 数据与缓存

- MySQL 数据使用 named volume 持久化。
- MinIO 数据使用 named volume 持久化。
- Maven 仓库缓存使用 named volume，减少重复下载依赖。
- 前端 `node_modules` 使用 named volume，避免覆盖宿主机源码目录。

## 使用方式

启动开发环境：

```bash
docker compose up --build
```

访问地址：

- 前端：`http://localhost:5173`
- 后端：`http://localhost:8080`
- MinIO Console：`http://localhost:9001`

停止服务：

```bash
docker compose down
```

清理容器和数据卷：

```bash
docker compose down -v
```

## 验收标准

- `docker compose config` 可以通过配置校验。
- `docker compose up --build` 可以启动 MySQL、MinIO、后端和前端。
- MySQL 首次启动后包含平台所需表结构。
- MinIO 首次启动后存在 `qa-report` bucket。
- README 能说明一键启动、访问地址、停止和清理数据命令。
