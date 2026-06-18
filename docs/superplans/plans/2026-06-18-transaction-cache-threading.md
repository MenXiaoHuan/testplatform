# Transaction Cache Threading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add explicit short-write transactions, Redis-backed detail caching with penetration/breakdown/avalanche protection, and documented custom thread-pool governance.

**Architecture:** Keep long task execution outside a single transaction, but wrap short database mutations in explicit transactional methods. Add a small Redis cache service for repository, scene, and task details with null caching, random TTL jitter, mutex loading, and write-through invalidation. Keep `taskExecutionExecutor` as the custom task pool and document both task and Tomcat request thread configuration.

**Tech Stack:** Spring Boot 3.5, Java 21, MyBatis, Spring Data Redis, Redis, Docker Compose, JUnit 5, Mockito, AssertJ.

---

## File Structure

- Modify `playwright-platform-server/pom.xml`: add `spring-boot-starter-data-redis`.
- Modify `playwright-platform-server/src/main/resources/application.yml`: add Redis, cache, and Tomcat thread settings.
- Modify `docker-compose.yml`: add Redis service and server Redis env.
- Create `playwright-platform-server/src/main/java/com/example/platform/cache/CacheProperties.java`.
- Create `playwright-platform-server/src/main/java/com/example/platform/cache/DetailCacheService.java`.
- Create `playwright-platform-server/src/main/java/com/example/platform/cache/CachedValue.java`.
- Create tests under `playwright-platform-server/src/test/java/com/example/platform/cache`.
- Modify `RepositoryServiceImpl`, `SceneServiceImpl`, `TaskServiceImpl`, `TaskCreationService`, `TaskRecoveryService`, `SceneSchedulerServiceImpl`, `SceneScheduleLeaseServiceImpl`, and related services for transaction and cache invalidation.
- Modify README for Redis cache, transaction policy, custom task pool, Tomcat threads, rejection compensation, and recovery.

---

### Task 1: Redis Detail Cache Foundation

- [ ] Add `spring-boot-starter-data-redis`.
- [ ] Add Redis and cache config to `application.yml`.
- [ ] Add Redis to `docker-compose.yml`.
- [ ] Create `CacheProperties`, `CachedValue`, and `DetailCacheService`.
- [ ] Tests must cover normal hit, null caching, TTL jitter range, mutex loader single execution, and invalidation.
- [ ] Run `cd playwright-platform-server && mvn test -Dtest=DetailCacheServiceTest`.
- [ ] Commit `feat: add redis detail cache foundation`.

### Task 2: Cache Details And Invalidate Writes

- [ ] Cache `RepositoryServiceImpl.get(id)`.
- [ ] Cache `SceneServiceImpl.get(id)`.
- [ ] Cache `TaskServiceImpl.getDetail(taskId)`.
- [ ] Invalidate repository cache on repository create/update/delete.
- [ ] Invalidate scene cache on scene create/update/delete and scene summary updates.
- [ ] Invalidate task cache on task cancel, rejection, execution status updates, recovery updates, and finalization.
- [ ] Add service tests for cache hit and invalidation.
- [ ] Run `cd playwright-platform-server && mvn test -Dtest=RepositoryServiceImplTest,SceneServiceImplTest,TaskExecutionServiceTest,TaskRecoveryServiceTest`.
- [ ] Commit `feat: cache detail queries with redis`.

### Task 3: Transaction Boundaries

- [ ] Add `@Transactional` to repository create/update/delete methods.
- [ ] Add `@Transactional` to scene create/update/delete methods.
- [ ] Add `@Transactional` to task cancel and short mutation helpers.
- [ ] Keep long `TaskExecutionOrchestrator.executeTask` non-transactional.
- [ ] Add transactional helper methods where needed for task state updates and scene summary updates.
- [ ] Add tests verifying expected methods have `@Transactional` and long execution method does not.
- [ ] Run `cd playwright-platform-server && mvn test -Dtest=*Transaction*,RepositoryServiceImplTest,SceneServiceImplTest,TaskExecutionServiceTest`.
- [ ] Commit `feat: add write transaction boundaries`.

### Task 4: Thread Pool Governance Docs And Config

- [ ] Add Tomcat thread settings to `application.yml`.
- [ ] Keep `taskExecutionExecutor` as the custom pool.
- [ ] Improve README with task pool parameters, rejection alert behavior, `FAILED/SYSTEM_BUSY` compensation, startup recovery, and Tomcat request thread settings.
- [ ] Add or update tests for task pool rejection behavior if needed.
- [ ] Run `cd playwright-platform-server && mvn test -Dtest=TaskExecutionMonitorServiceTest,TaskExecutionServiceTest`.
- [ ] Commit `docs: document thread pool governance`.

### Task 5: Final Validation

- [ ] Run `cd playwright-platform-server && mvn test`.
- [ ] Run `cd playwright-platform-web && npm test`.
- [ ] Run `cd playwright-platform-web && npm run build`.
- [ ] Run `docker compose config`.
- [ ] Commit final documentation or config adjustments if needed.

---

## Self-Review

- Spec coverage: covers Redis cache, cache protection, invalidation, transaction boundaries, custom task pool, Tomcat request threads, rejection compensation, and recovery docs.
- Placeholder scan: no placeholders or deferred tasks.
- Type consistency: cache service names and config keys match the design document.

---

## Execution Handoff

Plan saved to `docs/superplans/plans/2026-06-18-transaction-cache-threading.md`. User requested implementation after confirmation, so proceed directly task by task with TDD and validation.
