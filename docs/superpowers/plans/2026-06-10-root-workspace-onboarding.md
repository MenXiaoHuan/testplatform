# Root Workspace Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a root-level onboarding README and thin wrapper scripts so developers can work from `/Users/bytedance/test_platform` without memorizing subproject-specific entrypoints.

**Architecture:** Keep each existing subproject as the source of truth for build and run commands. Add only a root `README.md` and a small `scripts/` directory whose shell wrappers resolve the repository root, switch into the correct child directory, and delegate to the existing command.

**Tech Stack:** Markdown, POSIX shell, npm, Maven, Spring Boot, Vue/Vite

---

## File Map

- Create: `/Users/bytedance/test_platform/README.md`
- Create: `/Users/bytedance/test_platform/scripts/dev-server.sh`
- Create: `/Users/bytedance/test_platform/scripts/dev-web.sh`
- Create: `/Users/bytedance/test_platform/scripts/test-server.sh`
- Create: `/Users/bytedance/test_platform/scripts/test-web.sh`
- Modify: `/Users/bytedance/test_platform/docs/superpowers/plans/2026-06-10-root-workspace-onboarding.md`

### Task 1: Add Root README

**Files:**
- Create: `/Users/bytedance/test_platform/README.md`
- Reference: `/Users/bytedance/test_platform/playwright_framework/package.json`
- Reference: `/Users/bytedance/test_platform/playwright-platform-web/package.json`
- Reference: `/Users/bytedance/test_platform/playwright-platform-server/pom.xml`

- [ ] **Step 1: Write the root README**

```md
# Playwright Test Platform Workspace

This repository contains the Playwright framework, the platform backend, and the platform frontend in one Git workspace.

## Structure

- `playwright_framework`: Playwright test framework and test artifact producer
- `playwright-platform-server`: Spring Boot backend
- `playwright-platform-web`: Vue 3 frontend

## Prerequisites

- Node.js 20+
- npm
- Java 21
- Maven 3.9+
- MySQL
- MinIO

## Git

Run Git commands from `/Users/bytedance/test_platform`.

Legacy nested Git metadata has been backed up under `.tmp/git-boundary-backups/`.

## Start Services

### Backend

```bash
./scripts/dev-server.sh
```

### Frontend

```bash
./scripts/dev-web.sh
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

Framework commands still run from `playwright_framework`.

```bash
cd playwright_framework
npm run test:e2e
```
```

- [ ] **Step 2: Verify the README mentions only real commands**

Run: `grep -n "npm run dev\\|npm test\\|mvn spring-boot:run\\|mvn test\\|npm run test:e2e" /Users/bytedance/test_platform/README.md`
Expected: Output shows only commands that exist in child projects.

- [ ] **Step 3: Commit the README**

```bash
git add /Users/bytedance/test_platform/README.md
git commit -m "docs: add root workspace readme"
```

### Task 2: Add Root Wrapper Scripts

**Files:**
- Create: `/Users/bytedance/test_platform/scripts/dev-server.sh`
- Create: `/Users/bytedance/test_platform/scripts/dev-web.sh`
- Create: `/Users/bytedance/test_platform/scripts/test-server.sh`
- Create: `/Users/bytedance/test_platform/scripts/test-web.sh`

- [ ] **Step 1: Create the backend dev wrapper**

```sh
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="$ROOT_DIR/playwright-platform-server"

[ -d "$TARGET_DIR" ] || {
  echo "Missing directory: $TARGET_DIR" >&2
  exit 1
}

cd "$TARGET_DIR"
exec mvn spring-boot:run "$@"
```

- [ ] **Step 2: Create the frontend dev wrapper**

```sh
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="$ROOT_DIR/playwright-platform-web"

[ -d "$TARGET_DIR" ] || {
  echo "Missing directory: $TARGET_DIR" >&2
  exit 1
}

cd "$TARGET_DIR"
exec npm run dev -- "$@"
```

- [ ] **Step 3: Create the backend test wrapper**

```sh
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="$ROOT_DIR/playwright-platform-server"

[ -d "$TARGET_DIR" ] || {
  echo "Missing directory: $TARGET_DIR" >&2
  exit 1
}

cd "$TARGET_DIR"
exec mvn test "$@"
```

- [ ] **Step 4: Create the frontend test wrapper**

```sh
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="$ROOT_DIR/playwright-platform-web"

[ -d "$TARGET_DIR" ] || {
  echo "Missing directory: $TARGET_DIR" >&2
  exit 1
}

cd "$TARGET_DIR"
exec npm test -- "$@"
```

- [ ] **Step 5: Make the wrappers executable**

Run: `chmod +x /Users/bytedance/test_platform/scripts/*.sh`
Expected: No output and shell scripts become executable.

- [ ] **Step 6: Commit the wrapper scripts**

```bash
git add /Users/bytedance/test_platform/scripts
git commit -m "chore: add root workspace wrapper scripts"
```

### Task 3: Validate the Root Onboarding Layer

**Files:**
- Test: `/Users/bytedance/test_platform/README.md`
- Test: `/Users/bytedance/test_platform/scripts/dev-server.sh`
- Test: `/Users/bytedance/test_platform/scripts/dev-web.sh`
- Test: `/Users/bytedance/test_platform/scripts/test-server.sh`
- Test: `/Users/bytedance/test_platform/scripts/test-web.sh`

- [ ] **Step 1: Run shell syntax validation**

Run: `bash -n /Users/bytedance/test_platform/scripts/dev-server.sh /Users/bytedance/test_platform/scripts/dev-web.sh /Users/bytedance/test_platform/scripts/test-server.sh /Users/bytedance/test_platform/scripts/test-web.sh`
Expected: No output.

- [ ] **Step 2: Verify test wrappers delegate correctly**

Run: `/Users/bytedance/test_platform/scripts/test-web.sh`
Expected: Frontend test suite runs from `playwright-platform-web`.

Run: `/Users/bytedance/test_platform/scripts/test-server.sh`
Expected: Backend test suite runs from `playwright-platform-server`.

- [ ] **Step 3: Verify dev wrappers print the expected startup command path**

Run: `sed -n '1,120p' /Users/bytedance/test_platform/scripts/dev-server.sh`
Expected: Contains `exec mvn spring-boot:run "$@"`.

Run: `sed -n '1,120p' /Users/bytedance/test_platform/scripts/dev-web.sh`
Expected: Contains `exec npm run dev -- "$@"`.

- [ ] **Step 4: Check repository status**

Run: `git -C /Users/bytedance/test_platform status --short --branch`
Expected: Only the intended README and script changes are present before the final commit, then a clean working tree after commit.

- [ ] **Step 5: Commit the validation-backed changes**

```bash
git add /Users/bytedance/test_platform/README.md /Users/bytedance/test_platform/scripts
git commit -m "docs: add root onboarding entrypoints"
```
