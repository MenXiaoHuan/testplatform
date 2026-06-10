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

Useful framework commands:

```bash
npm run test:e2e
npm run test:e2e:headed
npm run report:open
```
