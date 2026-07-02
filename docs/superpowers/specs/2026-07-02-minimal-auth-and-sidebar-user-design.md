# 最小化登录态、权限体系与侧边栏用户区设计

## 1. 背景

当前测试平台已经具备仓库、场景、任务、调度事件等核心业务能力，但还没有真实登录态和权限体系。现状存在几个直接问题：

- 所有页面默认可访问，没有统一的登录校验。
- 写操作没有最小权限边界，前后端都缺少明确约束。
- 调度事件重试等操作人仍使用硬编码值，例如 `anonymous`。
- 应用侧边栏没有承载当前用户信息的入口，无法自然容纳“切换账号”“退出登录”“设置”等登录相关操作。

本次需求目标不是引入完整企业级 SSO，而是在现有代码结构上补一层可工作的最小闭环认证能力，保证后续接入真实权限体系时不需要推倒重来。

## 2. 用户确认的范围

本设计基于以下已确认要求：

- 采用“前后端最小闭环”，不是纯前端伪登录。
- 登录口令在登录接口参数中不能直接以明文出现。
- 登录态采用滑动过期，连续使用时保持 14 天有效。
- 换设备访问时必须重新登录。
- 侧边栏左下角增加用户区，显示头像和昵称，点击后弹出工具栏。
- 用户头像文件存储在 MinIO。
- 系统提供默认头像作为兜底。

## 3. 目标

- 为现有前后端应用增加最小可用的登录态。
- 用最小成本增加 `viewer`、`operator`、`admin` 三档权限。
- 把业务写操作与当前登录用户绑定，替代硬编码操作人。
- 为侧边栏增加稳定的当前用户入口，承载用户信息和会话操作。
- 为未来接入真实登录体系保留清晰替换点。

## 4. 非目标

- 本次不引入 Spring Security、OAuth、OIDC 或企业 SSO。
- 本次不做复杂 RBAC、菜单级动态权限配置、权限管理后台。
- 本次不做头像上传编辑能力，只支持读取 MinIO 中既有头像。
- 本次不做用户注册、找回密码、修改密码、短信或邮箱验证。
- 本次不做多端会话管理、设备列表、强制下线。

## 5. 总体方案

整体方案采用“内置用户目录 + RSA 登录口令加密 + 服务端 Session + HttpOnly Cookie + 前端路由守卫 + 最小角色权限控制”的组合。

### 5.1 认证核心原则

- 登录时，前端不直接提交明文密码，而是先获取后端公钥，对密码加密后再提交。
- 后端解密后使用密码哈希校验，不存储明文密码。
- 登录成功后由后端创建服务端 Session，并通过 `HttpOnly Cookie` 持久化会话。
- 后续请求通过 Cookie 自动携带会话标识，前端不在 `localStorage` 存储 token。
- 每次请求只要会话仍然有效，就刷新过期时间，形成“滑动 14 天”。
- 换设备无 Cookie，因此需要重新登录。

### 5.2 为什么不采用前端持久化 Bearer Token

当前前端所有请求都通过同域 `/api` 访问后端，开发态由 Vite 代理，生产态由 Nginx 反代。这个拓扑更适合采用 Cookie Session：

- 接入成本更低，不需要为所有请求手动拼接 `Authorization`。
- `HttpOnly Cookie` 比前端存储 token 更适合作为最小实现。
- 14 天滑动续期与“换设备重新登录”天然匹配。

## 6. 用户与权限模型

### 6.1 内置用户模型

后端维护一份最小内置用户目录。每个用户包含：

- `username`：登录账号，唯一标识
- `nickname`：显示昵称，用于侧边栏展示
- `passwordHash`：密码哈希，使用 `bcrypt`
- `role`：角色，取值为 `VIEWER`、`OPERATOR`、`ADMIN`
- `avatarObjectKey`：MinIO 中头像对象 key，可为空

### 6.2 权限矩阵

#### `VIEWER`

- 可访问：仓库列表、场景列表、任务列表、任务详情、调度事件页
- 不可执行：仓库新增/编辑/删除、场景新增/编辑/删除、任务取消、调度事件重试

#### `OPERATOR`

- 具备 `VIEWER` 全部读权限
- 可执行：任务取消、调度事件重试
- 不可执行：仓库新增/编辑/删除、场景新增/编辑/删除

#### `ADMIN`

- 拥有全部读写权限

### 6.3 权限分层

- 前端负责按钮显隐或禁用，减少误操作。
- 后端负责最终权限校验，避免前端绕过。
- 审计与业务操作人统一以后端认证上下文为准，不信任前端透传的操作人名称。

## 7. 登录与会话流程

### 7.1 登录流程

1. 前端进入登录页。
2. 前端调用 `GET /api/auth/public-key` 获取登录加密公钥。
3. 前端使用公钥对用户输入的密码做 RSA 加密。
4. 前端调用 `POST /api/auth/login`，提交 `username` 与 `encryptedPassword`。
5. 后端用私钥解密密码，并对比对应用户的 `passwordHash`。
6. 校验通过后创建 Session。
7. 后端下发 `HttpOnly Cookie`，并返回当前用户信息。
8. 前端保存当前用户展示态，跳转业务首页。

### 7.2 会话恢复流程

1. 前端应用启动时调用 `GET /api/auth/me`。
2. 若 Cookie 对应 Session 仍有效，则返回当前用户信息。
3. 后端刷新 Session 过期时间为“当前时间 + 14 天”。
4. 前端恢复登录态并正常进入业务页。

### 7.3 会话失效与登出

- 当 Session 不存在、过期或 Cookie 无效时，后端返回 `401`。
- 前端收到 `401` 后清空本地用户状态并跳转登录页。
- 调用 `POST /api/auth/logout` 时，后端删除 Session 并清 Cookie。

## 8. 登录口令加密策略

本次明确满足“登录接口参数中不直接出现明文密码”的要求。

### 8.1 方案

- 仅对“登录提交”这一跳增加口令加密。
- 后端提供 RSA 公钥。
- 前端在登录页中把原始密码加密为 Base64 密文后提交。
- 登录接口只接收 `encryptedPassword`，不接收明文 `password` 字段。

### 8.2 安全边界说明

- 该方案解决“登录接口载荷中不直接暴露明文密码”的要求。
- 正式环境仍然要求使用 HTTPS/TLS 保护整体传输链路。
- 后端日志、异常、审计、数据库中都不得输出或存储明文密码。

## 9. 头像与默认头像策略

### 9.1 用户头像存储

- 用户头像文件存储在 MinIO。
- 后端用户配置中保存 `avatarObjectKey`。
- 后端在返回当前用户信息时，将 `avatarObjectKey` 转换为前端可访问的头像 URL。

### 9.2 默认头像

- 系统内置一张默认头像静态资源，放在前端项目内。
- 当用户未配置头像、MinIO 对象不存在、URL 生成失败或图片加载失败时，统一回退默认头像。

### 9.3 本次限制

- 本次只做头像展示与兜底，不做头像上传、裁剪、替换。
- 这样既满足 UI 需求，又避免本次扩展到个人资料管理。

## 10. 前端设计

## 10.1 新增模块

新增以下前端文件：

- `src/types/auth.ts`
- `src/api/auth.ts`
- `src/stores/auth.ts`
- `src/views/auth/LoginView.vue`
- `src/utils/auth-crypto.ts`
- `src/components/layout/SidebarUserPanel.vue`
- `src/assets/default-avatar.png` 或同等静态资源路径

## 10.2 路由与启动

### 路由

- 新增 `/login`
- 为现有业务路由标记需要登录
- 增加全局路由守卫

### 启动恢复

- 应用启动时由 `auth` store 执行 `bootstrap()`
- 调用 `/api/auth/me` 恢复登录态
- 未登录时引导到 `/login`

## 10.3 HTTP 行为

在 `src/api/http.ts` 中增加：

- `withCredentials: true`
- 统一 `401` 处理：
  - 清空认证状态
  - 若当前不在登录页，则跳转 `/login`
- 统一 `403` 处理：
  - 提示“当前账号无此操作权限”

## 10.4 侧边栏用户区

当前 `App.vue` 已持有全局侧边栏布局，本次在侧边栏左下角增加固定用户区。

展示内容：

- 头像
- 昵称
- 右侧箭头图标

交互方式：

- 点击后弹出工具菜单
- 建议使用 Element Plus 的 `el-popover` 或 `el-dropdown`

### 本次真实落地菜单项

- `设置`
- `切换账号`
- `退出登录`

### 可保留 UI 壳子的菜单项

- `收藏夹`
- `官网`
- `API 服务`
- `帮助与反馈`

这些可先展示为静态菜单项，点击后提示“暂未开放”，避免扩展到无关范围。

### 设置页

设置页本次只需最小展示：

- 昵称
- 账号
- 角色
- 当前头像
- 会话状态说明

不包含头像上传或资料编辑。

## 10.5 页面级权限控制

### `SceneListView.vue`

- `ADMIN` 才显示“新增场景”“编辑”“复制”“删除”等写操作

### `ScheduleEventListView.vue`

- `OPERATOR` 与 `ADMIN` 可见并可用“重试”
- `VIEWER` 不显示或禁用“重试”
- 重试请求不再传递硬编码 `anonymous`

### `TaskListView.vue` / `TaskDetailView.vue`

- `OPERATOR` 与 `ADMIN` 可取消任务

### `RepositoryListView.vue`

- `ADMIN` 才可新增、编辑、删除仓库

## 11. 后端设计

## 11.1 新增模块

建议新增如下认证领域文件：

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

## 11.2 用户配置

用户信息通过配置提供，例如：

- `platform.auth.users[].username`
- `platform.auth.users[].nickname`
- `platform.auth.users[].password-hash`
- `platform.auth.users[].role`
- `platform.auth.users[].avatar-object-key`

本次不引入用户表迁移，避免扩展数据库建模范围。

## 11.3 Session 存储

服务端使用最小内存 Session 仓库：

- `ConcurrentHashMap<String, AuthSession>`
- key 为随机生成的 Session ID
- value 记录：
  - `username`
  - `role`
  - `nickname`
  - `avatarObjectKey`
  - `expiresAt`

该实现适用于本次单实例/最小闭环需求。后续若要多实例部署，可替换为 Redis，不影响前端和大部分后端接口形态。

## 11.4 认证过滤器

过滤器职责：

- 从 Cookie 读取 Session ID
- 查找对应 Session
- 校验是否过期
- 未过期时刷新到“当前时间 + 14 天”
- 把当前用户信息写入 `AuthContextHolder`

过滤器应对登录、登出、公钥等匿名接口做放行。

## 11.5 MinIO 头像解析

- 若用户配置了 `avatarObjectKey`，则通过现有 MinIO 能力生成可访问 URL
- 若无配置或生成失败，则返回空值或默认占位，由前端回退默认头像
- 后续若需要，也可由后端直接返回默认头像 URL；本次优先保持后端简单

## 12. 接口设计

### 12.1 `GET /api/auth/public-key`

响应：

```json
{
  "algorithm": "RSA",
  "publicKeyPem": "-----BEGIN PUBLIC KEY-----..."
}
```

### 12.2 `POST /api/auth/login`

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
  "username": "admin",
  "nickname": "平台管理员",
  "avatarUrl": "https://minio.example/avatar/admin.png",
  "role": "ADMIN"
}
```

同时设置：

- `HttpOnly`
- `SameSite=Lax`
- `Path=/`
- `Max-Age=1209600`（14 天）

### 12.3 `GET /api/auth/me`

未登录返回 `401`。

已登录响应：

```json
{
  "username": "alice",
  "nickname": "徐个愿",
  "avatarUrl": "https://minio.example/avatar/alice.png",
  "role": "OPERATOR"
}
```

### 12.4 `POST /api/auth/logout`

- 清理服务端 Session
- 让 Cookie 过期
- 响应可为 `204` 或标准成功包体

## 13. 现有业务接口接入方式

### 13.1 登录校验

以下现有页面对应接口均要求登录：

- 仓库管理相关接口
- 场景管理相关接口
- 任务查询和详情接口
- 调度事件查询与重试接口

### 13.2 权限校验

后端在 controller 或 service 入口对写操作做角色校验：

- 仓库新增/编辑/删除：`ADMIN`
- 场景新增/编辑/删除：`ADMIN`
- 任务取消：`OPERATOR` 或 `ADMIN`
- 调度事件重试：`OPERATOR` 或 `ADMIN`

## 13.3 操作人透传收口

- `ScheduleEventRetryRequest.operatorName` 保留兼容期，但后端不再作为真实操作人来源
- 审计日志 `operatorName` 统一从 `AuthContext` 读取
- `TaskController` 中写死的 `system-user` 替换为当前登录用户昵称或账号

## 14. 错误处理

### 前端

- `401`：清空登录态并跳登录页
- `403`：提示“当前账号无此操作权限”
- 登录失败：统一提示“用户名或密码错误”
- 公钥获取失败：提示“登录服务暂不可用”

### 后端

- 用户不存在、密码错误、解密失败统一按登录失败处理
- 不向前端暴露“用户名不存在”或“解密失败”等细节
- Session 失效时返回 `401`
- 角色不足时返回 `403`

## 15. 配置项

建议新增以下配置：

- `platform.auth.session.cookie-name`
- `platform.auth.session.sliding-days`
- `platform.auth.rsa.public-key`
- `platform.auth.rsa.private-key`
- `platform.auth.users`

开发环境可支持自动生成临时 RSA 密钥；生产环境建议通过配置或密钥管理系统注入。

## 16. 测试策略

### 16.1 前端测试

- `auth store` 单测：
  - 登录态恢复
  - 登录成功
  - 登出清理
  - `401` 清理
- 路由守卫测试：
  - 未登录跳登录页
  - 已登录进入业务页
- `LoginView` 测试：
  - 获取公钥
  - 加密提交
  - 登录失败提示
- `SidebarUserPanel` 测试：
  - 显示头像和昵称
  - 菜单展开
  - 点击切换账号和退出登录
- 业务页面权限测试：
  - `VIEWER` 不显示写操作
  - `OPERATOR`/`ADMIN` 显示对应按钮

### 16.2 后端测试

- `AuthService` 单测：
  - 公钥获取
  - 密码解密
  - 登录成功与失败
  - Session 创建、续期、登出
- `AuthSessionFilter` 测试：
  - 匿名接口放行
  - 无效 Session 返回 `401`
  - 有效 Session 恢复上下文
- 现有控制器测试：
  - 未登录返回 `401`
  - 权限不足返回 `403`
  - 合法角色访问成功
- 审计日志测试：
  - 记录的操作人来自认证上下文

## 17. 验收标准

满足以下条件即可认为本次需求完成：

1. 用户未登录时不能访问业务页面。
2. 登录请求参数中不直接包含明文密码。
3. 后端只保存密码哈希，不保存明文密码。
4. 登录态使用 `HttpOnly Cookie`，并支持滑动 14 天续期。
5. 换设备访问时需要重新登录。
6. 侧边栏左下角展示用户头像和昵称。
7. 点击用户区可展开工具菜单。
8. 用户头像优先显示 MinIO 头像，无可用头像时回退默认头像。
9. `VIEWER`、`OPERATOR`、`ADMIN` 三档权限生效。
10. 调度事件重试、任务取消、仓库/场景写操作都有后端权限校验。
11. 审计日志操作人来自当前登录用户。

## 18. 风险与后续演进

### 18.1 本次已知限制

- Session 先使用内存存储，不支持服务重启后保活。
- 多实例部署时需替换为共享 Session 存储，例如 Redis。
- 头像上传与资料编辑不在本次范围内。

### 18.2 后续可平滑替换部分

- 内置用户目录替换为数据库用户表或企业统一身份源
- 内存 Session 替换为 Redis Session
- 最小角色权限替换为真实 RBAC
- 登录页保留不变，仅替换后端认证实现
