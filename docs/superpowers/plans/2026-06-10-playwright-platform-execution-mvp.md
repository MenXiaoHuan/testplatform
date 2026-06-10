# Playwright Platform Execution MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reshape the current Playwright platform into a scene-centered, single-machine executable MVP with business-level cascading deletion, scene-scoped task history, newest-first task ordering, and a modernized frontend workflow.

**Architecture:** Keep the current single-process backend execution chain, but elevate scene into the primary execution entity and explicitly manage data lifecycle in application services. On the frontend, shift the main workflow from table-based CRUD toward scene cards, scene-scoped task history, and cleaner task inspection while preserving the existing task execution and artifact/report capabilities.

**Tech Stack:** Spring Boot 3, Spring Data JPA, Flyway, MySQL, MinIO, Vue 3, TypeScript, Vite, Element Plus, Vitest

---

## File Map

- Create: `/Users/bytedance/test_platform/playwright-platform-server/src/main/resources/db/migration/V3__scene_execution_mvp.sql`
- Create: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryCascadeDeleteService.java`
- Create: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryCascadeDeleteServiceImpl.java`
- Create: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/scene/dto/SceneCardResponse.java`
- Create: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/task/dto/SceneTaskListResponse.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/scene/model/SceneEntity.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/scene/model/SceneJpaRepository.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneService.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneServiceImpl.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/scene/controller/SceneController.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryService.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryServiceImpl.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/task/model/TaskEntity.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/task/model/TaskJpaRepository.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/task/service/TaskService.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/task/controller/TaskController.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/storage/service/ObjectStorageService.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/storage/service/MinioObjectStorageService.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/common/GlobalExceptionHandler.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/test/java/com/example/platform/repository/RepositoryControllerTest.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/test/java/com/example/platform/scene/SceneControllerTest.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/test/java/com/example/platform/task/TaskControllerTest.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/test/java/com/example/platform/task/TaskExecutionServiceTest.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/router/index.ts`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/api/scene.ts`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/api/task.ts`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/stores/scene.ts`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/stores/task.ts`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/types/scene.ts`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/types/task.ts`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/views/scene/SceneListView.vue`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/views/task/TaskListView.vue`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/views/task/TaskDetailView.vue`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/style.css`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/views/task/TaskDetailView.test.ts`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/router/index.test.ts`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/stores/scene.test.ts`

### Task 1: Extend Scene and Task Domain for the MVP Workflow

**Files:**
- Create: `/Users/bytedance/test_platform/playwright-platform-server/src/main/resources/db/migration/V3__scene_execution_mvp.sql`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/scene/model/SceneEntity.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/task/model/TaskEntity.java`
- Test: `/Users/bytedance/test_platform/playwright-platform-server/src/test/java/com/example/platform/config/EntitySchemaMappingTest.java`

- [ ] **Step 1: Write the failing schema-mapping test for new scene/task fields**

```java
@Test
void shouldExposeSceneExecutionMvpFields() {
    SceneEntity scene = new SceneEntity();
    scene.setDescription("nightly smoke");
    scene.setScheduleEnabled(true);
    scene.setCronExpression("0 0/30 * * * ?");
    scene.setLastTaskStatus("SUCCESS");

    TaskEntity task = new TaskEntity();
    task.setCreatedAt(LocalDateTime.now());

    assertThat(scene.getDescription()).isEqualTo("nightly smoke");
    assertThat(scene.getScheduleEnabled()).isTrue();
    assertThat(scene.getCronExpression()).isEqualTo("0 0/30 * * * ?");
    assertThat(scene.getLastTaskStatus()).isEqualTo("SUCCESS");
    assertThat(task.getCreatedAt()).isNotNull();
}
```

- [ ] **Step 2: Run the failing backend test**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=EntitySchemaMappingTest test`
Expected: FAIL because the new getters/setters or mapped fields do not yet exist.

- [ ] **Step 3: Add the Flyway migration for scene execution fields**

```sql
alter table scene
    add column description varchar(512) null after name,
    add column schedule_enabled tinyint not null default 0 after enabled,
    add column cron_expression varchar(64) null after schedule_enabled,
    add column last_run_at datetime null after cron_expression,
    add column last_task_status varchar(32) null after last_run_at;
```

- [ ] **Step 4: Add the new JPA fields to `SceneEntity`**

```java
    @Column(length = 512)
    private String description;

    @Column(name = "schedule_enabled", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean scheduleEnabled = false;

    @Column(name = "cron_expression", length = 64)
    private String cronExpression;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "last_task_status", length = 32)
    private String lastTaskStatus;
```

- [ ] **Step 5: Add accessors for the new scene fields**

```java
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getScheduleEnabled() { return scheduleEnabled; }
    public void setScheduleEnabled(Boolean scheduleEnabled) { this.scheduleEnabled = scheduleEnabled; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public String getLastTaskStatus() { return lastTaskStatus; }
    public void setLastTaskStatus(String lastTaskStatus) { this.lastTaskStatus = lastTaskStatus; }
```

- [ ] **Step 6: Make `TaskEntity` explicitly expose create/update timestamps**

```java
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
```

- [ ] **Step 7: Re-run the focused schema test**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=EntitySchemaMappingTest test`
Expected: PASS

- [ ] **Step 8: Commit the domain model changes**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-server/src/main/resources/db/migration/V3__scene_execution_mvp.sql \
  playwright-platform-server/src/main/java/com/example/platform/scene/model/SceneEntity.java \
  playwright-platform-server/src/main/java/com/example/platform/task/model/TaskEntity.java \
  playwright-platform-server/src/test/java/com/example/platform/config/EntitySchemaMappingTest.java
git commit -m "feat: extend scene execution mvp model"
```

### Task 2: Implement Business-Level Cascading Deletion

**Files:**
- Create: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryCascadeDeleteService.java`
- Create: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryCascadeDeleteServiceImpl.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryService.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryServiceImpl.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneService.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneServiceImpl.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/task/model/TaskJpaRepository.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/task/model/ArtifactJpaRepository.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/task/model/CaseResultJpaRepository.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/storage/service/ObjectStorageService.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/storage/service/MinioObjectStorageService.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/common/GlobalExceptionHandler.java`
- Test: `/Users/bytedance/test_platform/playwright-platform-server/src/test/java/com/example/platform/repository/RepositoryControllerTest.java`

- [ ] **Step 1: Write the failing repository delete test that expects cascade success**

```java
@Test
void shouldDeleteRepositoryWithRelatedScenesTasksAndArtifacts() throws Exception {
    Mockito.doNothing().when(repositoryService).delete(1L);

    mockMvc.perform(delete("/api/repos/1"))
            .andExpect(status().isNoContent());

    Mockito.verify(repositoryService).delete(1L);
}
```

- [ ] **Step 2: Write the failing service-level cascade test**

```java
@Test
void shouldCascadeDeleteRepositoryLifecycleData() {
    repositoryCascadeDeleteService.deleteRepositoryGraph(1L);

    Mockito.verify(sceneRepository).deleteAllByRepoId(1L);
    Mockito.verify(taskRepository).deleteAllByRepoId(1L);
    Mockito.verify(caseResultRepository).deleteAllByTaskIdIn(Mockito.anyList());
    Mockito.verify(artifactRepository).deleteAllByTaskIdIn(Mockito.anyList());
    Mockito.verify(testRepositoryJpaRepository).deleteById(1L);
}
```

- [ ] **Step 3: Run the failing repository tests**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=RepositoryControllerTest test`
Expected: FAIL because the cascade service and delete helpers do not exist.

- [ ] **Step 4: Add repository/task/case/artifact bulk query helpers**

```java
List<SceneEntity> findAllByRepoId(Long repoId);
List<TaskEntity> findAllByRepoIdOrderByIdAsc(Long repoId);
void deleteAllByRepoId(Long repoId);
void deleteAllByTaskIdIn(List<Long> taskIds);
List<ArtifactEntity> findAllByTaskIdIn(List<Long> taskIds);
```

- [ ] **Step 5: Add object deletion to storage service**

```java
void deleteObject(String bucket, String objectKey);
```

```java
@Override
public void deleteObject(String bucket, String objectKey) {
    try {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build());
    } catch (Exception exception) {
        throw new IllegalStateException("Failed to delete object from storage", exception);
    }
}
```

- [ ] **Step 6: Implement the cascade delete service**

```java
@Transactional
public void deleteRepositoryGraph(Long repoId) {
    TestRepositoryEntity repo = repositoryRepository.findById(repoId)
            .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repoId));

    List<TaskEntity> tasks = taskRepository.findAllByRepoIdOrderByIdAsc(repoId);
    List<Long> taskIds = tasks.stream().map(TaskEntity::getId).toList();
    List<ArtifactEntity> artifacts = taskIds.isEmpty() ? List.of() : artifactRepository.findAllByTaskIdIn(taskIds);

    for (ArtifactEntity artifact : artifacts) {
        if (artifact.getBucket() != null && artifact.getObjectKey() != null) {
            objectStorageService.deleteObject(artifact.getBucket(), artifact.getObjectKey());
        }
    }

    taskIds.forEach(taskId -> {
        TaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task != null && task.getReportUrl() != null && task.getReportUrl().contains("/" + storageBucket + "/")) {
            String objectKey = extractObjectKey(task.getReportUrl());
            objectStorageService.deleteObject(storageBucket, objectKey);
        }
    });

    if (!taskIds.isEmpty()) {
        caseResultRepository.deleteAllByTaskIdIn(taskIds);
        artifactRepository.deleteAllByTaskIdIn(taskIds);
    }
    taskRepository.deleteAllByRepoId(repoId);
    sceneRepository.deleteAllByRepoId(repoId);
    repositoryRepository.delete(repo);
}
```

- [ ] **Step 7: Route repository deletion through the cascade service**

```java
    @Override
    public void delete(Long id) {
        repositoryCascadeDeleteService.deleteRepositoryGraph(id);
    }
```

- [ ] **Step 8: Convert storage/data integrity failures into readable API errors**

```java
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleIllegalState(IllegalStateException ex) {
        return new ApiErrorResponse("CONFLICT", ex.getMessage());
    }
```

- [ ] **Step 9: Re-run the repository tests**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=RepositoryControllerTest test`
Expected: PASS

- [ ] **Step 10: Commit the cascading deletion implementation**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-server/src/main/java/com/example/platform/repository/service \
  playwright-platform-server/src/main/java/com/example/platform/scene/service \
  playwright-platform-server/src/main/java/com/example/platform/task/model \
  playwright-platform-server/src/main/java/com/example/platform/storage/service \
  playwright-platform-server/src/main/java/com/example/platform/common/GlobalExceptionHandler.java \
  playwright-platform-server/src/test/java/com/example/platform/repository/RepositoryControllerTest.java
git commit -m "feat: add business cascading deletion"
```

### Task 3: Add Scene-Centered APIs and Scene-Scoped Task History

**Files:**
- Create: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/scene/dto/SceneCardResponse.java`
- Create: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/task/dto/SceneTaskListResponse.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneService.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneServiceImpl.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/scene/controller/SceneController.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/task/service/TaskService.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/task/model/TaskJpaRepository.java`
- Test: `/Users/bytedance/test_platform/playwright-platform-server/src/test/java/com/example/platform/scene/SceneControllerTest.java`
- Test: `/Users/bytedance/test_platform/playwright-platform-server/src/test/java/com/example/platform/task/TaskControllerTest.java`

- [ ] **Step 1: Write the failing scene card response test**

```java
mockMvc.perform(get("/api/scenes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("checkout smoke"))
        .andExpect(jsonPath("$[0].lastTaskStatus").value("SUCCESS"))
        .andExpect(jsonPath("$[0].environmentVariableCount").value(2));
```

- [ ] **Step 2: Write the failing scene task list test**

```java
mockMvc.perform(get("/api/scenes/11/tasks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].sceneId").value(11))
        .andExpect(jsonPath("$[0].id").value(101));
```

- [ ] **Step 3: Run the failing scene/task controller tests**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=SceneControllerTest,TaskControllerTest test`
Expected: FAIL because scene card DTOs and scene-scoped task endpoints do not exist.

- [ ] **Step 4: Add newest-first task query methods**

```java
List<TaskEntity> findAllBySceneIdOrderByCreatedAtDescIdDesc(Long sceneId);
List<TaskEntity> findAllByOrderByCreatedAtDescIdDesc();
```

- [ ] **Step 5: Add the scene card DTO**

```java
public record SceneCardResponse(
        Long id,
        Long repoId,
        String name,
        String description,
        String branch,
        boolean enabled,
        boolean scheduleEnabled,
        String cronExpression,
        String lastTaskStatus,
        LocalDateTime lastRunAt,
        int environmentVariableCount) {
}
```

- [ ] **Step 6: Add scene-scoped task DTO if task entity should not be exposed directly**

```java
public record SceneTaskListResponse(
        Long id,
        Long sceneId,
        String status,
        String triggerType,
        String branch,
        Long durationMs,
        LocalDateTime createdAt,
        String reportUrl) {
}
```

- [ ] **Step 7: Update scene service/controller to return scene cards**

```java
@GetMapping
public List<SceneCardResponse> list() {
    return sceneService.listCards();
}
```

```java
public List<SceneCardResponse> listCards() {
    return repository.findAllByOrderByUpdatedAtDescIdDesc().stream()
            .map(this::toCard)
            .toList();
}
```

- [ ] **Step 8: Add the scene-scoped task endpoint**

```java
@GetMapping("/api/scenes/{sceneId}/tasks")
public List<SceneTaskListResponse> listSceneTasks(@PathVariable Long sceneId) {
    return taskService.listByScene(sceneId).stream()
            .map(SceneTaskListResponse::from)
            .toList();
}
```

- [ ] **Step 9: Update task execution to refresh scene summary fields**

```java
scene.setLastRunAt(task.getFinishedAt());
scene.setLastTaskStatus(task.getStatus());
sceneRepository.save(scene);
```

- [ ] **Step 10: Re-run the scene/task controller tests**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=SceneControllerTest,TaskControllerTest test`
Expected: PASS

- [ ] **Step 11: Commit the scene-centered API changes**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-server/src/main/java/com/example/platform/scene \
  playwright-platform-server/src/main/java/com/example/platform/task \
  playwright-platform-server/src/test/java/com/example/platform/scene/SceneControllerTest.java \
  playwright-platform-server/src/test/java/com/example/platform/task/TaskControllerTest.java
git commit -m "feat: add scene centered task apis"
```

### Task 4: Modernize the Scene Center into Card-Based Workflow

**Files:**
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/router/index.ts`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/api/scene.ts`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/stores/scene.ts`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/types/scene.ts`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/views/scene/SceneListView.vue`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/style.css`
- Test: `/Users/bytedance/test_platform/playwright-platform-web/src/stores/scene.test.ts`

- [ ] **Step 1: Write the failing scene store test for card responses**

```ts
it('maps scene card response with last task summary', async () => {
  http.get = vi.fn().mockResolvedValue({
    data: [{ id: 1, name: 'checkout smoke', description: 'nightly', lastTaskStatus: 'SUCCESS', scheduleEnabled: true, environmentVariableCount: 2 }]
  })

  await store.fetchAll()

  expect(store.items[0].lastTaskStatus).toBe('SUCCESS')
  expect(store.items[0].environmentVariableCount).toBe(2)
})
```

- [ ] **Step 2: Run the failing scene store test**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- src/stores/scene.test.ts`
Expected: FAIL because the type/store shape does not match the new API.

- [ ] **Step 3: Update scene type definitions**

```ts
export interface SceneRecord {
  id: number
  repoId: number
  name: string
  description: string
  branch: string
  enabled: boolean
  scheduleEnabled: boolean
  cronExpression: string
  lastTaskStatus: string | null
  lastRunAt: string | null
  environmentVariableCount: number
}
```

- [ ] **Step 4: Update scene API/store mapping**

```ts
export async function fetchScenes() {
  const { data } = await http.get<SceneRecord[]>('/api/scenes')
  return data
}
```

- [ ] **Step 5: Replace the scene table with a card grid workflow**

```vue
<div class="scene-grid">
  <article v-for="row in rows" :key="row.id" class="scene-card">
    <div class="scene-card__header">
      <div>
        <p class="scene-card__title">{{ row.name }}</p>
        <p class="scene-card__subtitle">{{ row.description || '未填写描述' }}</p>
      </div>
      <el-tag :type="row.lastTaskStatus === 'SUCCESS' ? 'success' : row.lastTaskStatus === 'FAILED' ? 'danger' : 'info'">
        {{ row.lastTaskStatus || '未执行' }}
      </el-tag>
    </div>
    <div class="scene-card__meta">
      <span>分支 {{ row.branch }}</span>
      <span>环境变量 {{ row.environmentVariableCount }}</span>
      <span>{{ row.scheduleEnabled ? '已启用定时' : '手动执行' }}</span>
    </div>
    <div class="scene-card__actions">
      <el-button type="primary" @click="run(row)">开始执行</el-button>
      <el-button @click="openTasks(row)">查看任务</el-button>
      <el-button text @click="openEdit(row)">编辑</el-button>
      <el-button text type="danger" @click="remove(row)">删除</el-button>
    </div>
  </article>
</div>
```

- [ ] **Step 6: Add route navigation from scene card to scene-scoped tasks**

```ts
function openTasks(row: SceneRecord) {
  void router.push(`/scenes/${row.id}/tasks`)
}
```

- [ ] **Step 7: Add the supporting shared CSS for card layout**

```css
.scene-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 20px;
}

.scene-card {
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 20px;
  padding: 24px;
  box-shadow: 0 20px 45px rgba(15, 23, 42, 0.08);
}
```

- [ ] **Step 8: Re-run the scene store test**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- src/stores/scene.test.ts`
Expected: PASS

- [ ] **Step 9: Commit the scene center refactor**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-web/src/router/index.ts \
  playwright-platform-web/src/api/scene.ts \
  playwright-platform-web/src/stores/scene.ts \
  playwright-platform-web/src/types/scene.ts \
  playwright-platform-web/src/views/scene/SceneListView.vue \
  playwright-platform-web/src/style.css \
  playwright-platform-web/src/stores/scene.test.ts
git commit -m "feat: add scene card workflow"
```

### Task 5: Move Task History Under Scene and Enforce Newest-First Ordering

**Files:**
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/router/index.ts`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/api/task.ts`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/stores/task.ts`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/types/task.ts`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/views/task/TaskListView.vue`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/router/index.test.ts`
- Test: `/Users/bytedance/test_platform/playwright-platform-server/src/test/java/com/example/platform/task/TaskControllerTest.java`

- [ ] **Step 1: Write the failing router test for scene-scoped task route**

```ts
it('registers the scene task history route', () => {
  const routes = router.getRoutes()
  expect(routes.some((route) => route.path === '/scenes/:id/tasks')).toBe(true)
})
```

- [ ] **Step 2: Run the failing router test**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- src/router/index.test.ts`
Expected: FAIL because the route does not exist.

- [ ] **Step 3: Add the scene task route**

```ts
{ path: '/scenes/:id/tasks', component: TaskListView, meta: { title: '场景任务列表' } },
```

- [ ] **Step 4: Update the task API/store to fetch scene-scoped tasks**

```ts
export async function fetchSceneTasks(sceneId: number) {
  const { data } = await http.get<TaskRecord[]>(`/api/scenes/${sceneId}/tasks`)
  return data
}
```

```ts
async function fetchByScene(sceneId: number) {
  loading.value = true
  try {
    items.value = await taskApi.fetchSceneTasks(sceneId)
  } finally {
    loading.value = false
  }
}
```

- [ ] **Step 5: Update `TaskListView.vue` to read `sceneId` from route and load scene tasks**

```ts
const route = useRoute()
const sceneId = computed(() => Number(route.params.id))

onMounted(() => {
  void store.fetchByScene(sceneId.value)
})
```

- [ ] **Step 6: Update the task list UI to show newest-first history in scene context**

```vue
<div class="page-hero">
  <p class="eyebrow">Scene Task History</p>
  <h1>场景任务列表</h1>
  <p class="hero-copy">查看当前场景的执行历史，最新任务排在最前面。</p>
</div>
```

- [ ] **Step 7: Re-run the router test**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- src/router/index.test.ts`
Expected: PASS

- [ ] **Step 8: Re-run the backend task controller test**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=TaskControllerTest test`
Expected: PASS with scene task ordering enforced by repository query.

- [ ] **Step 9: Commit the task history restructure**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-web/src/router/index.ts \
  playwright-platform-web/src/api/task.ts \
  playwright-platform-web/src/stores/task.ts \
  playwright-platform-web/src/types/task.ts \
  playwright-platform-web/src/views/task/TaskListView.vue \
  playwright-platform-web/src/router/index.test.ts \
  playwright-platform-server/src/test/java/com/example/platform/task/TaskControllerTest.java
git commit -m "feat: move task history under scenes"
```

### Task 6: Refresh Task Detail and Shared UI Styling for the MVP

**Files:**
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/views/task/TaskDetailView.vue`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/views/task/TaskDetailView.test.ts`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/style.css`

- [ ] **Step 1: Write the failing task detail UI test for scene-oriented summary blocks**

```ts
it('renders modern summary sections for task detail', async () => {
  render(TaskDetailView, { global: { plugins: [router, pinia] } })
  expect(await screen.findByText('任务详情')).toBeInTheDocument()
  expect(screen.getByText('附件列表')).toBeInTheDocument()
})
```

- [ ] **Step 2: Run the failing task detail test**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- src/views/task/TaskDetailView.test.ts`
Expected: FAIL if the new visual structure or copy is not yet reflected in the test.

- [ ] **Step 3: Refresh task detail structure without breaking report/artifact capabilities**

```vue
<div class="detail-summary-grid">
  <section class="summary-panel">
    <h2>执行摘要</h2>
    ...
  </section>
  <section class="summary-panel">
    <h2>产出情况</h2>
    ...
  </section>
</div>
```

- [ ] **Step 4: Add shared modern surface styles**

```css
.page-grid {
  min-height: 100vh;
  padding: 32px;
  background:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.12), transparent 34%),
    linear-gradient(180deg, #f8fafc 0%, #eef2ff 100%);
}

.glass-card,
.summary-panel {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(14px);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 20px;
}
```

- [ ] **Step 5: Re-run the task detail test**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- src/views/task/TaskDetailView.test.ts`
Expected: PASS

- [ ] **Step 6: Commit the UI refresh**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-web/src/views/task/TaskDetailView.vue \
  playwright-platform-web/src/views/task/TaskDetailView.test.ts \
  playwright-platform-web/src/style.css
git commit -m "feat: refresh execution mvp ui"
```

### Task 7: Add Minimal Real Scheduling Fields and Backend Hook Points

**Files:**
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneServiceImpl.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/scene/controller/SceneController.java`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/views/scene/SceneListView.vue`
- Modify: `/Users/bytedance/test_platform/playwright-platform-web/src/types/scene.ts`
- Test: `/Users/bytedance/test_platform/playwright-platform-server/src/test/java/com/example/platform/scene/SceneControllerTest.java`

- [ ] **Step 1: Write the failing scene controller test for schedule fields**

```java
mockMvc.perform(post("/api/scenes")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "repoId": 1,
              "name": "nightly smoke",
              "branch": "main",
              "testSelectorType": "grep",
              "testSelectorValue": "@smoke",
              "runCommand": "PLAYWRIGHT_PLATFORM_MODE=true npm run test:e2e",
              "scheduleEnabled": true,
              "cronExpression": "0 0 2 * * ?",
              "enabled": true
            }
            """))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.scheduleEnabled").value(true))
    .andExpect(jsonPath("$.cronExpression").value("0 0 2 * * ?"));
```

- [ ] **Step 2: Run the failing scene controller test**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=SceneControllerTest test`
Expected: FAIL because schedule fields are not yet copied during create/update.

- [ ] **Step 3: Persist schedule fields in scene create/update**

```java
existing.setDescription(entity.getDescription());
existing.setScheduleEnabled(Boolean.TRUE.equals(entity.getScheduleEnabled()));
existing.setCronExpression(entity.getCronExpression());
```

- [ ] **Step 4: Surface schedule controls in the scene form**

```vue
<el-form-item label="启用定时"><el-switch v-model="form.scheduleEnabled" /></el-form-item>
<el-form-item label="Cron 表达式"><el-input v-model="form.cronExpression" placeholder="0 0 2 * * ?" /></el-form-item>
```

- [ ] **Step 5: Add a backend scheduler hook point without full distributed design**

```java
@Scheduled(fixedDelay = 60000)
public void triggerScheduledScenes() {
    // Query enabled scenes with scheduleEnabled=true and valid cronExpression.
    // For MVP hook point, log or trigger createAndRun when the cron matches current time.
}
```

- [ ] **Step 6: Re-run the scene controller test**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=SceneControllerTest test`
Expected: PASS

- [ ] **Step 7: Commit the scheduling MVP hooks**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-server/src/main/java/com/example/platform/scene \
  playwright-platform-web/src/views/scene/SceneListView.vue \
  playwright-platform-web/src/types/scene.ts \
  playwright-platform-server/src/test/java/com/example/platform/scene/SceneControllerTest.java
git commit -m "feat: add scheduling mvp hook points"
```

### Task 8: End-to-End Verification and Hand-off

**Files:**
- Test: `/Users/bytedance/test_platform/playwright-platform-server`
- Test: `/Users/bytedance/test_platform/playwright-platform-web`

- [ ] **Step 1: Run backend targeted regression tests**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn -Dtest=RepositoryControllerTest,SceneControllerTest,TaskControllerTest,TaskExecutionServiceTest test`
Expected: PASS

- [ ] **Step 2: Run frontend targeted regression tests**

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm test -- src/stores/scene.test.ts src/router/index.test.ts src/views/task/TaskDetailView.test.ts`
Expected: PASS

- [ ] **Step 3: Start backend and frontend and spot-check the new workflow**

Run: `cd /Users/bytedance/test_platform/playwright-platform-server && mvn spring-boot:run`
Expected: Backend starts on `http://127.0.0.1:8080`

Run: `cd /Users/bytedance/test_platform/playwright-platform-web && npm run dev -- --host 0.0.0.0 --port 4173`
Expected: Frontend starts on `http://127.0.0.1:4173`

- [ ] **Step 4: Verify the user workflow manually**

Check:
- repository delete succeeds with lifecycle cleanup,
- scene page shows cards instead of the old table-first workflow,
- scene card can open scene-scoped task history,
- scene task list is newest-first,
- task detail still opens report and artifact actions.

- [ ] **Step 5: Commit the final verified integration changes**

```bash
cd /Users/bytedance/test_platform
git add .
git commit -m "feat: complete execution mvp workflow"
```
