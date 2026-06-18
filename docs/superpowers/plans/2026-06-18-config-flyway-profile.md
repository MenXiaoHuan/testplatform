# Config Flyway Profile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move database initialization to Flyway migrations, remove weak secrets from default configuration, and document safe dev/prod configuration paths.

**Architecture:** `application.yml` becomes the safe default profile with no weak secret defaults. `application-dev.yml` and `.env.example` provide explicit local development values. `docker-compose.yml` stops mounting schema SQL and lets the backend run Flyway migrations on startup.

**Tech Stack:** Spring Boot 3.5, Flyway, MySQL, H2 tests, Docker Compose, YAML configuration.

---

## File Structure

- Create `playwright-platform-server/src/main/resources/db/migration/V1__init_schema.sql`: Flyway initial schema migration.
- Modify `playwright-platform-server/src/main/resources/application.yml`: remove weak defaults for DB and MinIO credentials.
- Create `playwright-platform-server/src/main/resources/application-dev.yml`: local development defaults.
- Modify `playwright-platform-server/src/test/java/com/example/platform/config/ApplicationConfigurationTest.java`: assert the new safe-default/dev-profile split.
- Create `.env.example`: Docker Compose and manual development environment template.
- Modify `.gitignore`: ignore local `.env` files while keeping `.env.example` tracked.
- Modify `docker-compose.yml`: read values from environment and remove schema SQL bind mount.
- Modify `README.md`: document `.env`, Flyway automatic migration, and production configuration rules.

## Task 1: Add Flyway Initial Migration

**Files:**
- Create: `playwright-platform-server/src/main/resources/db/migration/V1__init_schema.sql`

- [ ] **Step 1: Create migration directory**

Run:

```bash
mkdir -p playwright-platform-server/src/main/resources/db/migration
```

Expected: command exits with code `0`.

- [ ] **Step 2: Create V1 migration**

Create `playwright-platform-server/src/main/resources/db/migration/V1__init_schema.sql` with the table and index definitions from `SCHEMA_OVERVIEW.sql`, excluding `CREATE DATABASE`, `USE`, `DROP TABLE`, and `SET FOREIGN_KEY_CHECKS`.

- [ ] **Step 3: Verify destructive statements are absent**

Run:

```bash
grep -E "CREATE DATABASE|USE |DROP TABLE|SET FOREIGN_KEY_CHECKS" playwright-platform-server/src/main/resources/db/migration/V1__init_schema.sql
```

Expected: command exits with code `1` and prints no matches.

## Task 2: Split Safe Default And Dev Configuration

**Files:**
- Modify: `playwright-platform-server/src/main/resources/application.yml`
- Create: `playwright-platform-server/src/main/resources/application-dev.yml`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/config/ApplicationConfigurationTest.java`

- [ ] **Step 1: Update safe default configuration**

Change sensitive fields in `application.yml` to require environment variables:

```yaml
spring:
  datasource:
    url: ${PLATFORM_DB_URL}
    username: ${PLATFORM_DB_USERNAME}
    password: ${PLATFORM_DB_PASSWORD}
platform:
  storage:
    minio:
      endpoint: ${PLATFORM_MINIO_ENDPOINT}
      access-key: ${PLATFORM_MINIO_ACCESS_KEY}
      secret-key: ${PLATFORM_MINIO_SECRET_KEY}
```

Keep non-sensitive defaults such as `SERVER_PORT`, `PLATFORM_STORAGE_BUCKET`, and `PLATFORM_RUNNER_WORKSPACE_ROOT`.

- [ ] **Step 2: Add dev profile configuration**

Create `playwright-platform-server/src/main/resources/application-dev.yml`:

```yaml
spring:
  datasource:
    url: ${PLATFORM_DB_URL:jdbc:mysql://localhost:3306/playwright_platform?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true&serverTimezone=UTC}
    username: ${PLATFORM_DB_USERNAME:root}
    password: ${PLATFORM_DB_PASSWORD:12345678}
platform:
  storage:
    minio:
      endpoint: ${PLATFORM_MINIO_ENDPOINT:http://127.0.0.1:9000}
      access-key: ${PLATFORM_MINIO_ACCESS_KEY:minioadmin}
      secret-key: ${PLATFORM_MINIO_SECRET_KEY:minioadmin}
```

## Task 3: Add Environment Template And Compose Updates

**Files:**
- Create: `.env.example`
- Modify: `.gitignore`
- Modify: `docker-compose.yml`

- [ ] **Step 1: Add `.env.example`**

Create `.env.example`:

```dotenv
PLATFORM_DB_NAME=playwright_platform
PLATFORM_DB_USERNAME=root
PLATFORM_DB_PASSWORD=12345678
PLATFORM_MINIO_ACCESS_KEY=minioadmin
PLATFORM_MINIO_SECRET_KEY=minioadmin
PLATFORM_STORAGE_BUCKET=qa-report
```

- [ ] **Step 2: Ignore local `.env`**

Add this line to `.gitignore`:

```gitignore
.env
```

- [ ] **Step 3: Update Compose database and MinIO variables**

Update `docker-compose.yml` so MySQL and MinIO use values from `.env`:

```yaml
MYSQL_ROOT_PASSWORD: ${PLATFORM_DB_PASSWORD:-12345678}
MYSQL_DATABASE: ${PLATFORM_DB_NAME:-playwright_platform}
MINIO_ROOT_USER: ${PLATFORM_MINIO_ACCESS_KEY:-minioadmin}
MINIO_ROOT_PASSWORD: ${PLATFORM_MINIO_SECRET_KEY:-minioadmin}
```

- [ ] **Step 4: Remove schema SQL bind mount**

Remove this bind mount from `mysql.volumes`:

```yaml
- ./playwright-platform-server/src/main/resources/db/schema/SCHEMA_OVERVIEW.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro
```

- [ ] **Step 5: Enable dev profile for Compose server**

Set:

```yaml
SPRING_PROFILES_ACTIVE: dev
```

Use the same `.env` variables for server DB and MinIO credentials.

## Task 4: Update README Documentation

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Document `.env` setup**

Add this command in the Docker Compose section:

```bash
cp .env.example .env
docker compose up --build
```

- [ ] **Step 2: Document Flyway migration behavior**

Update database initialization instructions to state:

```markdown
后端启动时会通过 Flyway 自动执行 `db/migration` 下的版本化迁移脚本。`SCHEMA_OVERVIEW.sql` 仅作为结构参考，不再是首选初始化方式。
```

- [ ] **Step 3: Document production configuration**

Add production guidance:

```markdown
生产环境必须显式注入 `PLATFORM_DB_URL`、`PLATFORM_DB_USERNAME`、`PLATFORM_DB_PASSWORD`、`PLATFORM_MINIO_ENDPOINT`、`PLATFORM_MINIO_ACCESS_KEY` 和 `PLATFORM_MINIO_SECRET_KEY`，不要启用 `dev` profile。
```

## Task 5: Verify

**Files:**
- No source file changes.

- [ ] **Step 1: Check default config has no weak secret defaults**

Run:

```bash
grep -nE "12345678|minioadmin" playwright-platform-server/src/main/resources/application.yml
```

Expected: command exits with code `1` and prints no matches.

- [ ] **Step 2: Validate Compose config**

Run:

```bash
docker compose config
```

Expected: command exits with code `0`.

- [ ] **Step 3: Run backend tests**

Run:

```bash
cd playwright-platform-server
mvn test
```

Expected: command exits with code `0`.

- [ ] **Step 4: Run frontend checks**

Run:

```bash
cd playwright-platform-web
npm test
npm run build
```

Expected: both commands exit with code `0`.

## Task 6: Commit Changes

**Files:**
- Create: `playwright-platform-server/src/main/resources/db/migration/V1__init_schema.sql`
- Modify: `playwright-platform-server/src/main/resources/application.yml`
- Create: `playwright-platform-server/src/main/resources/application-dev.yml`
- Create: `.env.example`
- Modify: `.gitignore`
- Modify: `docker-compose.yml`
- Modify: `README.md`
- Create: `docs/superpowers/plans/2026-06-18-config-flyway-profile.md`

- [ ] **Step 1: Check status**

Run:

```bash
git status --short
```

Expected: only the files listed in this task are changed.

- [ ] **Step 2: Commit implementation**

Run:

```bash
git add .env.example .gitignore docker-compose.yml README.md playwright-platform-server/src/main/resources/application.yml playwright-platform-server/src/main/resources/application-dev.yml playwright-platform-server/src/main/resources/db/migration/V1__init_schema.sql docs/superpowers/plans/2026-06-18-config-flyway-profile.md
git commit -m "chore: add flyway migration and config profiles"
```

Expected: commit succeeds.
