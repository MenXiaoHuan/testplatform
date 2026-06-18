# Annotation MyBatis Full Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace XML mapper and Spring Data JPA with annotation-based MyBatis mapper interfaces across the backend.

**Architecture:** Mapper interfaces live in each module's `mapper` package and use `@Mapper` plus annotation SQL. Entities become plain Java objects, services depend only on mapper interfaces, and Flyway remains the only schema owner.

**Tech Stack:** Spring Boot 3.5, Java 21, Maven, MyBatis Spring Boot Starter, JUnit 5, Mockito, AssertJ, H2, Flyway.

---

## File Structure

- Modify `playwright-platform-server/pom.xml`: remove `spring-boot-starter-data-jpa`, keep MyBatis dependencies.
- Modify `playwright-platform-server/src/main/resources/application.yml`: remove `spring.jpa.*` and `mybatis.mapper-locations`.
- Delete `playwright-platform-server/src/main/resources/mapper/**/*.xml`.
- Modify existing mapper interfaces under `repository/mapper`, `scene/mapper`, and `audit/mapper`: add `@Mapper` and annotation SQL.
- Create `playwright-platform-server/src/main/java/com/example/platform/task/mapper/TaskMapper.java`.
- Create `playwright-platform-server/src/main/java/com/example/platform/task/mapper/ArtifactMapper.java`.
- Create `playwright-platform-server/src/main/java/com/example/platform/task/mapper/CaseResultMapper.java`.
- Create `playwright-platform-server/src/main/java/com/example/platform/task/mapper/TaskStageLogMapper.java`.
- Delete all `*JpaRepository.java` files.
- Modify all entity classes to remove `jakarta.persistence.*` imports and annotations.
- Modify task services and tests to use mapper dependencies.
- Update README to state annotation MyBatis is the only persistence layer.

---

### Task 1: Convert Existing XML Mappers To Annotation Mappers

**Files:**
- Modify `TestRepositoryMapper.java`, `SceneMapper.java`, `SceneScheduleStateMapper.java`, `PlatformAuditLogMapper.java`
- Delete `src/main/resources/mapper/**/*.xml`
- Modify mapper tests if they rely on XML mapper locations

- [ ] Add `@Mapper` to each existing mapper interface.
- [ ] Move each XML SQL statement into the matching mapper method with `@Select`, `@Insert`, `@Update`, `@Delete`, `@Options`, and `@Results` where needed.
- [ ] Remove `@MybatisTest(properties = "mybatis.mapper-locations=classpath*:mapper/**/*.xml")` from mapper tests.
- [ ] Delete all XML mapper files.
- [ ] Run `cd playwright-platform-server && mvn test -Dtest=TestRepositoryMapperTest,SceneMapperTest,SceneScheduleStateMapperTest,PlatformAuditLogMapperTest`.
- [ ] Commit with `feat: use annotation mybatis mappers`.

### Task 2: Add Task Module Annotation Mappers

**Files:**
- Create `TaskMapper.java`
- Create `ArtifactMapper.java`
- Create `CaseResultMapper.java`
- Create `TaskStageLogMapper.java`

- [ ] Implement `TaskMapper` for insert, update, findById, list pages, list by scene, recoverable status query, latest by scene, existence by scene/status, and delete by repo/scene.
- [ ] Implement `ArtifactMapper` for insert, find by task, find by case result, find by task ids, and delete by task ids.
- [ ] Implement `CaseResultMapper` for insert, find by task, and delete by task ids.
- [ ] Implement `TaskStageLogMapper` for insert, find by task, find by task ids, and delete by task ids.
- [ ] Add or update mapper tests for task, artifact, case result, and stage log SQL.
- [ ] Run `cd playwright-platform-server && mvn test -Dtest=TaskMapperTest,ArtifactMapperTest,CaseResultMapperTest,TaskStageLogMapperTest`.
- [ ] Commit with `feat: add task annotation mybatis mappers`.

### Task 3: Migrate Task Services From JPA To Mappers

**Files:**
- Modify `TaskServiceImpl.java`
- Modify `TaskCreationService.java`
- Modify `TaskExecutionOrchestrator.java`
- Modify `TaskRecoveryService.java`
- Modify `TaskStageLogServiceImpl.java`
- Modify `TaskArtifactArchiveServiceImpl.java`
- Modify `TaskCaseResultPersistenceServiceImpl.java`
- Modify `TaskQueryViewService.java`
- Modify `SceneCascadeDeleteServiceImpl.java`
- Modify related tests under `src/test/java/com/example/platform/task` and `scene`

- [ ] Replace `TaskJpaRepository`, `ArtifactJpaRepository`, `CaseResultJpaRepository`, and `TaskStageLogJpaRepository` dependencies with mapper dependencies.
- [ ] Replace JPA `save()` calls with explicit `insert()` or `update()`.
- [ ] Replace Spring Data `Page` queries with `PageResponse.of(mapperList, mapperCount, page, size)`.
- [ ] Preserve existing transaction annotations and deletion order.
- [ ] Update Mockito tests to mock mapper interfaces.
- [ ] Run `cd playwright-platform-server && mvn test -Dtest=*Task*,SceneCascadeDeleteServiceImplTest`.
- [ ] Commit with `feat: migrate task services to annotation mybatis`.

### Task 4: Remove JPA Code And Configuration

**Files:**
- Delete all `*JpaRepository.java`
- Modify all entity model classes
- Modify `pom.xml`
- Modify `application.yml`
- Modify tests that still import JPA repository types

- [ ] Remove `spring-boot-starter-data-jpa` from `pom.xml`.
- [ ] Remove `spring.jpa.*` from `application.yml`.
- [ ] Remove `mybatis.mapper-locations` from `application.yml`.
- [ ] Delete all `*JpaRepository.java`.
- [ ] Remove all `jakarta.persistence.*` imports and JPA annotations from entity classes.
- [ ] Run searches for `JpaRepository`, `org.springframework.data.jpa`, and `jakarta.persistence`; expected no matches in `src/main/java` or `src/test/java`.
- [ ] Run `cd playwright-platform-server && mvn test`.
- [ ] Commit with `refactor: remove spring data jpa`.

### Task 5: Final Validation And Docs

**Files:**
- Modify `README.md`
- Validate backend, frontend, and Compose

- [ ] Update README persistence note to state annotation MyBatis is the only backend persistence layer.
- [ ] Run `cd playwright-platform-server && mvn test`.
- [ ] Run `cd playwright-platform-web && npm test`.
- [ ] Run `cd playwright-platform-web && npm run build`.
- [ ] Run `docker compose config`.
- [ ] Commit with `docs: document annotation mybatis persistence`.

---

## Self-Review

- Spec coverage: covers annotation mapper conversion, XML deletion, JPA dependency removal, repository deletion, entity annotation cleanup, task module full migration, tests, and final validation.
- Placeholder scan: no deferred placeholders; each task has exact file groups, commands, and commit boundaries.
- Type consistency: mapper naming follows existing module packages and service dependencies.

---

## Execution Handoff

Plan saved to `docs/superplans/plans/2026-06-18-annotation-mybatis-full-migration.md`. User has requested direct execution with方案 A, so implementation should proceed immediately task by task with review after each commit.
