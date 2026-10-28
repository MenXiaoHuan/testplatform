# PRD-01 · 平台设计（用户体系 · 权限 · 多租户）

> 版本 v1.0 · 2026-10-28 · **对应现有代码模块**：`auth` / `space` / `common`（ApiResponse / SensitiveDataRedactor）
> 
> 本 PRD 覆盖平台级的基础设施：登录注册、会话管理、RBAC 权限体系、Space 多租户隔离、审批流程、审计日志。**所有业务模块（UI 自动化、AI 助手、大模型评测）都依赖这套底层设计**。

---

## 一、需求目标

### 1.1 为什么做

UI 自动化测试平台不能是"单用户裸奔"的本地工具——多个测试人员需要**协作**、不同团队的测试数据需要**隔离**、仓库凭据不能让无关人员看到。平台需要一套完整的用户与权限体系来解决：

| 问题 | 平台解决什么 |
|------|-------------|
| 谁能访问平台 | 登录注册 + Cookie 会话 |
| 不同团队的数据不能混在一起 | Space 多租户隔离（所有业务表都带 space_id） |
| 同一 Space 内谁能做什么 | RBAC 三角色（ADMIN / DEVELOPER / OBSERVER） |
| 外部人员要加入 Space 怎么办 | 审批流程（申请 → Space Admin 审批 → 加入） |
| 用户操作历史不可追溯 | PlatformAuditLog 审计日志 |

### 1.2 范围边界

| 做 | 不做 |
|----|------|
| 邮箱 + 密码登录 | SSO（Google / GitHub / GitLab OAuth）、LDAP |
| 自助注册（可配置开关） | 邀请码注册、企业域名自动加入 Space |
| 三角色 RBAC（Space 内角色，非全局角色） | 全局 SuperAdmin / 跨 Space 角色继承 |
| Space 加入审批 | 自动邀请链接（无审批） |
| 用户昵称 + 头像 | 第三方头像（Gravatar）、昵称改 color |
| 密码加盐哈希（BCrypt） | 明文存储、可逆加密 |
| 审计日志（操作可追溯） | 细粒度字段级变更追踪 |
| Session Cookie + CSRF Token | JWT（无状态） |
| 默认 Space 自动创建（新用户） | 手动引导 Space 创建向导 |

### 1.3 成功标准

| 指标 | 目标 |
|------|------|
| 注册到首次执行 | 新用户注册 → 自动创建默认 Space → 首页能看到默认 Space → 能创建仓库 → 10 分钟内完成首次执行 |
| 权限隔离 | Space A 的成员访问 Space B 的任何业务数据 → 403；无例外 |
| 审批时效 | PENDING 申请平均处理时间 ≤ 24h（目标值）；审批后 1s 内成员可见 |
| 安全基线 | 密码不以任何形式存回 DB；日志中不出现密码明文；Cookie 使用 HttpOnly + Secure + SameSite |
| 并发稳定 | 100 个用户同时在线登录/登出/切换 Space 无 Session 冲突 |

---

## 二、核心对象模型

### 2.1 对象关系

```
PlatformUser（平台用户，全局唯一）
  │
  ├── 1:1 → UserSession（登录会话，可多端同时登录）
  │
  └── N:N → SpaceMember（通过 Space 关联，含角色）
                │
                ▼
           Space（多租户隔离边界，所有业务数据挂在这里）
                │
                └── SpaceAccessRequest（申请加入 / 升级角色）

Space（Space）
  ├── Repository（仓库）
  ├── Scene（场景）
  ├── Task（任务）
  └── 未来：EvaluationTarget / EvaluationDataset / PromptVersion ...
```

### 2.2 PlatformUser（平台用户）

| 字段 | 类型 | 说明 |
|------|------|------|
| `username` | varchar(128) | 登录用户名，**全局唯一** |
| `nickname` | varchar(128) | 显示昵称，**全局唯一**，最长 10 字符 |
| `password_hash` | varchar(255) | BCrypt 加盐哈希；**API 永不返回** |
| `avatar_object_key` | varchar(512) | MinIO 对象 key（头像文件） |
| `last_space_id` | bigint | 用户最后一次在的 Space，用于登录后自动跳转 |
| `enabled` | tinyint | 是否启用；禁用后无法登录 |

### 2.3 UserSession（登录会话）

| 字段 | 类型 | 说明 |
|------|------|------|
| `session_id` | varchar(64) | 随机生成的会话 token（HTTP Cookie 里存这个） |
| `user_id` | bigint | 关联的用户 |
| `expires_at` | datetime | 会话过期时间 |

**会话特点**：
- 支持同一用户多端同时登录（多条 UserSession 记录）
- 登出只删当前 session_id 的记录，不影响其他端
- 过期后 AuthSessionFilter 自动拦截，返回 401

### 2.4 Space（空间 = 多租户边界）

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | varchar(128) | Space 名称（建议命名："{nickname}的测试空间"） |
| `description` | varchar(512) | Space 描述 |
| `owner_user_id` | bigint | 创建者 |
| 所有业务表 | 均带 `space_id` 外键 | 强制数据隔离 |

### 2.5 SpaceMember（Space 成员 + RBAC 角色）

| 字段 | 类型 | 说明 |
|------|------|------|
| `space_id` | bigint | 所属 Space |
| `user_id` | bigint | 成员 |
| `role` | varchar(32) | **ADMIN** / **DEVELOPER** / **OBSERVER** |
| `status` | varchar(32) | ACTIVE / PENDING（审批中） / REJECTED |

**唯一约束**：`UNIQUE(space_id, user_id)`

### 2.6 SpaceAccessRequest（申请审批）

| 字段 | 类型 | 说明 |
|------|------|------|
| `space_id` | bigint | 目标 Space |
| `applicant_user_id` | bigint | 申请人 |
| `requested_role` | varchar(32) | 申请的角色（ADMIN / DEVELOPER / OBSERVER） |
| `reason` | varchar(512) | 申请理由 |
| `status` | varchar(32) | **PENDING** / **APPROVED** / **REJECTED** |
| `review_comment` | varchar(512) | 审批意见 |
| `reviewed_by` | bigint | 审批人（必须是该 Space 的 ADMIN） |

**唯一约束**：同一用户对同一 Space 同一角色不能有两条 PENDING 申请

---

## 三、RBAC 权限矩阵

### 3.1 三角色权限表

| 操作 | ADMIN | DEVELOPER | OBSERVER |
|------|-------|-----------|----------|
| 查看仓库列表 / 详情 | ✅ | ✅ | ✅ |
| 创建 / 编辑 / 删除仓库 | ✅ | ✅ | ❌ |
| 查看场景列表 / 详情 | ✅ | ✅ | ✅ |
| 创建 / 编辑 / 删除场景 | ✅ | ✅ | ❌ |
| 执行场景（手动触发） | ✅ | ✅ | ❌ |
| 取消正在执行的任务 | ✅ | ✅ | ❌ |
| 查看任务列表 / 详情 / 用例 / 产物 / 日志 | ✅ | ✅ | ✅ |
| 查看调度事件 / 重试 | ✅ | ✅ | ✅（重试需 DEVELOPER+） |
| 触发 Agent 对话 | ✅ | ✅ | ✅ |
| 空间成员管理（邀请/移除） | ✅ | ❌ | ❌ |
| 空间申请审批 | ✅ | ❌ | ❌ |
| **侧边栏「空间审批」菜单** | ✅ 可见 | ❌ | ❌ |
| **侧边栏菜单顺序** | 仓库 → 场景 → **空间审批** → 日志追踪 | 仓库 → 场景 → 日志追踪 | 仓库 → 场景 → 日志追踪 |

### 3.2 后端鉴权机制

所有业务 API 的前置校验链路：

```
AuthSessionFilter（全局 Filter）
  │
  ├── 从 Cookie 读取 session_id
  ├── 查 user_session 表（检查 expires_at）
  ├── 注入当前 User 到 ctx.state
  │
  ▼
SpaceAuthorizationService（Service 层校验）
  │
  ├── 所有 API 路径带 spaceId → 校验当前用户是否在该 Space
  ├── 查 space_member 表
  ├── 拿到 role → 根据权限矩阵放行 / 拒绝
  │
  └── 非成员 → 403 Forbidden（ApiResponseEnvelope { code: "FORBIDDEN", message: "..."}）
```

### 3.3 侧边栏菜单动态控制（前端）

前端通过 `space-permissions.ts` 判断当前角色决定菜单显示：
- **ADMIN**：仓库管理 → 场景管理 → **空间审批** → 日志追踪
- **DEVELOPER / OBSERVER**：仓库管理 → 场景管理 → 日志追踪（空间审批隐藏）

---

## 四、登录注册流程

### 4.1 登录

```
POST /api/auth/login
  Body: { username, password }

前端流程：
  1. 先 GET /api/auth/public-key → 拿 RSA 公钥
  2. 前端用公钥加密 password（防止中间人）
  3. POST /api/auth/login
  4. 后端 BCrypt 对比 password_hash
  5. 成功 → 生成 session_id → 写入 user_session 表
  6. 返回 Set-Cookie: PLATFORM_SESSION={session_id}; HttpOnly; Secure; SameSite=Lax
     + 登录成功的用户资料（含 last_space_id）
  7. 前端拿 last_space_id → 自动跳转上次 Space（或首页广场）
```

**安全机制**：
- 密码传输：RSA 公钥加密
- 密码存储：BCrypt 加盐哈希，不可逆
- Cookie：HttpOnly + Secure + SameSite=Lax

### 4.2 自助注册

```
POST /api/auth/register
  Body: { username, password, nickname? }

前置条件：PLATFORM_REGISTRATION_ENABLED=true（默认 false）

后端流程：
  1. 校验 username 全局唯一
  2. 校验 nickname 全局唯一（可选，不传则生成"未命名用户"）
  3. BCrypt 哈希密码
  4. 创建 platform_user 记录
  5. 【自动】创建默认 Space（name = "{nickname}的测试空间"）
  6. 【自动】在 space_member 表中把注册人加为 ADMIN
  7. 【自动】设置 last_space_id = 默认 Space
  8. 自动登录（生成 session + 返回 Set-Cookie）
```

**注册开关**：
- 本地开发：在 `.env` 里设 `PLATFORM_REGISTRATION_ENABLED=true`
- 生产环境：**必须**设为 `false`，防止公网任何人注册后获得执行命令能力
- 生产新用户入驻：由 Space ADMIN 在「空间审批」页发起

### 4.3 登出

```
POST /api/auth/logout
  Cookie: PLATFORM_SESSION={session_id}

后端流程：
  1. 从 Cookie 解析 session_id
  2. 删除 user_session 表对应记录
  3. 返回 Set-Cookie: PLATFORM_SESSION=; Max-Age=0
```

### 4.4 会话过期与恢复

```
AuthSessionFilter 每次请求执行：
  session_id → 查 user_session 表
    ├── 不存在 / 已过期 → 返回 401 Unauthorized
    └── 存在且未过期 → 注入当前 User → 继续请求

前端处理 401：
  清除 Pinia 中的 auth 状态 → 跳转到 /login → 保留 redirect 参数 → 登录后自动跳回
```

---

## 五、Space 协作流程

### 5.1 空间广场

```
GET /api/spaces/plaza

返回：当前用户可访问的所有 Space
  ├── 已是成员 → 显示角色（ADMIN / DEVELOPER / OBSERVER）
  ├── 有 PENDING 申请 → 显示"申请中"状态
  └── 完全未申请 → 显示"申请加入"按钮
```

### 5.2 申请加入 Space

```
POST /api/spaces/{spaceId}/access-requests
  Body: { requestedRole: "DEVELOPER", reason: "我们组要用这个 Space 跑 UI 自动化" }

后端流程：
  1. 校验 Space 存在
  2. 校验当前用户不是该 Space 成员（已是就报错）
  3. 校验当前用户对该 Space 没有 PENDING 申请（防重复）
  4. 创建 space_access_request 记录，status=PENDING
```

### 5.3 审批流程（只有 Space ADMIN 能操作）

```
GET /api/spaces/{spaceId}/access-requests
  → 返回所有 PENDING 申请列表

POST /api/spaces/{spaceId}/access-requests/{requestId}/approve
  Body: { role: "DEVELOPER" }（审批人可以调整角色）
  后端：
    1. 把 space_access_request.status 改成 APPROVED
    2. 【自动】在 space_member 表中把申请人加为指定角色

POST /api/spaces/{spaceId}/access-requests/{requestId}/reject
  Body: { reviewComment: "暂不需要" }
  后端：
    1. 把 space_access_request.status 改成 REJECTED
    2. 写 review_comment
```

### 5.4 Space 切换

```
前端：SpaceSwitcher 组件
  ├── 列出当前用户所有 ACTIVE Space
  ├── 点击切换 → Pinia space store 更新 currentSpace
  ├── 后端：所有业务 API 都带 spaceId 参数（路由层级强制）
  └── 后端：AuthSessionFilter 无状态，SpaceAuthorizationService 每次校验
```

---

## 六、API 接口总览

### 6.1 认证

| 接口 | 方法 | 权限 | 输入 | 输出 |
|------|------|------|------|------|
| 公钥 | `GET /api/auth/public-key` | 匿名 | — | { publicKey } |
| 登录 | `POST /api/auth/login` | 匿名 | username, password(RSA 加密), nickname | User + Set-Cookie |
| 注册 | `POST /api/auth/register` | 匿名（需 REGISTRATION_ENABLED） | username, password(RSA 加密), nickname | User + Set-Cookie |
| 当前用户 | `GET /api/auth/me` | 已登录 | — | User |
| 更新资料 | `PUT /api/auth/profile` | 已登录 | nickname, avatar? | User |
| 上传头像 | `POST /api/auth/avatar` | 已登录 | multipart/form-data | avatarUrl |
| 登出 | `POST /api/auth/logout` | 已登录 | — | void |

### 6.2 Space

| 接口 | 方法 | 权限 | 输入 | 输出 |
|------|------|------|------|------|
| 我的 Space 列表 | `GET /api/spaces` | 已登录 | — | SpaceMember[] |
| 空间广场 | `GET /api/spaces/plaza` | 已登录 | — | Space 列表 + 成员状态 |
| 创建 Space | `POST /api/spaces` | 已登录 | name, description | Space（自动把创建人加为 ADMIN） |
| 申请加入 | `POST /api/spaces/{spaceId}/access-requests` | 已登录 | requestedRole, reason | SpaceAccessRequest |
| 申请列表 | `GET /api/spaces/{spaceId}/access-requests` | Space ADMIN | — | SpaceAccessRequest[] |
| 审批通过 | `POST /api/spaces/{spaceId}/access-requests/{requestId}/approve` | Space ADMIN | role | void |
| 审批拒绝 | `POST /api/spaces/{spaceId}/access-requests/{requestId}/reject` | Space ADMIN | reviewComment | void |

---

## 七、技术约束

### C1 所有业务数据必须带 space_id

业务表（test_repository / scene / task / case_result / artifact / schedule_event / **未来的 eval_target / eval_run / eval_prompt_version 等**）都必须包含 `space_id` 外键。跨 Space 查询后端直接 403。

### C2 密码不可逆

- 密码仅存 BCrypt 加盐哈希
- API 不返回 password_hash、不返回明文、不回显
- 日志中经过 SensitiveDataRedactor，敏感字段替换为 `***`

### C3 会话在服务端

- 登录态存数据库（user_session 表），不是 JWT
- Cookie HttpOnly（前端 JS 不可读）
- 登出直接删数据库记录

### C4 权限校验在后端，不是前端

- 前端通过侧边栏菜单隐藏不代表后端放行
- SpaceAuthorizationService 在 Service 层二次校验
- 即使前端绕过菜单直接调 API，后端也会拦截

### C5 新用户自动入驻

注册后**必须**自动创建默认 Space（`{nickname}的测试空间`），并把自己加为 ADMIN。**新用户必须完成昵称设置和默认 Space 创建后才能访问平台功能**。

### C6 昵称约束

- 全局唯一（`uk_platform_user_nickname`）
- 最长 10 字符
- 为空时默认"未命名用户"

### C7 注册可配置关闭

生产部署时**必须**设 `PLATFORM_REGISTRATION_ENABLED=false`。新用户通过 Space ADMIN 审批入驻。

### C8 Session 多端支持

同一用户可以在多设备/多浏览器同时登录，每端独立 session_id，登出只影响当前端。

---

## 八、运行方式

### 8.1 开发环境

```bash
# 启动所有服务（含 Flyway 自动建表）
docker compose up -d --build

# MySQL 中 V5 迁移创建 platform_user / user_session
# 初始化 admin 账号（密码：admin）
```

### 8.2 生产环境配置要点

```env
# 1. 必须关闭自助注册
PLATFORM_REGISTRATION_ENABLED=false

# 2. MySQL、Redis 只在 Docker 内部网络暴露
#    不要在 docker-compose.prod.yml 中 publish 这些端口到宿主机

# 3. MinIO Console 限制来源 IP
#    Nginx location /minio-console/ 加 allow/deny

# 4. 推荐前后端分域名
#    前端：https://test-platform.example.com
#    后端 API：https://api.test-platform.example.com
#    （同域下 Cookie 跨域问题少）
```

---

## 九、验收方式

| 验收项 | 说明 |
|--------|------|
| AC1 注册入驻完整流程 | 新用户注册 → 自动创建 "{昵称}的测试空间" → 自动成为 ADMIN → 登录后首页直接看到默认 Space |
| AC2 登录安全 | 抓包 → password 字段是 RSA 密文 → 后端日志里没有 password → DB 中存的是 BCrypt 哈希 |
| AC3 Session 多端 | Chrome 登录 → Firefox 登录同一账号 → 两端都能操作 → Chrome 登出 → Firefox 仍在登录态 |
| AC4 跨 Space 隔离 | Space A 的 DEVELOPER → 手动把 URL 改成 Space B 的仓库列表 → 返回 403 FORBIDDEN |
| AC5 RBAC 权限矩阵 | OBSERVER 想执行场景 → 后端 403；DEVELOPER 想删仓库 → 允许；DEVELOPER 想看空间审批菜单 → 前端隐藏 + 后端 403 |
| AC6 审批流程 | OBSERVER 申请升级 DEVELOPER → Space ADMIN 看到申请 → 审批通过 → 角色变成 DEVELOPER → 权限立即生效 |
| AC7 审批拒绝 + 重新申请 | 申请 DEVELOPER 被拒 → 修改理由 → 重新申请（同一 Space 同一角色）→ 不被重复申请约束拦截 |
| AC8 注册开关 | 生产环境 REGISTRATION_ENABLED=false → 新用户 POST /auth/register → 返回 403 REGISTRATION_DISABLED |
| AC9 昵称唯一 | 尝试注册昵称 "Alice"（已被占用）→ 返回 409 NICKNAME_TAKEN |
| AC10 昵称长度 | 尝试注册 20 字符昵称 → 返回 400 NICKNAME_TOO_LONG |
| AC11 审计日志 | 执行仓库创建/更新/删除 → platform_audit_log 表有记录，含 entity_type / entity_id / action / operator_name / detail_json |
| AC12 Cookie 安全 | DevTools 检查 Cookie → PLATFORM_SESSION 的 SameSite=Lax，HttpOnly=true，Secure=true（生产 HTTPS 下） |

---

## 十、测试方式

### 10.1 单元测试

| 模块 | 覆盖重点 |
|------|----------|
| AuthServiceImpl | 登录/注册/会话/密码 BCrypt 哈希/验证 |
| SpaceAuthorizationServiceImpl | 三角色权限矩阵 / 跨 Space 拦截 |
| AuthSessionFilter | session 过期拦截 / 注入当前用户 |
| 默认 Space 创建 | 注册后自动创建 Space + 自动成为 ADMIN |
| Session 多端 | 多 session_id 独立管理 |

### 10.2 集成测试

| 场景 | 覆盖重点 |
|------|----------|
| 注册 → 登录 → 创建仓库完整链路 | 端到端用户入驻流程 |
| 审批流程端到端 | 申请 → 审批 → 角色变更 → 权限生效 |
| 跨 Space 拦截 | 非成员访问任意业务数据 |
| 权限矩阵边界 | 每个角色的每个权限点 |
| Session 过期 | 手动设置 expire → 过期后 401 |

### 10.3 手动测试 Checklist

- [ ] 注册新用户 → 自动创建默认 Space
- [ ] 同一账号 Chrome + Firefox 同时登录 → 分别操作 → 独立登出
- [ ] 用 OBSERVER 账号执行场景 → 403
- [ ] 用 DEVELOPER 账号执行场景 → 成功
- [ ] DEVELOPER 想删仓库 → 失败（只有 ADMIN 能删）
- [ ] 跨 Space 访问 → 403
- [ ] 创建 Space → 自动成为 ADMIN
- [ ] 审批申请 → 申请列表能看到 → 通过后角色变更
- [ ] 密码抓包看加密 → DB 看哈希
- [ ] 生产环境关掉注册 → 新用户注册 403

### 10.4 安全专项

| 测试项 | 说明 |
|--------|------|
| 密码强度 | BCrypt cost factor ≥ 10 |
| Cookie HttpOnly | 前端 JS `document.cookie` 读不到 PLATFORM_SESSION |
| Session 固定攻击 | 登录成功后 session_id 变化（防止攻击者预先设置） |
| SQL 注入 | username / nickname 带 SQL 注入 payload → 被参数化查询拦截 |
| 越权访问 | 手动改 URL 把 spaceId 改成其他 Space → 403 |

---

## 十一、已知限制与后续迭代

| 当前限制 | 后续规划 |
|----------|----------|
| 单一密码登录 | 增加 SSO（OAuth2 / OIDC）对接企业 IdP |
| 无密码策略 | 增加密码复杂度要求（长度 / 大小写 / 特殊字符）+ 密码过期强制重置 |
| 手动审批 | 增加"邀请链接 + 自动加入"模式；支持批量审批 |
| 角色少 | 增加 BUILDER 角色（只能跑测试，不能改配置）、READONLY_ADMIN（只能看，不能审批） |
| 无全局 Admin | 所有 Space ADMIN 平等 → 后续增加全局 SuperAdmin |
| Session 存 DB | 后续迁移到 Redis（当前并发量不大，DB 足够；大用户量时 Session 会成为瓶颈） |
| 审计日志无前端页面 | 后续增加「操作日志」页面，支持按用户/操作类型/时间筛选 |
| 昵称最长 10 字符 | 国际化场景可能需要更长，需权衡 UI 展示 |
