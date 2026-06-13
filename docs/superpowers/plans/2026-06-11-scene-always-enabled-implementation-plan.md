# Scene Always Enabled Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the scene enabled concept from the platform so scenes are always active, the scene list shows only a run icon in `执行控制`, and run availability depends only on repository status.

**Architecture:** Update frontend scene types, store payloads, and the scene list UI to stop reading or writing `enabled`. In the backend, remove `enabled` from the scene entity, service logic, controller payloads, and scheduler filters, then drop the `scene.enabled` column with a Flyway migration.

**Tech Stack:** Vue 3, TypeScript, Vitest, Spring Boot, JPA/Hibernate, Flyway, JUnit 5, Mockito

---

### Task 1: Lock Frontend Scene Semantics With Failing Tests

**Files:**
- Modify: `playwright-platform-web/tests/unit/views/scene/SceneListView.test.ts`
- Modify: `playwright-platform-web/tests/unit/stores/scene.test.ts`
- Test: `playwright-platform-web/tests/unit/views/scene/SceneListView.test.ts`
- Test: `playwright-platform-web/tests/unit/stores/scene.test.ts`

- [ ] **Step 1: Write the failing scene list test**

```ts
it('shows only the run icon in 执行控制 and no scene toggle', async () => {
  const wrapper = mountView()

  await wrapper.vm.$nextTick()

  expect(wrapper.text()).toContain('执行控制')
  expect(wrapper.text()).not.toContain('On/Off')
  expect(wrapper.findAll('input[type="checkbox"]')).toHaveLength(0)

  const runButton = wrapper.findAll('.button-stub').find((item) => item.attributes('aria-label') === '开始执行')
  expect(runButton).toBeTruthy()
})

it('disables the run icon when the repository is disabled', async () => {
  repositoryStoreState.items = [{ ...repositoryStoreState.items[0], enabled: false }]
  const wrapper = mountView()

  await wrapper.vm.$nextTick()

  const runButton = wrapper.findAll('.button-stub').find((item) => item.attributes('aria-label') === '开始执行')
  expect(runButton?.attributes('data-disabled')).toBe('true')
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- tests/unit/views/scene/SceneListView.test.ts`
Expected: FAIL because the scene list still renders the switch or still references `enabled` state.

- [ ] **Step 3: Write the failing store test**

```ts
it('creates a scene form without enabled state', () => {
  const form = createSceneForm()

  expect(form).not.toHaveProperty('enabled')
})

it('saves a scene without sending enabled', async () => {
  const payload = store.createEmptyForm()

  await store.save(null, payload)

  expect(apiCreate).toHaveBeenCalledWith(expect.not.objectContaining({
    enabled: expect.anything(),
  }))
})
```

- [ ] **Step 4: Run the store test to verify it fails**

Run: `npm test -- tests/unit/stores/scene.test.ts`
Expected: FAIL because the scene form or save payload still includes `enabled`.

- [ ] **Step 5: Commit**

```bash
git add playwright-platform-web/tests/unit/views/scene/SceneListView.test.ts playwright-platform-web/tests/unit/stores/scene.test.ts
git commit -m "test: lock scene always-enabled frontend behavior"
```

### Task 2: Remove Frontend Scene Enabled State And Switch UI

**Files:**
- Modify: `playwright-platform-web/src/types/scene.ts`
- Modify: `playwright-platform-web/src/stores/scene.ts`
- Modify: `playwright-platform-web/src/views/scene/SceneListView.vue`
- Test: `playwright-platform-web/tests/unit/views/scene/SceneListView.test.ts`
- Test: `playwright-platform-web/tests/unit/stores/scene.test.ts`

- [ ] **Step 1: Implement the type changes**

```ts
export interface SceneRecord {
  id: number
  repoId: number
  name: string
  description: string
  branch: string
  scheduleEnabled: boolean
  cronExpression: string
  lastTaskStatus: string | null
  lastRunAt: string | null
  environmentVariableCount: number
}

export interface SceneForm {
  repoId: number
  name: string
  description: string
  matchValue: string
  projectName?: string
  browser?: string
  envJson?: string
  scheduleEnabled: boolean
  cronExpression: string
  branch?: string
  testSelectorType?: string
  testSelectorValue?: string
  runCommand?: string
}
```

- [ ] **Step 2: Remove `enabled` from the default scene form**

```ts
export const createSceneForm = (): SceneForm => ({
  repoId: 0,
  name: '',
  description: '',
  matchValue: '',
  projectName: 'chromium',
  browser: 'chromium',
  envJson: '',
  scheduleEnabled: false,
  cronExpression: '',
  branch: 'main',
  testSelectorType: 'file',
  testSelectorValue: '',
  runCommand: 'node ./scripts/run-e2e.cjs',
})
```

- [ ] **Step 3: Remove switch logic and keep only run icon plus text actions**

```vue
<el-table-column label="执行控制" width="72">
  <template #default="{ row }">
    <el-button
      class="table-action-icon-button"
      link
      type="primary"
      aria-label="开始执行"
      :title="canRun(row) ? '开始执行' : '所属仓库已停用，无法执行'"
      :loading="runningId === row.id"
      :disabled="!canRun(row)"
      @click="run(row)"
    >
      <span class="play-icon" aria-hidden="true" />
    </el-button>
  </template>
</el-table-column>
```

- [ ] **Step 4: Delete toggle state and save payload references**

```ts
const runningId = ref<number | null>(null)

function canRun(row: SceneRecord) {
  const repository = selectedRepository(row.repoId)
  return repository !== null && repository.enabled
}

await sceneStore.save(editingId.value, {
  ...form,
  envJson: normalizedEnvJson ? normalizedEnvJson : undefined,
  matchValue: normalizedMatchValue,
  branch: repository.defaultBranch,
  testSelectorType: 'file',
  testSelectorValue: normalizedMatchValue,
  projectName: form.browser?.trim() || 'chromium',
  runCommand: repository.runCommandTemplate,
})
```

- [ ] **Step 5: Run the frontend tests to verify they pass**

Run: `npm test -- tests/unit/views/scene/SceneListView.test.ts tests/unit/stores/scene.test.ts`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add playwright-platform-web/src/types/scene.ts playwright-platform-web/src/stores/scene.ts playwright-platform-web/src/views/scene/SceneListView.vue playwright-platform-web/tests/unit/views/scene/SceneListView.test.ts playwright-platform-web/tests/unit/stores/scene.test.ts
git commit -m "feat: remove scene enabled state from frontend"
```

### Task 3: Lock Backend Scene Model Removal With Failing Tests

**Files:**
- Modify: `playwright-platform-server/src/test/java/com/example/platform/scene/SceneServiceImplTest.java`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/scene/SceneControllerTest.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/scene/SceneServiceImplTest.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/scene/SceneControllerTest.java`

- [ ] **Step 1: Write the failing service test**

```java
@Test
void shouldTriggerScheduledScenesWithoutEnabledFilter() {
    SceneJpaRepository repository = Mockito.mock(SceneJpaRepository.class);
    TestRepositoryJpaRepository repositoryJpaRepository = Mockito.mock(TestRepositoryJpaRepository.class);
    SceneServiceImpl service = new SceneServiceImpl(repository, repositoryJpaRepository, new ObjectMapper());

    SceneEntity scheduled = new SceneEntity();
    scheduled.setId(11L);
    scheduled.setScheduleEnabled(true);
    scheduled.setCronExpression("0 0 2 * * ?");

    Mockito.when(repository.findAllByScheduleEnabledTrue()).thenReturn(List.of(scheduled));

    service.triggerScheduledScenes();

    Mockito.verify(repository).findAllByScheduleEnabledTrue();
}
```

- [ ] **Step 2: Run the service test to verify it fails**

Run: `mvn -Dtest=SceneServiceImplTest test`
Expected: FAIL because the repository method or service still depends on `enabled`.

- [ ] **Step 3: Write the failing controller assertion**

```java
mockMvc.perform(get("/api/scenes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].enabled").doesNotExist());
```

- [ ] **Step 4: Run the controller test to verify it fails**

Run: `mvn -Dtest=SceneControllerTest test`
Expected: FAIL because scene JSON still exposes `enabled`.

- [ ] **Step 5: Commit**

```bash
git add playwright-platform-server/src/test/java/com/example/platform/scene/SceneServiceImplTest.java playwright-platform-server/src/test/java/com/example/platform/scene/SceneControllerTest.java
git commit -m "test: lock scene always-enabled backend behavior"
```

### Task 4: Remove Backend Scene Enabled Field And Scheduler Filter

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/model/SceneEntity.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/model/SceneJpaRepository.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneServiceImpl.java`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/scene/SceneServiceImplTest.java`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/scene/SceneControllerTest.java`

- [ ] **Step 1: Remove `enabled` from `SceneEntity`**

```java
@Column(name = "schedule_enabled", nullable = false, columnDefinition = "tinyint(1)")
private Boolean scheduleEnabled = false;
```

Remove:

```java
@Column(nullable = false, columnDefinition = "tinyint(1)")
private Boolean enabled = true;
```

- [ ] **Step 2: Replace repository query with schedule-only scan**

```java
List<SceneEntity> findAllByScheduleEnabledTrue();
```

- [ ] **Step 3: Remove `enabled` reads and writes from service methods**

```java
existing.setRepoId(entity.getRepoId());
existing.setName(entity.getName());
existing.setDescription(entity.getDescription());
existing.setBranch(entity.getBranch());
existing.setTestSelectorType(entity.getTestSelectorType());
existing.setTestSelectorValue(entity.getTestSelectorValue());
existing.setProjectName(entity.getProjectName());
existing.setBrowser(entity.getBrowser());
existing.setEnvJson(entity.getEnvJson());
existing.setRunCommand(entity.getRunCommand());
existing.setScheduleEnabled(entity.getScheduleEnabled());
existing.setCronExpression(entity.getCronExpression());
```

- [ ] **Step 4: Update scheduler implementation**

```java
public void triggerScheduledScenes() {
    List<SceneEntity> scenes = repository.findAllByScheduleEnabledTrue();
    scenes.forEach(scene -> log.info("Scheduling hook scanned scene id={}, cron={}", scene.getId(), scene.getCronExpression()));
}
```

- [ ] **Step 5: Run the backend tests to verify they pass**

Run: `mvn -Dtest=SceneServiceImplTest,SceneControllerTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add playwright-platform-server/src/main/java/com/example/platform/scene/model/SceneEntity.java playwright-platform-server/src/main/java/com/example/platform/scene/model/SceneJpaRepository.java playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneServiceImpl.java playwright-platform-server/src/test/java/com/example/platform/scene/SceneServiceImplTest.java playwright-platform-server/src/test/java/com/example/platform/scene/SceneControllerTest.java
git commit -m "feat: remove scene enabled state from backend"
```

### Task 5: Drop The Database Column And Sync Fixtures

**Files:**
- Create: `playwright-platform-server/src/main/resources/db/migration/V7__drop_scene_enabled.sql`
- Modify: `playwright-platform-server/src/test/resources/sql/*.sql`
- Test: `playwright-platform-server/src/test/java/com/example/platform/scene/SceneControllerTest.java`

- [ ] **Step 1: Add the Flyway migration**

```sql
ALTER TABLE scene
    DROP COLUMN enabled;
```

- [ ] **Step 2: Remove `enabled` from SQL fixtures**

```sql
INSERT INTO scene (
    id, repo_id, name, description, branch, test_selector_type, test_selector_value,
    project_name, browser, env_json, run_command, schedule_enabled, cron_expression
) VALUES (
    11, 7, 'login', '', 'main', 'file', 'tests/login.spec.ts',
    'chromium', 'chromium', NULL, 'npx playwright test', 0, NULL
);
```

- [ ] **Step 3: Run the controller test against the migrated schema**

Run: `mvn -Dtest=SceneControllerTest test`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add playwright-platform-server/src/main/resources/db/migration/V7__drop_scene_enabled.sql playwright-platform-server/src/test/resources/sql
git commit -m "db: drop scene enabled column"
```

### Task 6: Full Verification And Manual UI Check

**Files:**
- Modify: `README.md`
- Test: `playwright-platform-web/tests/unit/views/scene/SceneListView.test.ts`
- Test: `playwright-platform-server/src/test/java/com/example/platform/scene/SceneServiceImplTest.java`
- Test: `playwright-platform-server/src/test/java/com/example/platform/scene/SceneControllerTest.java`

- [ ] **Step 1: Update the product-facing description**

```md
- 场景默认始终可管理，不提供启停开关
- 场景列表中的执行图标仅在所属仓库启用时可点击
```

- [ ] **Step 2: Run frontend verification**

Run: `npm test -- tests/unit/views/scene/SceneListView.test.ts`
Expected: PASS

- [ ] **Step 3: Run backend verification**

Run: `mvn -Dtest=SceneServiceImplTest,SceneControllerTest test`
Expected: PASS

- [ ] **Step 4: Run final diagnostics check**

Run VS Code diagnostics for:

- `playwright-platform-web/src/views/scene/SceneListView.vue`
- `playwright-platform-web/src/types/scene.ts`
- `playwright-platform-server/src/main/java/com/example/platform/scene/model/SceneEntity.java`
- `playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneServiceImpl.java`

Expected: no new diagnostics from this change set.

- [ ] **Step 5: Commit**

```bash
git add README.md playwright-platform-web/src/views/scene/SceneListView.vue playwright-platform-web/src/types/scene.ts playwright-platform-server/src/main/java/com/example/platform/scene/model/SceneEntity.java playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneServiceImpl.java
git commit -m "docs: describe scene always-enabled behavior"
```

---

## Self-Review

- Spec coverage: frontend list, run icon disable rule, type cleanup, backend entity/service/controller cleanup, scheduler filter change, and migration are all mapped to Tasks 1-6.
- Placeholder scan: no `TODO`, `TBD`, or “similar to previous task” placeholders remain.
- Type consistency: the plan consistently removes `enabled` from `SceneRecord`, `SceneDetail`, `SceneForm`, `SceneEntity`, and schedule filtering.
