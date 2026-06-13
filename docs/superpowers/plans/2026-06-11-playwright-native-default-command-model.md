# Playwright Native Default Command Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the platform default to standard Playwright commands while preserving compatibility for repositories that still use a wrapper script such as `npm run test:e2e --`.

**Architecture:** Update repository form defaults and guidance to favor `npm install && npx playwright install` plus `npx playwright test`. In the backend, teach `TaskCommandBuilder` to detect native Playwright commands and append the test file as a positional argument, while keeping the existing `--target` behavior for script-wrapper repositories.

**Tech Stack:** Vue 3, Pinia, Element Plus, Vitest, Spring Boot, JUnit, Mockito

---

### Task 1: Update Frontend Repository Defaults

**Files:**
- Modify: `playwright-platform-web/src/types/repository.ts`
- Modify: `playwright-platform-web/src/views/repository/RepositoryListView.vue`
- Modify: `README.md`
- Test: `playwright-platform-web/tests/unit/views/repository/RepositoryListView.test.ts`
- Test: `playwright-platform-web/tests/unit/stores/repository.test.ts`

- [ ] Write failing frontend tests that expect repository defaults to use native Playwright commands.
- [ ] Run the focused frontend tests and verify they fail.
- [ ] Change the form defaults to `npm install && npx playwright install` and `npx playwright test`.
- [ ] Update placeholder/help text to describe native Playwright as the default and wrapper scripts as an override.
- [ ] Update README examples to match the new default model.
- [ ] Re-run the focused frontend tests until they pass.

### Task 2: Add Dual-Mode Backend Command Building

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskCommandBuilderImpl.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`

- [ ] Write failing backend tests for both native Playwright mode and wrapper-script compatibility mode.
- [ ] Run the focused backend tests and verify they fail.
- [ ] Detect whether the repository command starts with `npx playwright test`.
- [ ] In native mode, append `<testRoot>/<matchValue>` as a positional argument and append `--project` normally.
- [ ] In wrapper mode, preserve the current `--target` behavior.
- [ ] Re-run the focused backend tests until they pass.

### Task 3: Verify End-To-End Defaults And Compatibility

**Files:**
- Verify: `playwright-platform-web/tests/unit/views/repository/RepositoryListView.test.ts`
- Verify: `playwright-platform-web/tests/unit/stores/repository.test.ts`
- Verify: `playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`
- Verify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskCommandBuilderImpl.java`
- Verify: `playwright-platform-web/src/views/repository/RepositoryListView.vue`

- [ ] Run frontend regression: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- tests/unit/views/repository/RepositoryListView.test.ts tests/unit/stores/repository.test.ts`
- [ ] Run backend regression: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=TaskExecutionServiceTest test`
- [ ] Run diagnostics on `TaskCommandBuilderImpl.java` and `RepositoryListView.vue`.
- [ ] Manually validate that native defaults still allow wrapper-script repositories to override the command.
