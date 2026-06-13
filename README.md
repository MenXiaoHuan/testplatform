# Playwright Test Platform Workspace

This repository combines the Playwright framework, the platform backend, and the platform frontend into one Git workspace.

## Workspace Structure

- `playwright_framework`: Playwright test framework and platform-facing test artifact producer
- `playwright-platform-server`: Spring Boot backend for repository, scene, task, report, and artifact management
- `playwright-platform-web`: Vue 3 frontend for platform management and task detail pages

## Prerequisites

- Node.js 20+
- npm
- Java 21
- Maven 3.9+
- MySQL
- MinIO

## Git Usage

Run Git commands from the workspace root:

```bash
cd /Users/bytedance/test_platform
```

Legacy nested Git metadata is backed up under `.tmp/git-boundary-backups/`.

## Recommended Startup Order

1. Start the backend.
2. Start the frontend.
3. Run framework tests from `playwright_framework` when you need to produce test runs or artifacts.

## Start Services

### Backend

```bash
./scripts/dev-server.sh
```

### Frontend

```bash
./scripts/dev-web.sh --host 0.0.0.0 --port 4173
```

## Run Tests

### Backend tests

```bash
./scripts/test-server.sh
```

### Frontend tests

```bash
./scripts/test-web.sh
```

## Framework Usage

Framework commands continue to run from `playwright_framework`:

```bash
cd playwright_framework
npm run test:e2e
```

If you wrap Playwright execution in an npm script and want the platform to append test targets or filters, keep the command in a pass-through form such as `npm run test:e2e --` so the extra arguments are forwarded correctly.

Useful framework commands:

```bash
npm run test:e2e
npm run test:e2e:headed
npm run report:open
npm run test:e2e -- --grep @smoke
```

## Repository Configuration

- `Git 地址`: point to the GitHub repository URL that the platform should clone
- `工作目录`: optional; leave blank for root projects, use values like `playwright_framework` for monorepo subprojects
- `安装命令`: defaults to Playwright native install, for example `npm install && npx playwright install`; if the repository wraps setup in its own script, you can override it
- `测试执行命令`: defaults to Playwright native execution, for example `npx playwright test`; if the repository wraps execution in its own script, the command must stay in a pass-through form such as `npm run test:e2e --`, otherwise the platform cannot forward appended spec paths or filters
- `测试目录`: relative to the working directory, for example `tests`
- `报告目录`: relative to the working directory, for example `reports/allure-report`
- `仓库启停`: disabled repositories stay hidden from scene creation choices, and scenes under them cannot be executed until the repository is enabled again

## Scene Configuration

- Scenes no longer have an enable/disable switch; creating a scene means it is always available as a configuration item
- The scene list uses a single `执行` icon column for manual runs
- The `执行` icon is disabled only when the linked repository is disabled
- `用例路径/目录` is optional; leave it blank to run all tests under the repository test root
- `启用定时` controls only scheduled execution; `Cron 表达式` appears only when scheduling is enabled
