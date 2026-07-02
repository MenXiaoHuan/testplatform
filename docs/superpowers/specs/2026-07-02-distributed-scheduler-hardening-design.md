# Distributed Scheduler Hardening Design

## Background

The current scheduler is driven by Spring `@Scheduled(fixedDelay = 60000)` in `SceneServiceImpl`. Each application instance scans due scenes and delegates to `SceneSchedulerServiceImpl`, which attempts to guard duplicate triggers through `SceneScheduleLeaseServiceImpl` and the `scene_schedule_state` table.

This works reasonably in a single-instance deployment, but it is not yet a strict distributed scheduler design. The current lease logic is optimistic at the Java layer:

- It reads `scene_schedule_state`
- Compares `lastPlannedFireAt`
- Writes the updated state

That leaves concurrency windows under multi-instance scans. Two instances can race on the same `sceneId + plannedFireAt` and both believe they are allowed to trigger. The current `lease_owner` value is also static (`local-scheduler`), so the system cannot accurately identify the holder of a lease.

The task execution pipeline itself is already good enough for this phase:

- Scheduler creates a scheduled task
- Task enters the existing task execution executor
- Runner and artifact pipeline remain unchanged

The goal of this design is to harden the scheduling layer without introducing a message queue or rewriting the execution model.

## Goal

Make scheduled scene triggering safe under multi-instance deployment by ensuring that, for a given `scene` and `plannedFireAt`, at most one task is created.

## Non-Goals

This phase does **not** include:

- Introducing RabbitMQ, Kafka, RocketMQ, or Redis queue consumers
- Splitting scheduler and executor into separate services
- Adding lease heartbeats or lease renewal
- Adding automatic retry or compensation for failed scheduled trigger events
- Reworking task execution concurrency or the Playwright runner model

## Recommended Approach

Use a two-layer database-backed safety model:

1. **Atomic schedule-state claim**
   Use `scene_schedule_state` as the per-scene scheduling concurrency record. Replace the current read-check-write flow with mapper-level conditional SQL so that only one instance can successfully claim a given scheduling point.

2. **Idempotent schedule event record**
   Introduce a new `schedule_event` table with a unique key on `(scene_id, planned_fire_at)`. Even if the schedule-state claim layer sees an edge-case race or a retried code path, the unique key guarantees that only one persistent scheduling event exists for a given scene and planned fire time.

This preserves the current system shape:

```text
@Scheduled scan
-> find due scenes
-> atomically claim scene scheduling point
-> insert idempotent schedule_event
-> advance scene.next_run_at
-> createScheduledTask(...)
-> update schedule_event with task result
```

This is the best fit for the current repository because it materially improves correctness without forcing a queueing system into a codebase that does not yet model scheduling as an event bus.

## Alternatives Considered

### 1. Keep the current implementation and tighten Java-side checks

This was rejected because the concurrency problem is fundamentally at the persistence boundary. More Java-side checks still leave races between read and write operations across instances.

### 2. Introduce MQ-based single-consumer scheduling now

This was rejected for this phase because the codebase does not yet have a scheduling event producer/consumer architecture. Adding MQ now would widen the scope from “scheduler hardening” to “scheduler redesign”.

### 3. Redis queue plus distributed lock

This was rejected because it adds queue semantics and lock semantics without giving the reliability guarantees of a dedicated message system, while still requiring application-level idempotency.

## Functional Requirements

### FR1. Due scene detection remains unchanged

The system continues to treat a scene as due when:

- `schedule_enabled = true`
- `cron_expression` is present
- `next_run_at <= now`

No changes are required to the current query contract in `SceneMapper.findDueScheduledScenes`.

### FR2. A planned fire point can be claimed only once

For a given `sceneId` and `plannedFireAt`, only one application instance may successfully claim the right to proceed with scheduling logic.

This must be enforced by conditional database mutation, not only by Java-side checks.

### FR3. A schedule event exists at most once per scene and planned fire time

The system must persist exactly one schedule event row for a given:

- `scene_id`
- `planned_fire_at`

If duplicate insertion is attempted, it must be treated as an already-processed scheduling point rather than as a trigger to create another task.

### FR4. Different scenes with the same fire time still trigger independently

If multiple scenes share the same `plannedFireAt`, each scene may create its own schedule event and scheduled task. The deduplication boundary is:

- per scene
- per planned fire time

It is **not** global by timestamp.

### FR5. The existing task creation guard remains in place

The system must continue to block concurrent active tasks for the same scene through the existing `TaskCreationService` logic that checks `QUEUED` and `RUNNING` tasks.

The new scheduling hardening complements that guard; it does not replace it.

### FR6. Failed scheduled dispatch must be persisted

If a schedule event is successfully claimed and inserted, but task creation fails, the system must keep a record of that failure in the schedule event table.

This phase does not retry automatically, but it must leave enough state for diagnosis.

## Data Model Changes

### Existing Table: `scene_schedule_state`

Keep the existing table and use it more strictly.

Current relevant fields:

- `scene_id`
- `last_planned_fire_at`
- `last_triggered_at`
- `last_task_id`
- `lease_owner`
- `lease_until`
- `version`

#### Required behavior changes

- `lease_owner` must contain a real instance identifier, not a static constant
- updates must be conditional and atomic
- `version` should be incremented on successful claim/update

### New Table: `schedule_event`

Add a new table with the following fields:

- `id` bigint primary key auto increment
- `scene_id` bigint not null
- `planned_fire_at` datetime not null
- `status` varchar(32) not null
- `task_id` bigint null
- `trigger_reason` varchar(128) not null
- `error_message` varchar(512) null
- `created_at` datetime not null default current_timestamp
- `updated_at` datetime not null default current_timestamp on update current_timestamp

Constraints:

- foreign key from `scene_id` to `scene(id)`
- foreign key from `task_id` to `task(id)` if task is created
- unique key on `(scene_id, planned_fire_at)`

#### Status values

This phase uses a small, explicit status model:

- `CLAIMED`: scheduling point was persisted, task not yet created
- `TASK_CREATED`: scheduled task was successfully created
- `FAILED`: task creation failed after claim

No retry state is introduced in this phase.

## Instance Identity

Add a scheduler instance identifier resolved at application startup. It should be stable for the process lifetime and usable in logs and schedule-state records.

Preferred shape:

- environment variable override if provided
- otherwise generated from hostname + process identifier + random suffix

This identifier is only for observability and lease ownership. It is not an authentication boundary.

## Detailed Flow

### Step 1. Scheduled scan

`SceneServiceImpl.triggerScheduledScenes()` continues to run every minute and delegates to `SceneSchedulerServiceImpl.triggerDueScenes(now)`.

### Step 2. Legacy initialization

Legacy scenes with `next_run_at is null` continue to be initialized through the existing resolver path before due-scene processing.

### Step 3. Atomic claim of scheduling point

For each due scene:

- read `plannedFireAt = scene.nextRunAt`
- attempt to claim the scheduling point through a conditional mapper update

Successful claim requires:

- the row for the scene is absent and can be inserted, or
- the stored `last_planned_fire_at` is older than the current `plannedFireAt`

Failed claim means another instance already processed or claimed that exact fire point. Processing must stop for this scene.

### Step 4. Insert `schedule_event`

After claim succeeds, insert a `schedule_event` row with:

- `scene_id`
- `planned_fire_at`
- `status = CLAIMED`
- `trigger_reason = cron:<expression>`

If insertion fails on the unique constraint, treat that as “already processed” and stop for this scene without creating a task.

This unique insert is the second safety barrier.

### Step 5. Advance `scene.next_run_at`

Resolve and persist the next scheduled fire time immediately after the current scheduling point is accepted for processing.

This preserves the existing design where the scene moves forward as part of scheduling.

### Step 6. Create scheduled task

Call `taskService.createScheduledTask(sceneId, triggerReason)` as today.

If task creation succeeds:

- update `schedule_event.status = TASK_CREATED`
- set `schedule_event.task_id`
- update `scene_schedule_state.last_triggered_at`
- update `scene_schedule_state.last_task_id`

If task creation fails:

- update `schedule_event.status = FAILED`
- store a concise error message
- keep the event row for diagnosis

## Persistence Strategy

### Atomic claim mapper contract

The mapper layer must expose explicit methods for:

- insert-if-absent initial schedule state
- conditional update for claim
- update trigger result fields after task creation

The application service should rely on affected-row counts instead of assuming success after a read.

### Schedule event mapper contract

The mapper layer must expose:

- insert claimed event
- update event to task-created
- update event to failed
- query helpers needed by tests

## Error Handling

### Claim failure

This is not an application error. It means another instance already owns or completed that scheduling point. Log at debug or info level and continue.

### Unique-key conflict on `schedule_event`

This is also not an application error. It is an idempotency signal and should be handled as a no-op for that scene and fire point.

### Task creation failure

This is a real scheduling failure for that event. The event row must remain with `FAILED` state and an error message. Existing task creation exceptions should still surface through logs and diagnostics.

## Testing Strategy

This change must be implemented test-first.

### Unit/service tests

Add tests covering:

- same scene and same `plannedFireAt` cannot be claimed twice
- different scenes with same `plannedFireAt` can both succeed
- duplicate `schedule_event` insertion is treated as idempotent
- successful scheduled task creation transitions event to `TASK_CREATED`
- failed scheduled task creation transitions event to `FAILED`

### Mapper/integration tests

Add mapper tests for:

- conditional claim update semantics
- unique key enforcement on `(scene_id, planned_fire_at)`

### Regression tests

Retain and adjust existing scheduler tests so current behavior is preserved:

- due scenes are still discovered correctly
- `next_run_at` still advances
- active-task guard still blocks duplicate active tasks per scene

## Observability

Add structured logs that include:

- `sceneId`
- `plannedFireAt`
- `leaseOwner`
- `scheduleEventId` when available

This will make it possible to diagnose:

- which instance claimed a scheduling point
- whether the event was deduplicated
- whether task creation succeeded or failed

## Rollout and Migration

### Database migration

Add a Flyway migration for the `schedule_event` table and constraints.

### Backward compatibility

No existing table or API contract needs to be removed. The change is additive plus behavior tightening in scheduling logic.

### Operational note

This phase improves correctness under multi-instance deployment, but it does not yet support:

- lease renewal
- stale-claim recovery workflows
- scheduler-to-worker queueing

Those belong to the next phase if the platform evolves further toward a dedicated distributed scheduling system.

## Implementation Summary

Modify the current design along these lines:

- keep `@Scheduled`
- keep `findDueScheduledScenes`
- keep `createScheduledTask`
- harden `SceneScheduleLeaseServiceImpl` with atomic claim logic
- add `schedule_event` as the scheduling idempotency ledger

This gives the current project the strongest improvement-to-change ratio: it meaningfully upgrades multi-instance scheduling safety without forcing a queueing architecture into a codebase that is not yet shaped for it.
