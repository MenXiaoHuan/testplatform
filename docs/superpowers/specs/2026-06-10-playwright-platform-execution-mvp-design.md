# Playwright Platform Execution MVP Design

## Background

The current repository already contains a usable baseline:

- `playwright_framework` can execute Playwright tests and emit platform-oriented artifacts.
- `playwright-platform-server` can manage repositories, scenes, tasks, reports, artifacts, and parse Playwright raw results.
- `playwright-platform-web` can manage repositories, scenes, tasks, and show task detail pages.

However, the product shape is still too close to a generic CRUD admin:

- repository deletion is not aligned with the data model and fails on foreign-key usage,
- task navigation is too global and not centered around scene execution,
- scene is under-modeled for a real execution platform,
- UI structure does not yet communicate the actual workflow of "configure scene -> run scene -> inspect task history",
- the MVP execution mode and future evolution path are not documented as a coherent platform design.

The user wants a **real Playwright execution platform**, not a template manager or static demo UI.

## Product Goal

Build a **single-machine executable MVP** where users can:

1. connect a test repository,
2. define executable scenes,
3. trigger a scene and run real Playwright tests on the host machine,
4. retain report, artifacts, and case results,
5. review execution history under each scene,
6. prepare for later upgrades to scheduler, isolated runner, and distributed execution.

## Non-Goals

The MVP does **not** aim to provide:

- distributed runners,
- multi-tenant permission system,
- complex analytics dashboards,
- multi-machine scheduling pools,
- full-featured notification center,
- advanced enterprise audit/compliance workflows.

These should still be designed as future phases so the MVP does not block later evolution.

## Core Product Principles

### 1. Scene is the primary execution unit

Repository is a base resource, but scene is the object users operate on most often.

A scene represents:

- what to run,
- from which branch/repository,
- with which execution command,
- with which environment variables,
- with which schedule.

Tasks are execution history of a scene.

### 2. MVP must execute real tests

This platform is not a static orchestration UI. The backend must support actual local execution:

- prepare workspace,
- fetch repository,
- install dependencies,
- run Playwright command,
- parse results,
- archive report and artifacts.

### 3. Data lifecycle must be consistent

Deletion should follow business relationships, not raw UI convenience.

If repository, scene, or task is deleted, the platform must clean up dependent data:

- relational records,
- artifact metadata,
- case results,
- object storage data where applicable.

### 4. UI should reflect execution workflow

The frontend should guide users through:

- repository setup,
- scene configuration,
- scene execution,
- task inspection,
- result review.

The UI must look modern and productized, but it should prioritize clarity and execution flow over decorative complexity.

## MVP Architecture

### Execution mode

MVP uses **single-machine execution**.

`playwright-platform-server` acts as:

- management API,
- execution orchestrator,
- local runner coordinator.

Execution happens on the same machine where the backend is running.

### Core chain

The scene run flow is:

1. user triggers scene run,
2. backend creates a task record,
3. backend prepares workspace,
4. backend fetches repository code,
5. backend switches to target branch,
6. backend installs dependencies,
7. backend runs the configured Playwright command,
8. backend archives HTML report,
9. backend parses Playwright raw result index,
10. backend persists case results,
11. backend archives artifacts and binds them to cases,
12. frontend reads task details and scene-scoped task history.

## Domain Model

### Repository

Repository remains a base integration object.

Suggested responsibility:

- Git source location,
- default branch,
- package manager,
- install command,
- report path,
- results index path,
- artifact root path,
- base runtime conventions.

Repository should no longer be the main user workflow entrypoint.

### Scene

Scene becomes the main platform object.

Scene should represent a reusable execution definition.

Required MVP fields:

- id
- repoId
- name
- description
- branch
- selectorType
- selectorValue
- runCommand
- envJson
- enabled
- scheduleEnabled
- cronExpression
- lastRunAt
- lastTaskStatus
- createdAt
- updatedAt

Optional but useful MVP-compatible fields:

- timeoutSeconds
- retryCount
- nodeVersionOverride
- projectName
- browser

### Task

Task is execution history of a scene.

Task should include:

- id
- sceneId
- repoId
- status
- triggerType
- triggerUser
- branch
- commitSha
- startedAt
- finishedAt
- durationMs
- runnerName
- reportUrl
- logUrl
- createdAt
- updatedAt

### CaseResult / Artifact

These remain execution outputs under a task.

They should continue supporting:

- task-level attachment listing,
- case-level attachment listing,
- signed report and artifact URLs,
- raw Playwright result binding.

## Deletion Strategy

### Business-level cascading deletion

The MVP should use **explicit service-level cascading deletion**, not rely solely on database-level cascade.

Reason:

- object storage cleanup must happen together with database cleanup,
- deletion order must be controlled,
- business errors should stay readable,
- later migration to soft delete or retention policy becomes easier.

### Repository deletion

Deleting a repository should:

1. find all scenes under the repository,
2. delete every scene under it,
3. delete all tasks under those scenes,
4. delete case results under those tasks,
5. delete artifact metadata under those tasks,
6. delete object storage objects referenced by those artifacts/reports,
7. delete scene records,
8. finally delete repository record.

### Scene deletion

Deleting a scene should:

1. delete all tasks for the scene,
2. delete all case results under those tasks,
3. delete all artifacts under those tasks,
4. delete related object storage objects,
5. delete the scene itself.

### Task deletion

Deleting a task should:

1. delete case results,
2. delete artifacts,
3. delete report/artifact objects,
4. delete task record.

### Failure policy

If object storage cleanup partially fails:

- the platform should not silently succeed,
- error should be surfaced,
- deletion should be wrapped transactionally where possible for database operations,
- object cleanup should be attempted deterministically before final entity removal or recorded for retry.

MVP can accept a simpler implementation:

- database cleanup in transaction,
- object deletion attempted in deterministic order,
- failure returns readable error and prevents partial silent loss.

## Backend API Design

### Repository APIs

Keep repository CRUD, but deletion semantics change to business cascade.

Potential additions:

- `GET /api/repos/{id}/scenes/count`
- or summary fields in repository list to show how many scenes depend on it

These are optional for MVP.

### Scene APIs

Scene becomes the primary operational surface.

MVP should support:

- create scene,
- update scene,
- delete scene with cascade,
- list scenes ordered by latest create/update,
- run scene,
- list scene tasks,
- optional enable/disable toggle,
- optional schedule toggle/update.

Recommended additions:

- `GET /api/scenes/{sceneId}/tasks`
- `GET /api/scenes?sort=createdAt,desc`

### Task APIs

Current task APIs remain useful, but scene-scoped task access should be first-class.

The task list returned under a scene must sort by latest first:

- primary choice: `createdAt DESC`
- acceptable MVP fallback: `id DESC`

### Schedule APIs

For MVP, schedule should be part of scene configuration.

Recommended initial design:

- `scheduleEnabled`
- `cronExpression`
- `lastScheduledAt`
- `nextScheduledAt`

If implementation complexity is too high for the first coding batch, the fields may be landed first and the actual scheduler can follow immediately after the core scene/task restructuring.

## Frontend Information Architecture

### Navigation

Recommended MVP navigation:

- Repository Management
- Scene Center
- Task Center (secondary entry)

Main user flow should begin in Scene Center, not Task Center.

### Repository page

Repository page remains a base resource management page.

It should:

- create repository,
- edit repository,
- delete repository,
- show whether repository is enabled,
- support reuse by multiple scenes.

It should not act as the main execution dashboard.

### Scene Center

Scene Center becomes the platform homepage.

It should present scenes as cards.

Each card should show:

- scene name,
- description,
- repository name,
- branch,
- environment variable count,
- schedule state,
- latest task status,
- latest execution time.

Each card should provide:

- run now,
- view tasks,
- edit,
- delete.

### Scene configuration page

Scene create/edit should be a dedicated page or focused form.

Recommended MVP sections:

- basic info
- repository and branch
- execution config
- environment variables
- schedule config

Execution config should support at least:

- selector type
- selector value
- run command
- optional timeout

### Scene task list page

Task history should be scene-scoped by default.

Recommended route:

- `/scenes/:id/tasks`

This page should show:

- scene summary,
- task list ordered by newest first,
- status filter (optional MVP),
- task detail entry.

### Task detail page

Task detail remains useful and should continue to show:

- task metadata,
- report access,
- attachment list,
- case results,
- case attachments.

Case result display can remain incremental in MVP, but task detail should remain the execution inspection endpoint.

## UI Design Direction

The platform should reference the supplied inspiration only at the level of:

- scene-as-card mental model,
- execution-first workflow,
- configuration grouped by capability.

It should **not** clone the source layout or visual details.

### MVP visual principles

- modern and clean
- light background
- strong use of cards
- clear status badges
- consistent spacing
- restrained shadows and borders
- obvious primary actions

### Avoid in MVP

- overly dense control bars,
- copy-pasted enterprise-console layout,
- too many filters before the core flow is stable,
- decorative complexity without execution value.

## Scheduling Design

### MVP target

The design should already include real scheduling capability.

Scheduler behavior:

- scenes can opt into scheduled execution,
- backend periodically checks scene schedules,
- matching scenes trigger new tasks automatically.

### MVP-safe scope

The scheduler can be simple:

- single-node scheduler in backend process,
- cron-based trigger,
- no distributed lock,
- no priority queue,
- no worker leasing.

### Required scheduler safeguards

- do not trigger disabled scenes,
- do not trigger scenes with invalid cron expressions,
- avoid duplicate overlapping execution for the same scene if configured,
- log schedule trigger source as scheduled execution.

## Sorting and Query Behavior

Task ordering for scene history must be deterministic and newest-first.

Preferred order:

1. `createdAt DESC`
2. `id DESC` as tie-breaker

Scene Center may also surface:

- newest updated scenes first,
- optional status grouping later.

## Migration and Compatibility

The design should preserve compatibility with the existing execution chain where practical:

- existing Playwright platform mode remains valid,
- existing case result parsing remains valid,
- existing report and artifact archiving remains valid,
- task detail APIs should be reused rather than replaced unnecessarily.

The major structural change is product-level:

- scene becomes the primary execution entrypoint,
- deletion semantics become lifecycle-aware,
- scheduling becomes part of scene definition.

## Future Evolution Roadmap

### Phase 1: Single-machine execution MVP

- repository base config
- scene-centered workflow
- scene card homepage
- scene-scoped task history
- task detail with report/artifact/case outputs
- business-level cascading deletion
- minimal real scheduler

### Phase 2: Platform enhancement

- richer scene filters and tags
- scene duplication/template support
- retry strategies
- task filters and bulk operations
- richer case result presentation
- notification integrations
- better analytics summaries

### Phase 3: Architecture upgrade

- backend API split from execution runner
- independent runner service
- task queue
- runner heartbeat
- execution leasing
- isolated execution environments

### Phase 4: Distributed execution platform

- multi-runner scheduling
- execution pools by environment/platform
- concurrency controls
- distributed locking
- scheduling service separation
- higher reliability and capacity planning

### Phase 5: Enterprise features

- auth and RBAC
- audit log
- team/project spaces
- quotas and retention policies
- soft delete and recovery
- approval workflows

## Risks

- real scheduler increases backend complexity earlier than a pure CRUD MVP,
- business-level cascade requires careful ordering and object storage cleanup,
- scene-centered UI restructure touches multiple pages and routes at once,
- single-machine execution can hide future distributed constraints if interfaces are not documented well now.

These risks are acceptable because the current product problem is not lack of abstraction; it is lack of a coherent execution-platform shape.

## Acceptance Criteria

The MVP design is considered satisfied when implementation can demonstrate:

1. a repository can be created and referenced by one or more scenes,
2. a scene can be created with execution config and environment variables,
3. a scene can be run and produce a task,
4. task history is viewable from the scene context,
5. task detail shows report, attachments, and parsed case results,
6. repository/scene deletion performs lifecycle-aware cascade cleanup,
7. task order is newest-first,
8. scene UI acts as the main execution dashboard,
9. the design leaves a clean path to independent runners and distributed scheduling later.
