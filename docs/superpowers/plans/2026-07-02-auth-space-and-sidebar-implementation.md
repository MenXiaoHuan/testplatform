# 认证、空间体系与侧边栏用户区 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为平台增加最小可用的登录态、空间隔离、空间申请审批、侧边栏用户区和空间级权限控制，并将现有仓库、场景、任务、调度事件全部收口到空间上下文。

**Architecture:** 后端采用内置用户目录、RSA 登录口令加密、内存 Session 和空间成员关系；前端采用 `Home -> 空间 -> 业务页面` 的路由层级、独立 `auth/space` store、空间切换器与侧边栏用户区。业务权限不再依赖全局角色，而是以“当前用户在当前空间中的角色”为准。

**Tech Stack:** Spring Boot 3.5、MyBatis、Flyway、Vue 3、TypeScript、Pinia、Vue Router、Element Plus、Vitest

---

## File Structure

### Backend new files

- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/config/AuthProperties.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/context/AuthContext.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/context/AuthContextHolder.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/crypto/AuthKeyProvider.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/model/AuthSession.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/model/AuthUser.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/controller/AuthController.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/service/AuthService.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/service/AuthServiceImpl.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/filter/AuthSessionFilter.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/model/SpaceEntity.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/model/SpaceMemberEntity.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/model/SpaceAccessRequestEntity.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/mapper/SpaceMapper.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/mapper/SpaceMemberMapper.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/mapper/SpaceAccessRequestMapper.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/dto/SpaceSummaryResponse.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/dto/CreateSpaceRequest.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/dto/SubmitSpaceAccessRequestRequest.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/dto/ReviewSpaceAccessRequestRequest.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/controller/SpaceController.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/controller/SpaceAccessRequestController.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/service/SpaceService.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/service/SpaceServiceImpl.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/service/SpaceAccessRequestService.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/service/SpaceAccessRequestServiceImpl.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/service/SpaceAuthorizationService.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/service/SpaceAuthorizationServiceImpl.java`
- Create: `playwright-platform-server/src/main/resources/db/migration/V4__add_space_model.sql`
- Create: `playwright-platform-server/src/test/java/com/example/platform/auth/AuthServiceTest.java`
- Create: `playwright-platform-server/src/test/java/com/example/platform/auth/AuthControllerTest.java`
- Create: `playwright-platform-server/src/test/java/com/example/platform/space/SpaceServiceTest.java`
- Create: `playwright-platform-server/src/test/java/com/example/platform/space/SpaceAccessRequestServiceTest.java`
- Create: `playwright-platform-server/src/test/java/com/example/platform/space/SpaceControllerTest.java`
- Create: `playwright-platform-server/src/test/java/com/example/platform/space/SpaceAccessRequestControllerTest.java`

### Backend modified files

- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/model/TestRepositoryEntity.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/model/SceneEntity.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/model/TaskEntity.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/model/ScheduleEventEntity.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/mapper/TestRepositoryMapper.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/mapper/SceneMapper.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/mapper/TaskMapper.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/mapper/ScheduleEventMapper.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/service/ScheduleEventAdminServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/controller/RepositoryController.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/controller/SceneController.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/controller/TaskController.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/controller/ScheduleEventController.java`
- Modify: `playwright-platform-server/src/main/resources/db/schema/SCHEMA_OVERVIEW.sql`

### Frontend new files

- Create: `playwright-platform-web/src/types/auth.ts`
- Create: `playwright-platform-web/src/types/space.ts`
- Create: `playwright-platform-web/src/api/auth.ts`
- Create: `playwright-platform-web/src/api/space.ts`
- Create: `playwright-platform-web/src/stores/auth.ts`
- Create: `playwright-platform-web/src/stores/space.ts`
- Create: `playwright-platform-web/src/utils/auth-crypto.ts`
- Create: `playwright-platform-web/src/views/auth/LoginView.vue`
- Create: `playwright-platform-web/src/views/home/HomeView.vue`
- Create: `playwright-platform-web/src/views/space/SpaceAccessRequestListView.vue`
- Create: `playwright-platform-web/src/views/space/SpaceSettingsView.vue`
- Create: `playwright-platform-web/src/views/space/SpaceNoAccessView.vue`
- Create: `playwright-platform-web/src/components/layout/SidebarUserPanel.vue`
- Create: `playwright-platform-web/src/components/layout/SpaceSwitcher.vue`
- Create: `playwright-platform-web/src/assets/default-avatar.png`
- Create: `playwright-platform-web/tests/unit/auth.store.test.ts`
- Create: `playwright-platform-web/tests/unit/space.store.test.ts`
- Create: `playwright-platform-web/tests/unit/login-view.test.ts`
- Create: `playwright-platform-web/tests/unit/sidebar-user-panel.test.ts`

### Frontend modified files

- Modify: `playwright-platform-web/src/api/http.ts`
- Modify: `playwright-platform-web/src/router/index.ts`
- Modify: `playwright-platform-web/src/App.vue`
- Modify: `playwright-platform-web/src/main.ts`
- Modify: `playwright-platform-web/src/api/repository.ts`
- Modify: `playwright-platform-web/src/api/scene.ts`
- Modify: `playwright-platform-web/src/api/task.ts`
- Modify: `playwright-platform-web/src/api/schedule-event.ts`
- Modify: `playwright-platform-web/src/stores/repository.ts`
- Modify: `playwright-platform-web/src/stores/scene.ts`
- Modify: `playwright-platform-web/src/stores/task.ts`
- Modify: `playwright-platform-web/src/stores/schedule-event.ts`
- Modify: `playwright-platform-web/src/views/repository/RepositoryListView.vue`
- Modify: `playwright-platform-web/src/views/scene/SceneListView.vue`
- Modify: `playwright-platform-web/src/views/scene/ScheduleEventListView.vue`
- Modify: `playwright-platform-web/src/views/task/TaskListView.vue`
- Modify: `playwright-platform-web/src/views/task/TaskDetailView.vue`

---

### Task 1: 建立后端认证基础

**Files:**
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/config/AuthProperties.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/model/AuthUser.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/model/AuthSession.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/context/AuthContext.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/context/AuthContextHolder.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/crypto/AuthKeyProvider.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/service/AuthService.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/service/AuthServiceImpl.java`
- Create: `playwright-platform-server/src/test/java/com/example/platform/auth/AuthServiceTest.java`

- [ ] **Step 1: 写认证 service 的失败测试**

```java
@Test
void should_create_session_when_credentials_are_valid() {
    AuthService service = createServiceWithUser("admin", "$2a$...");
    LoginResult result = service.login("admin", encryptedPassword("secret"));
    assertThat(result.username()).isEqualTo("admin");
    assertThat(result.sessionId()).isNotBlank();
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw -q -Dtest=AuthServiceTest test`
Expected: FAIL with `AuthService` or `login` not implemented

- [ ] **Step 3: 写最小实现**

```java
public record AuthUser(Long id, String username, String nickname, String passwordHash, String avatarObjectKey, boolean enabled) {}

public record AuthSession(String sessionId, Long userId, String username, String nickname, String avatarObjectKey, Long lastSpaceId, LocalDateTime expiresAt) {}

public interface AuthService {
    LoginResult login(String username, String encryptedPassword);
    Optional<AuthSession> findSession(String sessionId);
    Optional<LoginUserResponse> currentUser(String sessionId);
    void logout(String sessionId);
}
```

- [ ] **Step 4: 补 `AuthProperties` 和 `AuthKeyProvider`**

```java
@ConfigurationProperties(prefix = "platform.auth")
public class AuthProperties {
    private String cookieName = "platform_session";
    private int slidingDays = 14;
    private List<UserConfig> users = new ArrayList<>();
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./mvnw -q -Dtest=AuthServiceTest test`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add playwright-platform-server/src/main/java/com/example/platform/auth playwright-platform-server/src/test/java/com/example/platform/auth/AuthServiceTest.java
git commit -m "feat: add minimal auth service foundation"
```

### Task 2: 暴露认证接口并接入 Cookie Session

**Files:**
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/controller/AuthController.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/auth/filter/AuthSessionFilter.java`
- Create: `playwright-platform-server/src/test/java/com/example/platform/auth/AuthControllerTest.java`

- [ ] **Step 1: 写登录与当前用户接口的失败测试**

```java
@Test
void should_return_public_key() throws Exception {
    mockMvc.perform(get("/api/auth/public-key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.publicKeyPem").isNotEmpty());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw -q -Dtest=AuthControllerTest test`
Expected: FAIL with 404 or missing bean

- [ ] **Step 3: 写最小控制器与过滤器**

```java
@RestController
@RequestMapping("/api/auth")
class AuthController {
    @GetMapping("/public-key")
    PublicKeyResponse publicKey() { ... }
    @PostMapping("/login")
    LoginUserResponse login(@RequestBody LoginRequest request, HttpServletResponse response) { ... }
    @GetMapping("/me")
    LoginUserResponse currentUser() { ... }
    @PostMapping("/logout")
    void logout(HttpServletRequest request, HttpServletResponse response) { ... }
}
```

- [ ] **Step 4: 在过滤器中恢复 Session 并刷新过期时间**

```java
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
    String sessionId = readCookie(request, authProperties.getCookieName());
    authService.findSession(sessionId).ifPresent(session -> AuthContextHolder.set(...));
    filterChain.doFilter(request, response);
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./mvnw -q -Dtest=AuthControllerTest test`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add playwright-platform-server/src/main/java/com/example/platform/auth playwright-platform-server/src/test/java/com/example/platform/auth/AuthControllerTest.java
git commit -m "feat: expose auth endpoints and session filter"
```

### Task 3: 引入空间数据模型与迁移脚本

**Files:**
- Create: `playwright-platform-server/src/main/resources/db/migration/V4__add_space_model.sql`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/model/SpaceEntity.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/model/SpaceMemberEntity.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/model/SpaceAccessRequestEntity.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/mapper/SpaceMapper.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/mapper/SpaceMemberMapper.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/mapper/SpaceAccessRequestMapper.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/model/TestRepositoryEntity.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/model/SceneEntity.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/model/TaskEntity.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/model/ScheduleEventEntity.java`
- Modify: `playwright-platform-server/src/main/resources/db/schema/SCHEMA_OVERVIEW.sql`

- [ ] **Step 1: 写 Flyway 迁移校验失败测试**

```java
@Test
void should_map_space_id_columns_after_migration() {
    assertThat(entitySchemaMappingTest.columnExists("scene", "space_id")).isTrue();
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw -q -Dtest=EntitySchemaMappingTest test`
Expected: FAIL because `space_id` columns and new tables do not exist

- [ ] **Step 3: 编写迁移脚本**

```sql
create table space (...);
create table space_member (..., unique key uk_space_member (space_id, user_id));
create table space_access_request (...);
alter table test_repository add column space_id bigint not null;
alter table scene add column space_id bigint not null;
alter table task add column space_id bigint not null;
alter table schedule_event add column space_id bigint not null;
```

- [ ] **Step 4: 补实体和 mapper 字段**

```java
private Long spaceId;
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./mvnw -q -Dtest=EntitySchemaMappingTest test`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add playwright-platform-server/src/main/resources/db/migration/V4__add_space_model.sql playwright-platform-server/src/main/java/com/example/platform/space playwright-platform-server/src/main/java/com/example/platform/{repository,scene,task} playwright-platform-server/src/main/resources/db/schema/SCHEMA_OVERVIEW.sql
git commit -m "feat: add space schema and space-bound entities"
```

### Task 4: 实现空间服务、申请审批与空间鉴权

**Files:**
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/dto/SpaceSummaryResponse.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/dto/CreateSpaceRequest.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/dto/SubmitSpaceAccessRequestRequest.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/dto/ReviewSpaceAccessRequestRequest.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/service/SpaceService.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/service/SpaceServiceImpl.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/service/SpaceAccessRequestService.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/service/SpaceAccessRequestServiceImpl.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/service/SpaceAuthorizationService.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/service/SpaceAuthorizationServiceImpl.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/controller/SpaceController.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/space/controller/SpaceAccessRequestController.java`
- Create: `playwright-platform-server/src/test/java/com/example/platform/space/SpaceServiceTest.java`
- Create: `playwright-platform-server/src/test/java/com/example/platform/space/SpaceAccessRequestServiceTest.java`
- Create: `playwright-platform-server/src/test/java/com/example/platform/space/SpaceControllerTest.java`
- Create: `playwright-platform-server/src/test/java/com/example/platform/space/SpaceAccessRequestControllerTest.java`

- [ ] **Step 1: 写“创建空间自动成为管理员”的失败测试**

```java
@Test
void should_add_creator_as_admin_when_space_is_created() {
    SpaceSummaryResponse response = service.createSpace(currentUser(), new CreateSpaceRequest("默认空间", "desc"));
    assertThat(memberMapper.findBySpaceIdAndUserId(response.id(), currentUser().id())).isPresent();
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw -q -Dtest=SpaceServiceTest,SpaceAccessRequestServiceTest test`
Expected: FAIL because services are missing

- [ ] **Step 3: 写最小服务实现**

```java
public interface SpaceAuthorizationService {
    void requireMember(Long spaceId);
    void requireOperator(Long spaceId);
    void requireAdmin(Long spaceId);
}
```

- [ ] **Step 4: 实现申请与审批规则**

```java
if (memberMapper.findActive(spaceId, applicantId).isPresent()) {
    throw new IllegalStateException("already a member");
}
if (requestMapper.findPending(spaceId, applicantId).isPresent()) {
    throw new IllegalStateException("request already pending");
}
```

- [ ] **Step 5: 暴露空间和审批接口**

```java
@GetMapping("/api/spaces")
List<SpaceSummaryResponse> listMySpaces() { ... }

@PostMapping("/api/spaces/{spaceId}/access-requests/{requestId}/approve")
void approve(...) { ... }
```

- [ ] **Step 6: 运行测试确认通过**

Run: `./mvnw -q -Dtest=SpaceServiceTest,SpaceAccessRequestServiceTest,SpaceControllerTest,SpaceAccessRequestControllerTest test`
Expected: PASS

- [ ] **Step 7: 提交**

```bash
git add playwright-platform-server/src/main/java/com/example/platform/space playwright-platform-server/src/test/java/com/example/platform/space
git commit -m "feat: add space services and approval flow"
```

### Task 5: 给现有业务接口接入空间路径与空间级权限

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/controller/RepositoryController.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/controller/SceneController.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/controller/TaskController.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/controller/ScheduleEventController.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/service/ScheduleEventAdminServiceImpl.java`
- Modify: existing controller tests under `src/test/java/com/example/platform/{repository,scene,task}`

- [ ] **Step 1: 写仓库/场景/任务/调度事件控制器的失败测试**

```java
@Test
void should_return_forbidden_when_viewer_retries_schedule_event() throws Exception {
    mockMvc.perform(post("/api/spaces/7/schedule-events/9/retry").cookie(sessionCookieForViewer()))
        .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw -q -Dtest=RepositoryControllerTest,SceneControllerTest,TaskControllerTest,ScheduleEventControllerTest test`
Expected: FAIL because routes or auth checks do not match

- [ ] **Step 3: 修改控制器路径并注入空间鉴权**

```java
@RequestMapping("/api/spaces/{spaceId}/scenes")
public class SceneController { ... }
```

- [ ] **Step 4: 在 service 层补 `spaceId` 过滤与操作人来源**

```java
authorizationService.requireOperator(spaceId);
task.setSpaceId(spaceId);
auditLog.setOperatorName(AuthContextHolder.require().nickname());
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./mvnw -q -Dtest=RepositoryControllerTest,SceneControllerTest,TaskControllerTest,ScheduleEventControllerTest test`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add playwright-platform-server/src/main/java/com/example/platform/{repository,scene,task} playwright-platform-server/src/test/java/com/example/platform/{repository,scene,task}
git commit -m "feat: scope business APIs by space and role"
```

### Task 6: 建立前端认证与空间 store、登录页、路由守卫

**Files:**
- Create: `playwright-platform-web/src/types/auth.ts`
- Create: `playwright-platform-web/src/types/space.ts`
- Create: `playwright-platform-web/src/api/auth.ts`
- Create: `playwright-platform-web/src/api/space.ts`
- Create: `playwright-platform-web/src/stores/auth.ts`
- Create: `playwright-platform-web/src/stores/space.ts`
- Create: `playwright-platform-web/src/utils/auth-crypto.ts`
- Create: `playwright-platform-web/src/views/auth/LoginView.vue`
- Create: `playwright-platform-web/src/views/home/HomeView.vue`
- Create: `playwright-platform-web/tests/unit/auth.store.test.ts`
- Create: `playwright-platform-web/tests/unit/space.store.test.ts`
- Create: `playwright-platform-web/tests/unit/login-view.test.ts`
- Modify: `playwright-platform-web/src/api/http.ts`
- Modify: `playwright-platform-web/src/router/index.ts`
- Modify: `playwright-platform-web/src/main.ts`

- [ ] **Step 1: 写 auth/space store 和登录页的失败测试**

```ts
it('redirects unauthenticated users to /login', async () => {
  await router.push('/spaces/7/scenes')
  expect(router.currentRoute.value.path).toBe('/login')
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- tests/unit/auth.store.test.ts tests/unit/space.store.test.ts tests/unit/login-view.test.ts`
Expected: FAIL because store, routes, and login view do not exist

- [ ] **Step 3: 写最小 API 与 store**

```ts
export interface LoginUser {
  id: number
  username: string
  nickname: string
  avatarUrl?: string | null
  lastSpaceId?: number | null
}
```

- [ ] **Step 4: 在 HTTP 层开启 Cookie 并处理 401/403**

```ts
const http = axios.create({ baseURL: '/api', timeout: 10000, withCredentials: true })
```

- [ ] **Step 5: 写登录页与路由守卫**

```ts
router.beforeEach(async (to) => {
  if (to.path !== '/login' && !authStore.isAuthenticated) return '/login'
})
```

- [ ] **Step 6: 运行测试确认通过**

Run: `npm test -- tests/unit/auth.store.test.ts tests/unit/space.store.test.ts tests/unit/login-view.test.ts`
Expected: PASS

- [ ] **Step 7: 提交**

```bash
git add playwright-platform-web/src/{types,api,stores,utils,views/auth,views/home,router/index.ts,main.ts} playwright-platform-web/tests/unit/{auth.store.test.ts,space.store.test.ts,login-view.test.ts}
git commit -m "feat: add frontend auth and space entry flow"
```

### Task 7: 实现侧边栏用户区、空间切换器与空间页面

**Files:**
- Create: `playwright-platform-web/src/components/layout/SidebarUserPanel.vue`
- Create: `playwright-platform-web/src/components/layout/SpaceSwitcher.vue`
- Create: `playwright-platform-web/src/views/space/SpaceNoAccessView.vue`
- Create: `playwright-platform-web/src/views/space/SpaceAccessRequestListView.vue`
- Create: `playwright-platform-web/src/views/space/SpaceSettingsView.vue`
- Create: `playwright-platform-web/tests/unit/sidebar-user-panel.test.ts`
- Modify: `playwright-platform-web/src/App.vue`

- [ ] **Step 1: 写侧边栏用户区和空间切换器的失败测试**

```ts
it('shows nickname and default avatar fallback', async () => {
  const wrapper = mount(SidebarUserPanel, { props: { user: { nickname: '徐个愿', avatarUrl: null } } })
  expect(wrapper.text()).toContain('徐个愿')
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- tests/unit/sidebar-user-panel.test.ts`
Expected: FAIL because components do not exist

- [ ] **Step 3: 写最小组件实现**

```vue
<img :src="resolvedAvatarUrl" @error="useDefaultAvatar = true" />
<span>{{ user.nickname }}</span>
```

- [ ] **Step 4: 修改 `App.vue` 承载空间切换器和用户区**

```vue
<SpaceSwitcher v-if="spaceStore.currentSpace" />
<SidebarUserPanel v-if="authStore.user" />
```

- [ ] **Step 5: 运行测试确认通过**

Run: `npm test -- tests/unit/sidebar-user-panel.test.ts`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add playwright-platform-web/src/components/layout playwright-platform-web/src/views/space playwright-platform-web/src/App.vue playwright-platform-web/tests/unit/sidebar-user-panel.test.ts
git commit -m "feat: add sidebar user panel and space navigation"
```

### Task 8: 将现有业务页切换到空间上下文并收口权限

**Files:**
- Modify: `playwright-platform-web/src/api/repository.ts`
- Modify: `playwright-platform-web/src/api/scene.ts`
- Modify: `playwright-platform-web/src/api/task.ts`
- Modify: `playwright-platform-web/src/api/schedule-event.ts`
- Modify: `playwright-platform-web/src/stores/repository.ts`
- Modify: `playwright-platform-web/src/stores/scene.ts`
- Modify: `playwright-platform-web/src/stores/task.ts`
- Modify: `playwright-platform-web/src/stores/schedule-event.ts`
- Modify: `playwright-platform-web/src/views/repository/RepositoryListView.vue`
- Modify: `playwright-platform-web/src/views/scene/SceneListView.vue`
- Modify: `playwright-platform-web/src/views/scene/ScheduleEventListView.vue`
- Modify: `playwright-platform-web/src/views/task/TaskListView.vue`
- Modify: `playwright-platform-web/src/views/task/TaskDetailView.vue`
- Modify: existing unit tests for repository/scene/task/schedule event views and stores

- [ ] **Step 1: 写业务页空间路径与权限显隐的失败测试**

```ts
it('hides retry button for viewer role', async () => {
  authStore.setSpaceRole('VIEWER')
  const wrapper = mount(ScheduleEventListView)
  expect(wrapper.text()).not.toContain('重试')
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- tests/unit/schedule-event.store.test.ts tests/unit/schedule-event-view.test.ts`
Expected: FAIL because APIs and role checks still use global routes

- [ ] **Step 3: 改 API 路径为显式 `spaceId`**

```ts
get(`/spaces/${spaceId}/schedule-events`, { params })
post(`/spaces/${spaceId}/schedule-events/${eventId}/retry`, payload)
```

- [ ] **Step 4: 改页面跳转和权限显隐**

```vue
<el-button v-if="spaceStore.canOperateScheduleEvents" ...>重试</el-button>
```

- [ ] **Step 5: 跑前端关键单测**

Run: `npm test -- tests/unit/auth.store.test.ts tests/unit/space.store.test.ts tests/unit/login-view.test.ts tests/unit/sidebar-user-panel.test.ts tests/unit/schedule-event.store.test.ts tests/unit/schedule-event-view.test.ts`
Expected: PASS

- [ ] **Step 6: 跑前端构建**

Run: `npm run build`
Expected: PASS

- [ ] **Step 7: 提交**

```bash
git add playwright-platform-web/src/{api,stores,views} playwright-platform-web/tests/unit
git commit -m "feat: move frontend business pages into space context"
```

### Task 9: 后端回归验证与文档同步

**Files:**
- Modify: `docs/superpowers/specs/2026-07-02-minimal-auth-and-sidebar-user-design.md` (only if implementation required clarifications)
- Modify: `docs/superpowers/plans/2026-07-02-auth-space-and-sidebar-implementation.md` (mark notes if needed)

- [ ] **Step 1: 跑后端关键测试**

Run: `./mvnw -q -Dtest=AuthServiceTest,AuthControllerTest,SpaceServiceTest,SpaceAccessRequestServiceTest,SpaceControllerTest,SpaceAccessRequestControllerTest,RepositoryControllerTest,SceneControllerTest,TaskControllerTest,ScheduleEventControllerTest test`
Expected: PASS

- [ ] **Step 2: 跑前端关键测试**

Run: `npm test -- tests/unit/auth.store.test.ts tests/unit/space.store.test.ts tests/unit/login-view.test.ts tests/unit/sidebar-user-panel.test.ts tests/unit/schedule-event.store.test.ts tests/unit/schedule-event-view.test.ts`
Expected: PASS

- [ ] **Step 3: 跑前端构建**

Run: `npm run build`
Expected: PASS

- [ ] **Step 4: 检查需求覆盖**

```text
- 登录页 -> 已实现
- Home -> 已实现
- 空间创建 -> 已实现
- 无权限页 -> 已实现
- 申请审批 -> 已实现
- MinIO 头像 + 默认头像 -> 已实现
- 业务空间隔离 -> 已实现
```

- [ ] **Step 5: 提交**

```bash
git add .
git commit -m "chore: verify auth and space rollout"
```

## Self-Review

### Spec coverage

- 登录态、RSA 登录、Cookie Session：Task 1-2、Task 6
- 空间数据模型、成员关系、申请审批：Task 3-4
- 业务资源强归属空间：Task 3、Task 5、Task 8
- Home 页、空间切换器、侧边栏用户区：Task 6-7
- 业务页面权限收口：Task 5、Task 8
- MinIO 头像与默认头像：Task 1、Task 7
- 验证与验收：Task 9

### Placeholder scan

- 计划中没有 `TODO`、`TBD`、`later`
- 每个任务都给出文件路径、最小代码轮廓、运行命令和预期结果

### Type consistency

- 后端用户字段统一使用 `userId/username/nickname/avatarObjectKey/lastSpaceId`
- 空间角色统一使用 `ADMIN/OPERATOR/VIEWER`
- 前端路由统一使用 `/spaces/:spaceId/...`
