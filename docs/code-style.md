# 代码编写规范

> 适用范围：`playwright-platform-server`（Spring Boot）与 `playwright-platform-web`（Vue 3）。
> 目标：让不同开发者、不同 AI 产出的代码在结构、命名、错误处理、验证方式上保持一致，减少「能跑但风格各异」的维护成本。
> 本规范是**强制约定**，除特别说明外均适用于新增代码；修改存量代码时至少保证不引入不一致。

---

## 1. 总体原则

| 原则 | 落地要求 |
|------|----------|
| 最小改动 | 只改与需求相关的代码；不顺带重构、不重命名无关变量、不批量格式化 |
| 模块内聚 | 后端按业务模块分包（`auth` / `space` / `task` / `evaluation` …），模块内自带 controller / service / mapper / model / dto |
| 先读后写 | 改动前先读同模块已有实现，复用既有模式；禁止凭猜测新增接口或字段 |
| 单一职责 | Controller 只做参数校验与响应组装；业务规则放 Service；SQL 放 Mapper |
| 可验证 | 每个改动必须能通过 `mvn test` / `npm test` / `vue-tsc`；前端交互改动需在浏览器实测 |
| 密钥零入库 | 代码、注释、测试、文档中不得出现真实密码、Token、Key；一律走环境变量 |

---

## 2. 后端规范（Spring Boot）

### 2.1 目录与分层

```
com.example.platform.<module>
├── controller/   # REST 入口，薄层
├── service/      # 接口 + Impl，承载业务规则与事务
├── mapper/       # MyBatis 注解 Mapper
├── model/        # 数据库实体
├── dto/          # 请求 / 响应对象（record）
└── config/       # 模块级配置（按需）
```

- 跨模块通用能力放 `common`；平台级设置放 `admin`；不要为了「复用」把业务代码塞进 `common`。
- **禁止**新增 XML Mapper；持久层只使用注解式 MyBatis（`@Select` / `@Insert` / `@Update` / `@Delete` / `@Results`）。

### 2.2 命名

| 类型 | 规范 | 示例 |
|------|------|------|
| Controller | `<Module>Controller` | `TaskController` |
| Service | `<Module>Service` / `<Module>ServiceImpl` | `SceneService` |
| Mapper | `<Entity>Mapper` | `SpaceMapper` |
| 实体 | `<Entity>Entity` | `TaskEntity` |
| 请求/响应 | `<Action><Entity>Request` / `<Entity>Response` | `CreateSpaceRequest`、`AdminUserResponse` |
| 测试 | `<ClassUnderTest>Test` | `AdminUserServiceTest` |

- DTO 使用 Java `record`，字段即构造参数；不使用 Lombok。
- Controller 内使用的私有请求/响应 record 可以定义在 Controller 文件末尾（参考 `AdminUserController`）。

### 2.3 依赖注入与事务

- 一律**构造器注入**，字段声明 `private final`；禁止 `@Autowired` 字段注入。
- 写操作 Service 方法加 `@Transactional`；**不要**把 Git / Docker / MinIO 等外部调用包进大事务（参考任务执行编排）。
- 无法回滚的外部资源失败，用状态落库 + 补偿逻辑处理，不依赖事务回滚。

### 2.4 异常与错误响应

- 统一成功响应：`ApiResponse.ok(data)`；分页使用 `PageResponse.of(items, total, page, size)`。
- 业务错误优先抛 `BusinessException(code, message)`，由 `GlobalExceptionHandler` 映射 HTTP 状态；权限类错误可用 `ResponseStatusException(HttpStatus.FORBIDDEN, "…")`。
- 错误码使用全大写英文常量（`BAD_REQUEST` / `CONFLICT` / `TARGET_NOT_FOUND` / `SSRF_VIOLATION` …），新增错误码必须同步加入 `GlobalExceptionHandler` 的状态映射。
- **不要**把异常堆栈、SQL、内部路径返回给前端；未预期异常统一返回「服务内部错误，请稍后重试」，细节写日志。
- 用户可见的错误文案用中文，简洁、可操作（如「仓库名称已存在，请更换后重试」）。

### 2.5 校验与边界

- 入参在 Controller/Service 边界校验：长度、枚举、URL、分页上限（`size ≤ 100`）。
- 跨空间访问必须在 Service 层校验 `spaceId` 归属，不能只依赖前端。
- 超管（`AuthContext.superAdmin()`）视为任意空间 `ADMIN`，但**仍要**经过统一的权限方法，禁止在业务代码里散落 `if (superAdmin)` 分支。
- 所有外部 URL（HTTP Agent、Webhook）必须做 SSRF 校验；私有仓库凭据必须经 `CredentialCipher` 加解密。

### 2.6 注释与日志

- 公共类、Service 实现、复杂算法写中文 JavaDoc，包含「核心职责 / 依赖说明」；行内注释只解释「为什么」，不解释「是什么」。
- 日志使用 SLF4J，禁止 `System.out`；异常日志必须带上下文（任务 ID、空间 ID、阶段）。
- 日志中不得输出密码、Token、完整凭据；必要时使用 `SensitiveDataRedactor`。

### 2.7 数据库与迁移

- 表结构变更一律新增 Flyway 迁移：`V<n>__<lower_snake_topic>.sql`，禁止修改已发布的迁移文件。
- 新表使用 `utf8mb4_unicode_ci`，主键 `bigint` 自增；业务唯一约束显式命名（`uk_*`），便于 `GlobalExceptionHandler` 识别并发冲突。
- 唯一约束冲突要落到友好中文提示，不允许直接暴露 500。

### 2.8 配置

- 新增配置项写入 `application.yml`，并提供 `${ENV_VAR:默认值}`；部署差异只放 `.env` / `.env.example`。
- 配置类使用 `@ConfigurationProperties`（如 `TaskExecutionProperties`），禁止在业务代码里散落 `@Value`（简单单值除外）。

### 2.9 测试

- 单元测试用 JUnit 5 + Mockito；Service 逻辑必须可在不启动 Spring 的情况下测试。
- 测试方法名用中文或英文完整语义句，例如 `删除自己时应被拒绝`。
- 涉及权限、状态机、并发限制的改动必须补测试用例；修复 Bug 时先补失败用例再修。

---

## 3. 前端规范（Vue 3 + TypeScript）

### 3.1 目录与文件

```
src/
├── api/         # 每个后端模块一个 api 文件，函数名以 fetch/create/update/delete 开头
├── types/       # 与 api 对应的 TS 类型
├── stores/      # Pinia store，按领域拆分
├── views/       # 路由页面，按模块分子目录
├── components/  # 跨页面复用组件（list / ai / evaluation）
└── utils/       # 纯函数工具（格式化、权限、错误文案、反馈）
```

- 页面组件命名 `XxxView.vue`；复用组件 `XxxDialog.vue` / `XxxList.vue`。
- 一个页面的私有子组件优先放在同级目录，跨页面复用才上移 `components/`。

### 3.2 组件写法

- 统一 `<script setup lang="ts">` + Composition API，禁止 Options API。
- 顺序：`import` → 常量/类型 → store → 响应式状态 → computed → 方法 → 生命周期。
- props/emits 必须显式声明类型；禁止 `any`（无法避免时用 `unknown` 并做类型收窄）。
- 列表页统一使用 `ListPageShell`，通过 `#header-left` / `#header-right` 插槽组织工具栏，分页由壳组件托管。

### 3.3 API 与类型

- 所有请求走 `src/api/http.ts` 封装（自动解包 `ApiResponse`），页面不直接使用 `axios`。
- 每个接口在 `types/` 中有明确入参/出参类型，禁止用内联对象字面量重复定义。
- 接口函数保持纯数据转换，不在这里弹 Toast、不操作 store。

### 3.4 状态与权限

- 跨页面状态（登录态、空间列表、AI 会话）放 Pinia；页面内状态用 `ref` / `reactive`。
- 权限判断复用 `utils/space-permissions.ts` 与 `authStore.isSuperAdmin`，禁止在页面里硬编码角色字符串。
- 路由级权限写 `meta`（`requiresAuth` / `superAdminOnly`），菜单可见性由 `getSpaceMenuGroups` 统一计算。

### 3.5 样式

- 组件样式使用 `<scoped>`；类名采用 `block__element--modifier`（如 `admin-users__role-tag`）。
- 影响 Element Plus 内部结构的选择器使用 `:deep()`。
- 全局样式只放 `src/style.css`：主题变量、表格/表单基础样式、Toast 与确认弹窗（`app-toast` / `app-confirm-dialog`）。
- 颜色、圆角、间距优先使用 `:root` 中的 CSS 变量与既有取值，不新增随手色值。

### 3.6 交互反馈

- 成功/失败提示统一使用 `showAppToast(message, type)`；危险操作二次确认使用 `confirmDangerAction`。
- 需要自定义内容（如状态切换对比）时，使用 `ElMessageBox` + `dangerouslyUseHTMLString` + 全局 `customClass`，不要自建 `el-dialog` 做确认。
- 时间展示统一使用 `formatBeijingDateTime`（`utils/datetime.ts`），禁止页面各自格式化。
- 错误文案统一经 `toErrorMessage(error, fallback)` 转换，禁止直接把后端英文错误抛给用户。

### 3.7 表格约定

- 列表表格统一 `class="list-table"`；列宽固定列使用 `fixed="right"`。
- 行内操作：主要操作（查看/编辑）用 `el-button link`；超过 3 个操作时，其余收进 `el-dropdown`（与 Prompt 管理一致）。
- 操作列容器使用 `.table-actions`（`inline-flex` + `nowrap`），避免换行。
- 单元格内容单行省略由全局 `.list-table td .cell` 统一处理，页面不重复写 `ellipsis`。

### 3.8 类型检查与测试

- 提交前必须执行：`npm run build`（内含 `vue-tsc -b`）与 `npm test`。
- 纯逻辑（格式化、权限、store）优先抽到 `utils/` 或 `stores/` 并配 Vitest 单测；页面视觉改动用浏览器实测。
- 修改前端后若使用 Docker 开发环境，必须 `docker compose restart web`（Vite 对挂载目录改动可能不热更新）。

---

## 4. Git 与提交

| 前缀 | 用途 |
|------|------|
| `feat:` | 新功能 |
| `fix:` | Bug 修复 |
| `docs:` | 文档 |
| `refactor:` | 重构（行为不变） |
| `test:` | 测试 |
| `chore:` | 构建 / 工具 |

- 一次提交只做一件事；功能 + 顺手重构必须拆成两个提交。
- 提交前检查：无调试输出、无临时文件、无构建产物（`jacoco.exec`、`dist/`、`node_modules/`）、无 `.env`。
- 提交信息正文写「为什么」，不逐条罗列 diff。

---

## 5. AI 协作约定

1. **禁止编造**：不确定的接口、字段、错误码必须先读代码或询问，不得凭空生成。
2. **先定位再修改**：先给出涉及文件与函数，再动手；不要整文件重写。
3. **改动后自证**：后端跑相关测试/编译，前端跑类型检查；无法验证时明确说明「未验证」。
4. **同步文档**：模块行为变化必须同步对应 playbook；接口契约变化同步 `docs/playbooks` 与 README。
5. **不扩大范围**：用户没要求的功能、重构、兼容层一律不加。

---

## 6. 完成定义（DoD）

- [ ] 代码通过编译与类型检查，相关测试通过
- [ ] 新增/修改接口有权限校验、边界校验与中文错误文案
- [ ] 数据库变更为新增 Flyway 迁移，未修改历史迁移
- [ ] 前端错误提示、时间格式、表格操作列符合既有约定
- [ ] 文档（playbook / README / DEPLOYMENT）已同步
- [ ] 无密钥、无构建产物、无临时调试代码进入提交
