# Docker Compose Dev Environment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Docker Compose development environment that starts MySQL, MinIO, the Spring Boot backend, and the Vite frontend with one command.

**Architecture:** The root `docker-compose.yml` coordinates service startup, data volumes, health checks, schema initialization, and MinIO bucket creation. Lightweight development Dockerfiles run the backend through Maven and the frontend through Vite while mounting local source code for iterative development.

**Tech Stack:** Docker Compose, MySQL 8, MinIO, Maven with Java 21, Node.js 20, Spring Boot, Vue/Vite.

---

## File Structure

- Create `docker-compose.yml`: defines MySQL, MinIO, MinIO bucket initialization, backend, frontend, and persistent volumes.
- Create `playwright-platform-server/Dockerfile.dev`: development backend container that runs `mvn spring-boot:run`.
- Create `playwright-platform-server/.dockerignore`: excludes backend build output and local editor files from Docker context.
- Create `playwright-platform-web/Dockerfile.dev`: development frontend container that runs Vite on `0.0.0.0`.
- Create `playwright-platform-web/.dockerignore`: excludes frontend dependency and build output from Docker context.
- Modify `playwright-platform-web/vite.config.ts`: allows the Vite API proxy target to be overridden inside Docker Compose.
- Modify `README.md`: documents one-command development startup, URLs, stop command, and data cleanup command.

## Task 1: Add Development Dockerfiles

**Files:**
- Create: `playwright-platform-server/Dockerfile.dev`
- Create: `playwright-platform-server/.dockerignore`
- Create: `playwright-platform-web/Dockerfile.dev`
- Create: `playwright-platform-web/.dockerignore`

- [ ] **Step 1: Create backend development Dockerfile**

Create `playwright-platform-server/Dockerfile.dev`:

```dockerfile
FROM maven:3.9-eclipse-temurin-21

WORKDIR /workspace

EXPOSE 8080

CMD ["mvn", "spring-boot:run"]
```

- [ ] **Step 2: Create backend Docker ignore file**

Create `playwright-platform-server/.dockerignore`:

```gitignore
target/
.mvn/
.idea/
*.iml
.DS_Store
```

- [ ] **Step 3: Create frontend development Dockerfile**

Create `playwright-platform-web/Dockerfile.dev`:

```dockerfile
FROM node:20-alpine

WORKDIR /workspace

EXPOSE 5173

CMD ["sh", "-c", "npm install && npm run dev -- --host 0.0.0.0"]
```

- [ ] **Step 4: Create frontend Docker ignore file**

Create `playwright-platform-web/.dockerignore`:

```gitignore
node_modules/
dist/
dist-ssr/
.vite/
.idea/
*.iml
.DS_Store
```

## Task 2: Add Docker Compose Configuration

**Files:**
- Create: `docker-compose.yml`

- [ ] **Step 1: Create root Compose file**

Create `docker-compose.yml`:

```yaml
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: 12345678
      MYSQL_DATABASE: playwright_platform
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ./playwright-platform-server/src/main/resources/db/schema/SCHEMA_OVERVIEW.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-p12345678"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s

  minio:
    image: minio/minio:RELEASE.2025-04-22T22-12-26Z
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio-data:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 10s

  minio-init:
    image: minio/mc:RELEASE.2025-04-16T18-13-26Z
    depends_on:
      minio:
        condition: service_healthy
    entrypoint: >
      /bin/sh -c "
      mc alias set local http://minio:9000 minioadmin minioadmin &&
      mc mb --ignore-existing local/qa-report
      "

  server:
    build:
      context: ./playwright-platform-server
      dockerfile: Dockerfile.dev
    depends_on:
      mysql:
        condition: service_healthy
      minio-init:
        condition: service_completed_successfully
    environment:
      PLATFORM_DB_URL: jdbc:mysql://mysql:3306/playwright_platform?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
      PLATFORM_DB_USERNAME: root
      PLATFORM_DB_PASSWORD: 12345678
      PLATFORM_MINIO_ENDPOINT: http://minio:9000
      PLATFORM_MINIO_ACCESS_KEY: minioadmin
      PLATFORM_MINIO_SECRET_KEY: minioadmin
      PLATFORM_STORAGE_BUCKET: qa-report
      PLATFORM_RUNNER_WORKSPACE_ROOT: /tmp/playwright-platform/workspaces
    ports:
      - "8080:8080"
    volumes:
      - ./playwright-platform-server:/workspace
      - maven-cache:/root/.m2

  web:
    build:
      context: ./playwright-platform-web
      dockerfile: Dockerfile.dev
    depends_on:
      - server
    ports:
      - "5173:5173"
    volumes:
      - ./playwright-platform-web:/workspace
      - web-node-modules:/workspace/node_modules

volumes:
  mysql-data:
  minio-data:
  maven-cache:
  web-node-modules:
```

## Task 3: Document One-Command Development Environment

**Files:**
- Modify: `README.md`
- Modify: `playwright-platform-web/vite.config.ts`

- [ ] **Step 1: Make Vite proxy target configurable**

Update `playwright-platform-web/vite.config.ts` so Compose can point the proxy at the backend service:

```typescript
const apiProxyTarget = process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080'
```

Use `apiProxyTarget` as the `/api` proxy target.

- [ ] **Step 2: Add Docker Compose section after environment requirements**

Insert:

```markdown
## Docker Compose 开发环境

本地已安装 Docker 后，可以使用 Compose 一键启动 MySQL、MinIO、后端和前端：

```bash
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

该 Compose 配置用于本地开发。生产环境配置、弱口令清理和多环境 profile 将在后续阶段处理。
```

## Task 4: Verify Compose And Existing Gates

**Files:**
- No source file changes.

- [ ] **Step 1: Validate Compose syntax**

Run:

```bash
docker compose config
```

Expected: command exits with code `0`.

- [ ] **Step 2: Start Compose stack**

Run:

```bash
docker compose up --build
```

Expected: MySQL, MinIO, backend, and frontend start. Frontend is available at `http://localhost:5173`.

- [ ] **Step 3: Stop Compose stack**

Run:

```bash
docker compose down
```

Expected: containers stop cleanly.

- [ ] **Step 4: Run backend tests**

Run:

```bash
cd playwright-platform-server
mvn test
```

Expected: command exits with code `0`.

- [ ] **Step 5: Run frontend tests and build**

Run:

```bash
cd playwright-platform-web
npm test
npm run build
```

Expected: both commands exit with code `0`.

## Task 5: Commit Changes

**Files:**
- Create: `docker-compose.yml`
- Create: `playwright-platform-server/Dockerfile.dev`
- Create: `playwright-platform-server/.dockerignore`
- Create: `playwright-platform-web/Dockerfile.dev`
- Create: `playwright-platform-web/.dockerignore`
- Modify: `playwright-platform-web/vite.config.ts`
- Modify: `README.md`
- Create: `docs/superpowers/plans/2026-06-18-docker-compose-dev.md`

- [ ] **Step 1: Check status**

Run:

```bash
git status --short
```

Expected: only the files listed in this task are changed.

- [ ] **Step 2: Commit implementation**

Run:

```bash
git add docker-compose.yml playwright-platform-server/Dockerfile.dev playwright-platform-server/.dockerignore playwright-platform-web/Dockerfile.dev playwright-platform-web/.dockerignore playwright-platform-web/vite.config.ts README.md docs/superpowers/plans/2026-06-18-docker-compose-dev.md
git commit -m "chore: add docker compose dev environment"
```

Expected: commit succeeds.
