# 平台最小化认证、空间体系、权限审批与侧边栏用户区设计

## 1. 背景

当前测试平台已经具备仓库、场景、任务、调度事件等核心业务能力，但仍处于“单全局工作区”阶段。系统缺少三个关键能力：

- 没有真实登录态，所有业务页默认可访问。
- 没有统一权限边界，写操作与操作人审计不可靠。
- 没有“空间”概念，所有资源默认处于全局域中，无法支持成员隔离、权限申请和审批。

用户已经明确提出平台演进方向：

- 先有登录，再有空间。
- 新用户登录后先进入 `Home` 页。
- 用户可创建空间，创建者自动成为该空间管理员。
- 非成员访问空间时，应看到无权限提示并可发起申请。
- 申请时必须填写申请原因，并选择申请角色：管理员 / 访问者 / 运维。
- 审批者可以看到申请人信息、申请角色、申请原因，并进行同意或拒绝。
- 侧边栏左下角需要展示带头像和昵称的用户区，头像来源于 MinIO，未配置时回退默认头像。

本次设计目标不是引入完整企业级 IAM 或复杂 RBAC，而是在现有项目架构上补一个“能运行、能隔离、能审批、后续能平滑升级”的最小闭环方案。

## 2. 已确认范围

本设计基于已确认的用户要求：

- 采用前后端最小闭环，不做纯前端伪登录。
- 登录接口参数中的密码不能以明文直接出现。
- 登录态采用滑动 14 天有效期。
- 换设备访问需要重新登录。
- 头像文件存储在 MinIO。
- 系统提供默认头像兜底。
- 平台引入一级业务概念“空间”。
- 现有仓库、场景、任务、调度事件全部归属于某个空间。
- 登录后先进入 `Home`，再进入具体空间。
- 空间内成员关系与权限审批是本次范围的一部分。

## 3. 目标

- 为平台增加最小可用的登录态、会话恢复与登出能力。
- 把所有业务资源收口到空间维度，形成空间隔离。
- 支持空间创建、空间访问申请、审批、成员关系和角色控制。
- 把现有仓库、场景、任务、调度事件页面改造成“当前空间上下文”工作模式。
- 用侧边栏承载空间切换与用户身份展示。
- 把操作人、审计与当前登录用户和当前空间角色绑定。
- 为未来接入真实用户表、Redis Session、统一身份源保留平滑替换点。

## 4. 非目标

- 本次不引入 Spring Security、OAuth、OIDC 或企业 SSO。
- 本次不做复杂 RBAC 权限编排、权限管理后台、细粒度资源授权。
- 本次不做用户注册、找回密码、修改密码、短信或邮箱验证。
- 本次不做头像上传、裁剪、个人资料编辑。
- 本次不做空间删除。
- 本次不做多端设备管理、登录设备列表、强制下线。
- 本次不做多实例共享 Session；先接受单实例内存 Session。

## 5. 总体方案

整体方案采用以下组合：

- 内置用户目录
- RSA 登录口令加密提交
- 服务端 Session
- `HttpOnly Cookie`
- 滑动 14 天续期
- `Home -> 空间 -> 业务页面` 的三级访问模型
- 空间成员关系与空间角色权限
- 空间访问申请与审批流

系统权限分为两层：

1. 认证层：用户是否已登录。
2. 空间层：该用户在当前空间是否具有对应角色。

登录只证明“你是谁”，真正的业务操作权限由“你在当前空间里是什么角色”决定。

## 6. 认证模型

### 6.1 认证核心原则

- 登录时前端不直接提交明文密码，而是先获取后端公钥，对密码加密后提交。
- 后端解密后使用密码哈希校验，不存储明文密码。
- 登录成功后由后端创建服务端 Session，并通过 `HttpOnly Cookie` 持久化会话。
- 前端不在 `localStorage` 中存储认证 token。
- 只要 Session 仍有效，每次请求都刷新到“当前时间 + 14 天”，形成滑动过期。
- 换设备无 Cookie，因此必须重新登录。

### 6.2 为什么采用 Cookie Session

当前前端所有请求都通过同域 `/api` 访问后端，开发态由 Vite 代理，生产态由 Nginx 反代。这个拓扑最适合采用 Cookie Session：

- 接入成本低，不需要为每个请求显式拼接 `Authorization`
- `HttpOnly Cookie` 比前端持久化 token 更适合当前最小实现
- 滑动续期与“换设备重新登录”天然匹配

## 7. 登录口令加密策略

### 7.1 目标

满足“登录接口参数中不直接出现明文密码”的要求。

### 7.2 方案

- 仅对“登录提交”这一跳增加口令加密。
- 后端提供 RSA 公钥。
- 前端在登录页中把原始密码加密为 Base64 密文后提交。
- 登录接口只接收 `encryptedPassword`，不接收明文 `password` 字段。

### 7.3 安全边界

- 该方案解决“接口载荷中不直接出现明文密码”的要求。
- 正式环境仍然必须依赖 HTTPS/TLS 保护整体传输链路。
- 后端日志、异常、审计、数据库中都不得输出或存储明文密码。

## 8. 用户模型与头像策略

### 8.1 内置用户模型

后端维护一份最小内置用户目录。每个用户至少包含：

- `id`
- `username`
- `nickname`
- `passwordHash`
- `avatarObjectKey`
- `enabled`

说明：

- `username` 用于登录和唯一标识。
- `nickname` 用于侧边栏和审批页展示。
- `passwordHash` 使用 `bcrypt`。
- `avatarObjectKey` 指向 MinIO 中头像对象。

### 8.2 头像与默认头像

- 用户头像文件存储在 MinIO。
- 后端用户配置中保存 `avatarObjectKey`。
- 返回用户信息时，后端把 `avatarObjectKey` 转换为可访问 URL。
- 当前端头像加载失败、对象不存在或用户未配置头像时，统一回退前端内置默认头像。

### 8.3 本次限制

- 不做头像上传、编辑、裁剪。
- 默认头像作为前端静态资源内置，不依赖 MinIO 可用性。

## 9. 空间模型

空间是一级业务概念。现有所有核心资源都归属于某个空间。

### 9.1 为什么要把所有资源挂到空间

如果空间只是入口概念，而资源仍然全局共享，则以下能力无法真正成立：

- 访问无权限空间时禁止查看其数据
- 空间角色决定写权限
- 空间维度审批与成员治理
- 跨空间资源隔离

因此本设计明确采用“强空间归属”：

- 仓库属于空间
- 场景属于空间
- 任务属于空间
- 调度事件属于空间

### 9.2 空间角色

空间内角色使用三档：

- `ADMIN`
- `OPERATOR`
- `VIEWER`

语义如下：

#### `ADMIN`

- 管理空间成员与审批
- 管理空间内仓库和场景
- 继承运维和访问者能力

#### `OPERATOR`

- 查看空间内资源
- 取消任务
- 重试调度事件

#### `VIEWER`

- 只读访问空间内资源

### 9.3 空间规则

- 创建空间的用户自动成为首个 `ADMIN`
- 空间必须始终至少存在一个 `ACTIVE ADMIN`
- 已是成员的用户不能重复申请加入同一空间
- 已存在 `PENDING` 申请时不能重复提交申请

## 10. 业务资源与空间归属

以下现有资源必须增加 `spaceId`：

- 仓库
- 场景
- 任务
- 调度事件

### 10.1 直接落 `spaceId` 的原因

虽然任务可以通过场景间接推导空间，调度事件也可以通过场景推导空间，但仍建议直接落 `spaceId`：

- 任务列表和任务详情会频繁做空间级过滤
- 调度事件需要直接按空间筛选与鉴权
- 减少查询链路复杂度
- 降低遗漏空间过滤的风险

### 10.2 资源创建规则

- 在当前空间下新建仓库或场景时，后端自动写入当前 `spaceId`
- 前端不允许用户手动选择归属空间
- 任务和调度事件在创建时自动继承对应空间的 `spaceId`

## 11. 登录与会话流程

### 11.1 登录流程

1. 前端进入登录页。
2. 调用 `GET /api/auth/public-key` 获取登录公钥。
3. 前端使用公钥加密用户密码。
4. 调用 `POST /api/auth/login`，提交 `username` 与 `encryptedPassword`。
5. 后端解密密码并校验 `passwordHash`。
6. 校验通过后创建 Session。
7. 后端下发 `HttpOnly Cookie`。
8. 后端返回当前用户基本信息。
9. 前端跳转到 `Home` 页，而不是直接跳业务页。

### 11.2 会话恢复流程

1. 应用启动时调用 `GET /api/auth/me`。
2. 若 Cookie 对应 Session 有效，则返回当前用户。
3. 后端刷新 Session 过期时间为“当前时间 + 14 天”。
4. 前端恢复登录态。
5. 若用户有最近访问空间且仍有权限，可从 `Home` 引导回该空间；否则停留在 `Home`。

### 11.3 会话失效与登出

- Session 不存在、过期或 Cookie 无效时，后端返回 `401`
- 前端收到 `401` 后清空登录态并跳转 `/login`
- 调用 `POST /api/auth/logout` 时，后端删除 Session 并清 Cookie

## 12. 主要用户流程

### 12.1 新用户首次进入

1. 登录成功后进入 `Home`
2. 若当前用户没有任何可访问空间，则展示空态
3. 空态主操作为“创建第一个空间”
4. 创建完成后自动成为该空间 `ADMIN`
5. 跳转到该空间的默认落地页

### 12.2 普通用户进入已有空间

1. 登录后进入 `Home`
2. 查看可访问空间列表
3. 点击某个空间进入该空间的业务页面

### 12.3 无权限访问空间

1. 用户直接访问某个空间链接
2. 后端或前端判断其不是该空间成员
3. 页面显示“暂无权限访问该空间”
4. 提供“申请加入”入口
5. 若已存在待审批申请，则显示“申请已提交，等待审批”

### 12.4 空间申请与审批

申请人提交：

- 申请角色：管理员 / 运维 / 访问者
- 申请原因：必填

审批人看到：

- 申请人昵称
- 申请人账号
- 申请角色
- 申请原因
- 申请时间
- 当前状态

审批动作：

- 同意
- 拒绝

处理结果：

- 同意：新增或恢复成员关系，申请状态变为 `APPROVED`
- 拒绝：申请状态变为 `REJECTED`

## 13. 前端设计

### 13.1 路由结构

前端路由分三层：

#### 认证层

- `/login`

#### 平台入口层

- `/home`

#### 空间工作层

- `/spaces/:spaceId/repos`
- `/spaces/:spaceId/scenes`
- `/spaces/:spaceId/tasks`
- `/spaces/:spaceId/schedule-events`
- `/spaces/:spaceId/settings`
- `/spaces/:spaceId/access-requests`

说明：

- 业务路由必须显式携带 `spaceId`
- 不继续使用全局 `/repos`、`/scenes` 作为长期形态

### 13.2 新增前端模块

建议新增：

- `src/types/auth.ts`
- `src/types/space.ts`
- `src/api/auth.ts`
- `src/api/space.ts`
- `src/stores/auth.ts`
- `src/stores/space.ts`
- `src/views/auth/LoginView.vue`
- `src/views/home/HomeView.vue`
- `src/views/space/SpaceNoAccessView.vue`
- `src/views/space/SpaceAccessRequestListView.vue`
- `src/views/space/SpaceSettingsView.vue`
- `src/components/layout/SidebarUserPanel.vue`
- `src/components/layout/SpaceSwitcher.vue`
- `src/utils/auth-crypto.ts`
- `src/assets/default-avatar.png`

### 13.3 `Home` 页

`Home` 页承担以下职责：

- 展示我可访问的空间列表
- 支持创建空间
- 展示我的申请记录
- 对空间管理员展示待审批申请摘要

### 13.4 侧边栏结构

引入空间后，侧边栏承载两个不同上下文：

1. 空间上下文
2. 用户上下文

建议布局：

- 顶部：空间切换器
- 中部：当前空间导航菜单
- 底部：用户区卡片

### 13.5 空间切换器

展示内容：

- 当前空间名称
- 当前空间角色
- 下拉箭头

点击后弹出：

- 我的空间列表
- 回到 `Home`
- 创建空间

切换空间后：

- 更新当前 `spaceId`
- 跳转到对应空间默认页，例如 `/spaces/{spaceId}/scenes`
- 重新拉取当前空间数据

### 13.6 用户区卡片

当前 `App.vue` 适合作为全局布局壳子，本次在侧边栏左下角增加固定用户区。

展示内容：

- 头像
- 昵称
- 右侧箭头

点击后弹出工具菜单。

本次真实落地菜单项：

- `设置`
- `切换账号`
- `退出登录`

可先保留 UI 壳子的菜单项：

- `收藏夹`
- `官网`
- `API 服务`
- `帮助与反馈`

### 13.7 页面级权限控制

#### 仓库页

- 仅 `ADMIN` 可新增、编辑、删除

#### 场景页

- 仅 `ADMIN` 可新增、编辑、复制、删除

#### 任务页

- `OPERATOR` 与 `ADMIN` 可取消任务

#### 调度事件页

- `OPERATOR` 与 `ADMIN` 可重试事件
- `VIEWER` 不显示或禁用重试按钮

#### 成员与审批页

- 仅 `ADMIN` 可见

## 14. 后端设计

### 14.1 认证模块

建议新增：

- `auth/controller/AuthController.java`
- `auth/service/AuthService.java`
- `auth/service/AuthServiceImpl.java`
- `auth/model/AuthUser.java`
- `auth/model/AuthSession.java`
- `auth/config/AuthProperties.java`
- `auth/filter/AuthSessionFilter.java`
- `auth/context/AuthContext.java`
- `auth/context/AuthContextHolder.java`
- `auth/crypto/AuthKeyProvider.java`

### 14.2 空间模块

建议新增：

- `space/controller/SpaceController.java`
- `space/controller/SpaceAccessRequestController.java`
- `space/service/SpaceService.java`
- `space/service/SpaceMemberService.java`
- `space/service/SpaceAccessRequestService.java`
- `space/mapper/SpaceMapper.java`
- `space/mapper/SpaceMemberMapper.java`
- `space/mapper/SpaceAccessRequestMapper.java`
- `space/model/SpaceEntity.java`
- `space/model/SpaceMemberEntity.java`
- `space/model/SpaceAccessRequestEntity.java`

### 14.3 Session 存储

服务端采用最小内存 Session 仓库：

- `ConcurrentHashMap<String, AuthSession>`

Session 至少保存：

- `userId`
- `username`
- `nickname`
- `avatarObjectKey`
- `lastSpaceId`
- `expiresAt`

### 14.4 认证过滤器职责

- 从 Cookie 读取 Session ID
- 查找 Session
- 校验过期
- 未过期则刷新到“当前时间 + 14 天”
- 写入 `AuthContextHolder`

匿名放行接口：

- 登录
- 登出
- 公钥

### 14.5 空间权限校验职责

每个空间级业务请求都需要完成两步判断：

1. 当前用户已登录
2. 当前用户在对应 `spaceId` 下具有足够角色

推荐新增通用的空间鉴权组件，避免在各业务 service 中重复手写。

## 15. 数据库设计

### 15.1 新增表 `space`

建议字段：

- `id`
- `name`
- `description`
- `owner_user_id`
- `created_by`
- `created_at`
- `updated_at`

### 15.2 新增表 `space_member`

建议字段：

- `id`
- `space_id`
- `user_id`
- `role`
- `status`
- `joined_at`
- `created_at`
- `updated_at`

建议枚举：

- `role`：`ADMIN`、`OPERATOR`、`VIEWER`
- `status`：`ACTIVE`、`LEFT`、`REMOVED`

建议约束：

- `uk_space_member(space_id, user_id)`

### 15.3 新增表 `space_access_request`

建议字段：

- `id`
- `space_id`
- `applicant_user_id`
- `requested_role`
- `reason`
- `status`
- `review_comment`
- `reviewed_by`
- `reviewed_at`
- `created_at`
- `updated_at`

建议枚举：

- `status`：`PENDING`、`APPROVED`、`REJECTED`、`CANCELLED`

建议索引：

- `(space_id, status, created_at)`
- `(applicant_user_id, status, created_at)`

### 15.4 现有表补 `space_id`

建议补到以下核心表：

- 仓库表
- 场景表
- 任务表
- 调度事件表

### 15.5 旧数据迁移

现有系统已存在全局资源，因此引入 `space_id` 时必须给出迁移策略：

- 建立一个系统默认空间
- 将现有旧资源迁移到该默认空间
- 初始化管理员成为默认空间的 `ADMIN`

## 16. 接口设计

### 16.1 认证接口

#### `GET /api/auth/public-key`

响应：

```json
{
  "algorithm": "RSA",
  "publicKeyPem": "-----BEGIN PUBLIC KEY-----..."
}
```

#### `POST /api/auth/login`

请求：

```json
{
  "username": "admin",
  "encryptedPassword": "base64..."
}
```

响应：

```json
{
  "id": 1,
  "username": "admin",
  "nickname": "平台管理员",
  "avatarUrl": "https://minio.example/avatar/admin.png",
  "lastSpaceId": 12
}
```

同时设置：

- `HttpOnly`
- `SameSite=Lax`
- `Path=/`
- `Max-Age=1209600`

#### `GET /api/auth/me`

未登录返回 `401`。

已登录返回：

```json
{
  "id": 2,
  "username": "alice",
  "nickname": "徐个愿",
  "avatarUrl": "https://minio.example/avatar/alice.png",
  "lastSpaceId": 8
}
```

#### `POST /api/auth/logout`

- 清理服务端 Session
- 清理 Cookie

### 16.2 空间接口

#### `GET /api/spaces`

- 返回当前用户可访问的空间列表

#### `POST /api/spaces`

- 创建空间
- 创建者自动成为该空间 `ADMIN`

#### `GET /api/spaces/{spaceId}`

- 获取空间详情

#### `GET /api/spaces/{spaceId}/members`

- 获取成员列表

#### `POST /api/spaces/{spaceId}/access-requests`

请求：

```json
{
  "requestedRole": "OPERATOR",
  "reason": "需要负责空间内任务运维和调度事件处理"
}
```

#### `GET /api/spaces/{spaceId}/access-requests`

- 获取该空间申请列表

#### `POST /api/spaces/{spaceId}/access-requests/{requestId}/approve`

- 同意申请

#### `POST /api/spaces/{spaceId}/access-requests/{requestId}/reject`

- 拒绝申请

#### `GET /api/my/access-requests`

- 查看我自己的申请记录

### 16.3 业务接口路径调整

建议把现有业务接口调整为显式带 `spaceId` 的路径：

- `/api/spaces/{spaceId}/repositories`
- `/api/spaces/{spaceId}/scenes`
- `/api/spaces/{spaceId}/tasks`
- `/api/spaces/{spaceId}/schedule-events`

采用显式路径而不是 Header 隐式传空间上下文，原因是：

- URL 自带上下文，更清晰
- 后端不容易漏校验
- 页面刷新和复制链接更自然

## 17. 现有业务接入方式

### 17.1 仓库与场景

- 查询时必须带当前 `spaceId`
- 创建时后端自动写入当前 `spaceId`
- 写操作仅 `ADMIN` 可执行

### 17.2 任务

- 查询和详情均按当前 `spaceId` 限制
- 取消任务需要 `OPERATOR` 或 `ADMIN`

### 17.3 调度事件

- 查询和重试均按当前 `spaceId` 限制
- 重试需要 `OPERATOR` 或 `ADMIN`
- 审计操作人来自登录上下文，不再透传 `anonymous`

## 18. 错误处理

### 18.1 前端

- `401`：清空登录态并跳登录页
- `403`：提示无权限
- 登录失败：统一提示“用户名或密码错误”
- 公钥获取失败：提示“登录服务暂不可用”
- 访问无权限空间：显示完整无权限页，而不是只有 toast

### 18.2 后端

- 用户不存在、密码错误、解密失败统一按登录失败处理
- Session 失效返回 `401`
- 非空间成员访问空间资源时返回 `403`
- 角色不足返回 `403`

## 19. 测试策略

### 19.1 前端测试

- `auth store`：登录、恢复、登出、`401` 清理
- `space store`：空间列表、当前空间切换、申请提交流程
- 路由守卫：未登录跳登录页，已登录进入 `Home`，空间路径带 `spaceId`
- `LoginView`：获取公钥、加密提交、登录失败提示
- `HomeView`：空间列表、创建空间、我的申请
- `SidebarUserPanel`：头像昵称展示、菜单展开、切换账号、退出登录
- `SpaceSwitcher`：切换当前空间
- 权限测试：不同空间角色下按钮显隐正确

### 19.2 后端测试

- `AuthService`：公钥、密码解密、登录成功失败、续期、登出
- `AuthSessionFilter`：匿名放行、Session 恢复、过期处理
- `SpaceService`：创建空间、创建者自动成为管理员
- `SpaceAccessRequestService`：提交申请、重复申请限制、审批通过/拒绝
- 控制器测试：未登录返回 `401`、非成员访问返回 `403`、角色不足返回 `403`
- 审计测试：操作人来自登录上下文

## 20. 验收标准

满足以下条件即可认为本次需求完成：

1. 用户未登录时不能访问业务页面。
2. 登录请求参数中不直接包含明文密码。
3. 后端只保存密码哈希，不保存明文密码。
4. 登录态使用 `HttpOnly Cookie`，并支持滑动 14 天续期。
5. 换设备访问时需要重新登录。
6. 登录后先进入 `Home`，而不是直接进入业务页。
7. 新用户无空间时可创建第一个空间，创建者自动成为管理员。
8. 仓库、场景、任务、调度事件全部归属于空间。
9. 非成员访问空间时会看到无权限页，并可提交申请。
10. 申请必须填写申请原因并选择申请角色。
11. 空间管理员可以查看申请并同意或拒绝。
12. 侧边栏左下角展示用户头像和昵称。
13. 用户头像优先使用 MinIO 头像，失败时回退默认头像。
14. 侧边栏可切换当前空间。
15. `ADMIN`、`OPERATOR`、`VIEWER` 三档空间角色生效。
16. 调度事件重试、任务取消、仓库/场景写操作都有空间级后端权限校验。
17. 审计日志操作人来自当前登录用户。

## 21. 风险与后续演进

### 21.1 本次已知限制

- Session 先使用内存存储，服务重启后会失效。
- 多实例部署时需替换为共享 Session 存储，例如 Redis。
- 用户资料编辑与头像上传不在本次范围。
- 空间删除不在本次范围。

### 21.2 后续可平滑替换点

- 内置用户目录替换为数据库用户表或统一身份源
- 内存 Session 替换为 Redis Session
- 空间角色替换为更完整 RBAC
- 头像只读替换为上传与编辑能力
- 审批流可增加通知、备注模板、历史审计详情
