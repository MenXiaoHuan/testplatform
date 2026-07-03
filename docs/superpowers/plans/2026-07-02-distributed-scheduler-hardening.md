# Distributed Scheduler Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不引入 MQ 的前提下，把当前定时调度改成“数据库原子认领 + 调度事件幂等”的更严格实现。

**Architecture:** 保留 `@Scheduled -> SceneSchedulerServiceImpl -> createScheduledTask` 主链路，只强化调度层一致性。通过 `scene_schedule_state` 原子认领调度点，再用 `schedule_event(scene_id, planned_fire_at)` 唯一键兜底幂等，并把任务创建结果回填到事件表。

**Tech Stack:** Spring Boot, MyBatis, Flyway, MySQL, JUnit 5, Mockito

---

### Task 1: Add failing lease acquisition tests

**Files:**
- Modify: `playwright-platform-server/src/test/java/com/example/platform/scene/SceneScheduleLeaseServiceImplTest.java`

- [ ] **Step 1: Write the failing tests**

补充用例，覆盖：
- 新建状态时应写入可注入的实例 ID，而不是固定 `local-scheduler`
- 同一个 `plannedFireAt` 第二次认领失败
- 已存在状态且更晚 `plannedFireAt` 时，通过条件更新成功认领

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=SceneScheduleLeaseServiceImplTest test`

- [ ] **Step 3: Write minimal implementation**

改造 `SceneScheduleLeaseServiceImpl`、`SceneScheduleStateMapper`，支持：
- 实例 ID 提供者
- 初始化插入
- 条件更新认领

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=SceneScheduleLeaseServiceImplTest test`

### Task 2: Add schedule event persistence

**Files:**
- Add: `playwright-platform-server/src/main/resources/db/migration/V2__add_schedule_event.sql`
- Modify: `playwright-platform-server/src/main/resources/db/schema/SCHEMA_OVERVIEW.sql`
- Add: `playwright-platform-server/src/main/java/com/example/platform/scene/model/ScheduleEventEntity.java`
- Add: `playwright-platform-server/src/main/java/com/example/platform/scene/mapper/ScheduleEventMapper.java`
- Add: `playwright-platform-server/src/main/java/com/example/platform/scene/service/ScheduleEventService.java`
- Add: `playwright-platform-server/src/main/java/com/example/platform/scene/service/ScheduleEventServiceImpl.java`

- [ ] **Step 1: Write the failing tests**

补充调度事件服务测试，覆盖：
- 首次插入 `scene + plannedFireAt` 成功
- 重复插入同一调度点触发幂等冲突
- 任务创建成功后可更新状态和 `taskId`

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=ScheduleEventServiceImplTest test`

- [ ] **Step 3: Write minimal implementation**

创建迁移、实体、Mapper、Service，并把唯一键约束落到 DB schema。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=ScheduleEventServiceImplTest test`

### Task 3: Wire schedule events into scheduler

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneSchedulerServiceImpl.java`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/scene/SceneServiceImplTest.java`

- [ ] **Step 1: Write the failing tests**

补充调度流程测试，覆盖：
- 成功认领后创建事件、推进 `next_run_at`、创建 task、回填事件
- 重复调度点时跳过任务创建
- `createScheduledTask(...)` 失败时事件标记为 `FAILED`

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=SceneServiceImplTest test`

- [ ] **Step 3: Write minimal implementation**

在 `SceneSchedulerServiceImpl` 中接入 `ScheduleEventService`，保持原有任务执行链路不变。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=SceneServiceImplTest test`

### Task 4: Add instance identity provider and configuration

**Files:**
- Add: `playwright-platform-server/src/main/java/com/example/platform/scene/service/SchedulerInstanceIdProvider.java`
- Modify: `playwright-platform-server/src/main/resources/application.yml`
- Modify: related tests as needed

- [ ] **Step 1: Write the failing tests**

覆盖：
- 配置存在时返回配置值
- 配置缺省时生成稳定实例 ID

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=SchedulerInstanceIdProviderTest test`

- [ ] **Step 3: Write minimal implementation**

添加实例 ID 提供者，并让调度租约逻辑依赖该提供者。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=SchedulerInstanceIdProviderTest test`

### Task 5: Verify integrated behavior

**Files:**
- No new files expected

- [ ] **Step 1: Run focused scheduler test suite**

Run: `mvn -Dtest=SceneScheduleLeaseServiceImplTest,SceneServiceImplTest,ScheduleEventServiceImplTest,SchedulerInstanceIdProviderTest test`

- [ ] **Step 2: Run broader scene/task safety checks**

Run: `mvn -Dtest=SceneControllerTest,TaskExecutionServiceTest,TransactionBoundaryTest test`

- [ ] **Step 3: Review schema and migration consistency**

检查：
- `V2__add_schedule_event.sql`
- `SCHEMA_OVERVIEW.sql`
- 新增 mapper/entity/service 是否和 schema 一致

- [ ] **Step 4: Commit**

```bash
git add playwright-platform-server/src/main/resources/db/migration/V2__add_schedule_event.sql \
  playwright-platform-server/src/main/resources/db/schema/SCHEMA_OVERVIEW.sql \
  playwright-platform-server/src/main/java/com/example/platform/scene \
  playwright-platform-server/src/test/java/com/example/platform/scene \
  docs/superpowers/plans/2026-07-02-distributed-scheduler-hardening.md
git commit -m "feat: harden distributed scheduler coordination"
```
