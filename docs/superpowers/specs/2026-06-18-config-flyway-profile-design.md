# 第三阶段配置与 Flyway 治理设计

## 目的

本阶段补齐数据库迁移、环境变量示例、多环境 profile 和生产配置说明，并清理默认弱口令。目标是让数据库结构由 Flyway 自动管理，让生产默认配置不再内置敏感凭据，同时保留本地开发的低门槛启动方式。

## 范围

- 新增 Flyway migration：`playwright-platform-server/src/main/resources/db/migration/V1__init_schema.sql`。
- 调整 `application.yml`，移除数据库密码、MinIO access key、MinIO secret key 等弱口令默认值。
- 新增 `application-dev.yml`，保留本地开发示例配置。
- 新增根目录 `.env.example`，为 Docker Compose 和手动启动提供环境变量模板。
- 更新 `docker-compose.yml`，让 Compose 通过环境变量和 dev profile 启动，不再挂载 schema SQL 初始化数据库。
- 更新 README，说明 `.env` 初始化、Flyway 自动迁移和生产配置要求。

## 不在本阶段处理

- 不做生产镜像发布。
- 不引入覆盖率、lint、依赖扫描或发布文档，这些放到第四阶段。
- 不重构 Runner 为容器化或沙箱执行，这放到第五阶段。
- 不引入鉴权系统。

## Flyway 设计

现有 `SCHEMA_OVERVIEW.sql` 继续作为结构参考文档保留。新增 `V1__init_schema.sql` 作为真正的数据库初始化脚本。

Migration 脚本从现有 schema 中迁移建表语句，但不包含以下内容：

- `CREATE DATABASE`
- `USE`
- `DROP TABLE`
- `SET FOREIGN_KEY_CHECKS`

这样可以避免 Flyway 在已有数据库上执行破坏性操作，并让 schema 演进由版本化 migration 管理。

## 配置设计

`application.yml` 作为安全默认配置：

- 保留环境变量引用。
- 对敏感字段不提供弱口令默认值。
- 对非敏感字段可以保留开发友好的默认值，例如 `SERVER_PORT`、bucket 名称和 runner workspace。

`application-dev.yml` 作为本地开发配置：

- 提供 MySQL、MinIO 的本地示例连接信息。
- 仅用于本地手动开发或 Docker Compose 开发环境。
- 不建议生产环境启用。

测试配置继续使用 H2，确保 `mvn test` 不依赖外部 MySQL 或 MinIO。

## Compose 设计

Compose 从 `.env` 读取开发变量，并为常用本地开发值提供默认 fallback。服务端容器启用 `SPRING_PROFILES_ACTIVE=dev`，同时显式注入数据库和 MinIO 变量。

MySQL 初始化不再挂载 `SCHEMA_OVERVIEW.sql`。后端启动后由 Flyway 自动创建表结构。

## 文档设计

README 增加：

- `cp .env.example .env`
- Docker Compose 依赖 `.env` 的说明。
- 数据库初始化由 Flyway 自动完成。
- 生产环境必须显式注入数据库和 MinIO 凭据。
- 生产环境不应使用 `dev` profile。

## 验收标准

- `mvn test` 通过。
- `docker compose config` 通过。
- `npm test` 通过。
- `npm run build` 通过。
- `application.yml` 不再包含数据库和 MinIO 弱口令默认值。
- Flyway migration 文件存在，且不包含 destructive schema 初始化语句。
