# Root Workspace Onboarding Design

## Background

`/Users/bytedance/test_platform` has been consolidated into a single Git repository that contains three working areas:

- `playwright_framework`: Playwright test framework and platform-facing test artifacts contract.
- `playwright-platform-server`: Spring Boot backend for repository, scene, task, report, and artifact management.
- `playwright-platform-web`: Vue frontend for platform management and task detail views.

The repository already supports local development, but there is no root-level onboarding guide or unified command entrypoint. Developers currently need to know each subproject's directory and start command in advance.

## Goal

Add a lightweight root-level onboarding layer so a developer can open the repository root and immediately understand:

- what each subproject does,
- which dependencies are required,
- which command starts each service,
- where Git operations should now happen,
- how the framework, backend, and frontend relate to each other.

## Non-Goals

- Do not refactor subproject build systems.
- Do not introduce a monorepo package manager or workspace orchestration.
- Do not change existing backend, frontend, or framework commands.
- Do not implement process supervision for starting multiple long-running services in one shell.

## Proposed Approach

### Option A: Root README plus thin shell wrappers

Create a root `README.md` and a small `scripts/` directory containing wrappers for existing commands.

This is the recommended approach because it improves discoverability without changing runtime behavior.

### Option B: Root README only

Document the commands but do not add scripts.

This is simpler, but developers still need to remember and manually type subproject-specific commands.

### Option C: Full root orchestration layer

Add a root `package.json` or more advanced task runner to coordinate frontend, backend, and framework commands.

This is more powerful, but it adds new repository-wide conventions and is unnecessary for the current scope.

## Selected Design

Use **Option A**.

The root repository will gain:

- `README.md` for structure, prerequisites, startup order, and common commands.
- `scripts/dev-server.sh` to start `playwright-platform-server`.
- `scripts/dev-web.sh` to start `playwright-platform-web`.
- `scripts/test-server.sh` to run backend tests.
- `scripts/test-web.sh` to run frontend tests.

The framework remains independently operated from `playwright_framework`, but the root README will document how it fits into the platform workflow.

## File-Level Design

### Root README

The root `README.md` will include:

- repository overview,
- directory map,
- environment prerequisites,
- recommended startup order,
- common development commands,
- note that Git operations now happen at `/Users/bytedance/test_platform`,
- note that legacy nested Git metadata has been backed up under `.tmp/git-boundary-backups/`.

### Root scripts

Each shell script will:

- resolve the repository root relative to the script location,
- `cd` into the correct subproject,
- execute the existing project command,
- avoid changing command semantics or environment defaults.

The initial script set is intentionally small and focused on the commands most likely to be reused during local development.

## Behavior Details

### Backend startup

`scripts/dev-server.sh` will run the existing backend startup command from `playwright-platform-server`.

### Frontend startup

`scripts/dev-web.sh` will run the existing frontend dev server command from `playwright-platform-web`.

### Backend tests

`scripts/test-server.sh` will execute the current Maven test command from `playwright-platform-server`.

### Frontend tests

`scripts/test-web.sh` will execute the current npm test command from `playwright-platform-web`.

### Framework usage

No root wrapper will be added yet for `playwright_framework`. The README will instead document that framework execution continues through its existing `package.json` scripts until there is a clearer need for a root alias.

## Error Handling

- Scripts should fail fast if the target directory is missing.
- Scripts should rely on the underlying command exit code so CI and local usage behave predictably.
- README should document that backend and frontend are still independently startable if a wrapper is not suitable.

## Validation

Validation for this change is lightweight:

- confirm the new files are present,
- run each wrapper once to verify it delegates correctly,
- ensure the root README matches the real command set already in each subproject,
- verify the repository stays clean after the documentation and script update.

## Risks

- Commands may drift in the future if subproject scripts change without updating the root README.
- Shell wrappers are platform-oriented and primarily target the current macOS/Linux-style environment.

These risks are acceptable because the wrappers are intentionally minimal and easy to update.
