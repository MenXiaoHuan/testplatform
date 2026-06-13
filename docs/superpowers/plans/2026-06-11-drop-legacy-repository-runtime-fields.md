# Drop Legacy Repository Runtime Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `packageManager` and `nodeVersion` from the repository database schema, backend model, frontend list rendering, and tests in one pass.

**Architecture:** Delete the legacy fields at the schema boundary first, then simplify the repository service so create and update only operate on active execution fields. Update the frontend repository list and all focused tests to match the new repository model.

**Tech Stack:** Spring Boot, JPA, Flyway SQL migration, Vue 3, Element Plus, Vitest, JUnit, Mockito

---

### Task 1: Remove Backend Legacy Fields

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/model/TestRepositoryEntity.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryServiceImpl.java`
- Create: `playwright-platform-server/src/main/resources/db/migration/V6__drop_repository_legacy_runtime_fields.sql`
- Test: `playwright-platform-server/src/test/java/com/example/platform/repository/RepositoryControllerTest.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/repository/RepositoryServiceImplTest.java`

- [ ] Write failing backend tests that no longer send or assert `packageManager` and `nodeVersion`.
- [ ] Run focused backend tests and confirm failure.
- [ ] Remove the fields from `TestRepositoryEntity`.
- [ ] Remove hidden default logic from `RepositoryServiceImpl`.
- [ ] Add Flyway migration to drop `package_manager` and `node_version`.
- [ ] Re-run focused backend tests until they pass.

### Task 2: Remove Frontend Legacy Rendering

**Files:**
- Modify: `playwright-platform-web/src/views/repository/RepositoryListView.vue`
- Test: `playwright-platform-web/tests/unit/views/repository/RepositoryListView.test.ts`
- Test: `playwright-platform-web/tests/unit/views/listColumnWidth.test.ts`

- [ ] Write failing frontend assertions that repository list no longer renders `包管理器`.
- [ ] Run focused frontend tests and confirm failure.
- [ ] Remove the `包管理器` table column.
- [ ] Update repository view tests and width assertions to the new structure.
- [ ] Re-run focused frontend tests until they pass.

### Task 3: Verify And Diagnose

**Files:**
- Verify: `playwright-platform-server/src/test/java/com/example/platform/config/EntitySchemaMappingTest.java`
- Verify: `playwright-platform-server/src/main/java/com/example/platform/repository/model/TestRepositoryEntity.java`
- Verify: `playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryServiceImpl.java`
- Verify: `playwright-platform-web/src/views/repository/RepositoryListView.vue`

- [ ] Run the focused backend regression: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=RepositoryControllerTest,RepositoryServiceImplTest,EntitySchemaMappingTest test`
- [ ] Run the focused frontend regression: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- tests/unit/views/repository/RepositoryListView.test.ts tests/unit/views/listColumnWidth.test.ts`
- [ ] Run diagnostics on the edited Java and Vue files and fix any introduced issues.
