# Stage One CI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a minimal GitHub Actions CI gate that verifies backend tests, frontend dependency installation, frontend tests, and frontend production build.

**Architecture:** The CI workflow lives in `.github/workflows/ci.yml` and contains independent backend and frontend jobs. Backend uses Java 21 with Maven caching; frontend uses Node.js 20 with npm caching and installs from `package-lock.json`.

**Tech Stack:** GitHub Actions, Java 21, Maven, Node.js 20, npm, Vue/Vite, Vitest.

---

## File Structure

- Create `.github/workflows/ci.yml`: defines CI triggers and the backend/frontend jobs.
- Modify `README.md`: adds a short CI section that documents the automatic checks.

## Task 1: Add GitHub Actions CI Workflow

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Create the workflow directory**

Run:

```bash
mkdir -p .github/workflows
```

Expected: command exits with code `0`.

- [ ] **Step 2: Create `.github/workflows/ci.yml`**

Write this exact content:

```yaml
name: CI

on:
  push:
  pull_request:

jobs:
  backend:
    name: Backend Tests
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: playwright-platform-server
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Run backend tests
        run: mvn test

  frontend:
    name: Frontend Tests And Build
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: playwright-platform-web
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: playwright-platform-web/package-lock.json

      - name: Install frontend dependencies
        run: npm ci

      - name: Run frontend tests
        run: npm test

      - name: Build frontend
        run: npm run build
```

- [ ] **Step 3: Validate workflow file is staged for review**

Run:

```bash
git diff -- .github/workflows/ci.yml
```

Expected: diff shows the workflow content from Step 2.

## Task 2: Document CI Gate In README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add CI section after development commands**

Insert this section after the frontend development commands block:

```markdown
## CI

项目使用 GitHub Actions 作为主干质量门禁。每次 `push` 和 `pull_request` 会自动执行：

- 后端：`mvn test`
- 前端：`npm ci`
- 前端：`npm test`
- 前端：`npm run build`

这些检查用于确保主干代码始终可测试、依赖可复现安装，并且前端可以完成生产构建。
```

- [ ] **Step 2: Review README diff**

Run:

```bash
git diff -- README.md
```

Expected: diff only adds the CI section and does not change existing startup or test instructions.

## Task 3: Local Verification

**Files:**
- No source file changes.

- [ ] **Step 1: Run backend tests**

Run:

```bash
cd playwright-platform-server
mvn test
```

Expected: command exits with code `0`.

- [ ] **Step 2: Run frontend dependency installation**

Run:

```bash
cd playwright-platform-web
npm ci
```

Expected: command exits with code `0` and installs dependencies according to `package-lock.json`.

- [ ] **Step 3: Run frontend tests**

Run:

```bash
cd playwright-platform-web
npm test
```

Expected: command exits with code `0`.

- [ ] **Step 4: Run frontend production build**

Run:

```bash
cd playwright-platform-web
npm run build
```

Expected: command exits with code `0`.

## Task 4: Commit CI Changes

**Files:**
- Create: `.github/workflows/ci.yml`
- Modify: `README.md`
- Create: `docs/superpowers/plans/2026-06-18-ci-stage-one.md`

- [ ] **Step 1: Check status**

Run:

```bash
git status --short
```

Expected: changed files are `.github/workflows/ci.yml`, `README.md`, and this plan file.

- [ ] **Step 2: Commit implementation**

Run:

```bash
git add .github/workflows/ci.yml README.md docs/superpowers/plans/2026-06-18-ci-stage-one.md
git commit -m "ci: add stage one quality gate"
```

Expected: commit succeeds.
