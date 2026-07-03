# 自助注册与个人空间自动入驻 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为平台补齐自助注册、自动创建个人空间并自动登录的入驻闭环，同时修复空间审批页映射异常、统一默认头像兜底并收口错误提示。

**Architecture:** 后端在现有认证服务上扩展注册事务，在单事务内完成用户创建、空间创建、管理员成员关系、默认空间和 session 创建。前端在现有登录页上增加注册态，沿用 `auth/space` store 完成自动登录与跳转，并统一头像与错误展示策略。

**Tech Stack:** Spring Boot 3.5、MyBatis、Flyway、Vue 3、TypeScript、Pinia、Vue Router、Element Plus、Vitest、JUnit 5

---

## File Structure

- Modify: `playwright-platform-server/src/main/resources/db/migration/V5__add_platform_user_and_user_session.sql`
- Modify: `playwright-platform-server/src/main/resources/db/schema/SCHEMA_OVERVIEW.sql`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/auth/mapper/PlatformUserMapper.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/auth/service/AuthService.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/auth/service/AuthServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/auth/controller/AuthController.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/space/mapper/SpaceMapper.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/space/mapper/SpaceMemberMapper.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/space/mapper/SpaceAccessRequestMapper.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/space/service/SpaceAccessRequestServiceImpl.java`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/auth/AuthServiceTest.java`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/auth/AuthControllerTest.java`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/space/SpaceAccessRequestServiceTest.java`
- Modify: `playwright-platform-web/src/types/auth.ts`
- Modify: `playwright-platform-web/src/api/auth.ts`
- Modify: `playwright-platform-web/src/stores/auth.ts`
- Modify: `playwright-platform-web/src/stores/space.ts`
- Modify: `playwright-platform-web/src/views/auth/LoginView.vue`
- Modify: `playwright-platform-web/src/router/index.ts`
- Modify: `playwright-platform-web/src/views/space/SpaceAccessRequestListView.vue`
- Modify: `playwright-platform-web/src/utils/error.ts`
- Modify: `playwright-platform-web/src/components/layout/SidebarUserPanel.vue`
- Modify: `playwright-platform-web/src/views/home/HomeView.vue`
- Modify: `playwright-platform-web/tests/unit/auth.store.test.ts`
- Modify: `playwright-platform-web/tests/unit/login-view.test.ts`
- Modify: `playwright-platform-web/tests/unit/sidebar-user-panel.test.ts`

## Task 1: 修复审批映射异常并收口审批错误提示

- [ ] 为 `SpaceAccessRequestMapper.findProjectionBySpaceId(...)` 补一个失败测试，覆盖 projection 查询与 applicant 字段映射。
- [ ] 将 projection 查询改成构造器映射可稳定处理的方式，避免 record/setter 反射异常。
- [ ] 在 `SpaceAccessRequestServiceImpl.listBySpace(...)` 中把底层异常转换成稳定的业务异常，不让数据库栈透给前端。
- [ ] 在前端审批页保留现有 fallback 文案，确保接口失败只显示“审批列表加载失败，请刷新后重试”。
- [ ] 跑后端空间审批相关测试并确认通过。

## Task 2: 扩展用户、昵称、空间名唯一约束

- [ ] 为 `platform_user.nickname` 和 `space.name` 增加唯一约束的 migration/schema 更新。
- [ ] 为 `PlatformUserMapper`、`SpaceMapper` 增加按用户名、昵称、空间名查询的方法。
- [ ] 补后端测试，覆盖用户名重复、昵称重复、空间名冲突。
- [ ] 确认迁移文件与 SCHEMA_OVERVIEW 一致。

## Task 3: 实现后端注册事务

- [ ] 为 `AuthService.register(...)` 补失败测试，覆盖注册成功后自动创建空间、成员关系、默认空间、session。
- [ ] 在 `AuthService` 和 `AuthServiceImpl` 中增加注册请求模型和注册事务逻辑。
- [ ] 复用现有 bcrypt + RSA 解密链路，增加密码强度校验。
- [ ] 在单事务内完成：建用户、建空间、建 ADMIN 成员、更新 `last_space_id`、创建 session。
- [ ] 把重复用户名、昵称、空间名和弱密码映射成明确业务异常。
- [ ] 跑后端 auth 测试并确认通过。

## Task 4: 暴露注册接口并保持自动登录

- [ ] 为 `AuthController` 补 `/api/auth/register` 控制器测试。
- [ ] 新增 `RegisterRequest`，返回值与登录接口保持一致并设置 session cookie。
- [ ] 为新注册用户返回 `avatarObjectKey = null`，不在后端制造假头像。
- [ ] 跑 `AuthControllerTest` 并确认通过。

## Task 5: 实现前端注册页与自动跳转

- [ ] 为前端新增注册 payload/type、API 调用与 store 方法测试。
- [ ] 在 `LoginView.vue` 内切成登录/注册双态表单，不新增独立页面。
- [ ] 增加用户名、昵称、密码、确认密码校验与友好提示。
- [ ] 注册成功后写入 `authStore.user`，刷新 `spaceStore`，直接跳转到 `/spaces/{lastSpaceId}/repos`。
- [ ] 跑前端登录/注册相关单测并确认通过。

## Task 6: 统一头像兜底与错误消息映射

- [ ] 确认侧边栏、空间广场、空间审批三处都对空头像和图片加载失败走默认头像。
- [ ] 增加错误码到中文提示的映射，避免把后端内部异常原文显示给用户。
- [ ] 回归用户上传头像后从默认头像恢复显示的逻辑。
- [ ] 跑头像与错误处理相关单测并确认通过。

## Task 7: 完整验证

- [ ] 跑后端定向测试：认证、注册、审批列表。
- [ ] 跑前端定向测试：auth store、login/register、sidebar、space store。
- [ ] 跑前端类型检查。
- [ ] 总结未跑项和剩余风险，只基于已执行命令报告结果。
