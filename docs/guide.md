# Playwright Test Platform 面试讲解手册

---

## 1. 项目一句话定位

这是一个面向 Playwright 自动化测试场景的执行与管理平台。它把原本分散在测试仓库里的执行命令、环境变量、运行日志、测试结果、截图、视频、Trace 等能力集中到一个平台中，提供：

- 用户自助注册、登录态管理与个人空间自动入驻
- 空间广场、个人空间、空间成员与空间审批
- 测试仓库配置管理
- E2E 场景配置管理
- 手动触发和定时触发
- 异步任务执行与状态跟踪
- 用例结果、阶段日志、测试产物统一查看和下载

从业务角度看，它本质上是一个“测试执行平台”。

从技术角度看，它本质上是一个“前后端分离 + 异步任务编排 + 对象存储归档 + 工程化部署”的系统。

---

## 2. 你可以怎么介绍这个项目

### 2.1 30 秒版本

我做的是一个带空间协作能力的 Playwright 测试执行平台。用户可以自助注册，系统会自动创建个人空间并让用户成为自己空间的管理员。进入空间后，用户可以配置测试仓库、维护 E2E 场景、选择浏览器和环境变量，然后手动或定时触发任务。后端会拉取代码、执行安装和测试命令、解析 Playwright 结果文件，并把截图、视频、Trace、阶段日志归档到 MinIO，前端统一展示任务状态、用例结果和运行产物。

### 2.2 1 分钟版本

这个项目是前后端分离架构。前端用 Vue 3 + TypeScript + Vite + Pinia，除了仓库、场景、任务和任务详情，还提供登录/注册、空间广场、空间审批、头像与空间切换等能力；后端用 Spring Boot + MyBatis + Flyway，负责认证、空间模型、仓库/场景 CRUD、定时调度、任务异步执行、日志和产物归档。数据落在 MySQL，缓存用 Redis，运行产物存到 MinIO。
任务执行不是在 Web 请求线程里同步跑，而是由后端创建任务记录后提交到独立线程池，之后再调用本地或 Docker Runner 去执行安装和测试命令。执行完成后，后端解析结果文件、入库 case result、归档 artifact，并通过平台代理下载接口把 Trace、日志等内容暴露给前端。用户和空间之间通过 `space_member` 绑定角色，空间审批用于处理非 owner 用户的加入和角色升级。

### 2.3 适合面试官继续追问的亮点

- 不是简单 CRUD，有完整的任务生命周期编排
- 有认证、空间隔离、空间审批和角色控制，不只是单用户后台
- 有定时调度能力，不只是手动触发
- 执行环境做了本地模式和 Docker 模式抽象
- 测试结果和产物不是只存数据库，而是数据库 + 对象存储分工
- 有缓存、异常统一处理、请求链路追踪、覆盖率 CI

---

## 3. 仓库整体结构

```text
.
├── README.md
├── docker-compose.yml                # 本地开发环境编排
├── docker-compose.prod.yml           # 单机生产部署编排
├── docs/
│   ├── deployment.md                 # 单机生产部署说明
│   └── project-architecture-interview-guide.md
├── .github/workflows/
│   └── ci.yml                        # GitHub Actions CI
├── playwright-platform-server/       # Spring Boot 后端
└── playwright-platform-web/          # Vue 3 前端
```

可以把整个项目理解为三层：

1. 展示层：`playwright-platform-web`
2. 业务与编排层：`playwright-platform-server`
3. 基础设施层：MySQL、Redis、MinIO、Docker Runner、GitHub Actions

---

## 4. 系统架构图

```text
[Vue Web]
   |
   | HTTP /api
   v
[Spring Boot Server]
   |-- MyBatis -> MySQL
   |-- Redis -> 详情缓存 / 调度租约
   |-- MinIO -> 截图/视频/Trace/日志/报告
   |-- RunnerExecutionService
           |-- LocalRunnerCommandExecutor
           |-- DockerRunnerCommandExecutor
                   |
                   v
             [Playwright Runner 容器]
                   |
                   v
             拉取测试仓库 -> 安装依赖 -> 执行测试 -> 生成结果
```

---

## 5. 业务对象与数据模型

这个项目现在更适合按五类核心业务对象来讲：

### 5.1 `platform_user`

表示平台用户，保存：

- 用户名
- 昵称
- 密码哈希
- 头像对象键
- 是否启用
- 最近进入的空间 `last_space_id`

它解决的是“谁在使用平台，以及当前登录态对应哪个用户”的问题。

### 5.2 `space`

表示空间协作边界，保存：

- 空间名称和描述
- `owner_user_id`
- `created_by`

它解决的是“测试资源、场景、任务归谁管理、谁能看到”的问题。

配套还有两个从属对象：

- `space_member`：记录用户在空间中的角色，例如 `VIEWER / OPERATOR / ADMIN`
- `space_access_request`：记录申请加入空间或升级空间角色的审批流

### 5.3 `test_repository`

表示一个测试代码仓库，保存：

- Git 地址
- 默认分支
- 工作目录
- 安装命令
- 测试执行命令模板
- 测试目录
- 结果索引文件路径
- 产物目录路径

它解决的是“平台如何知道去哪个仓库、用什么命令执行”的问题。

### 5.4 `scene`

表示一个测试场景，和仓库绑定，保存：

- 关联仓库
- 场景名称和描述
- 分支
- 选择器类型和值
- 浏览器 / projectName
- 环境变量 JSON
- 执行命令
- 是否启用定时调度
- Cron 表达式

它解决的是“在这个仓库里具体执行哪一类测试”的问题。

### 5.5 `task`

表示一次实际执行记录，保存：

- 来自哪个场景、哪个仓库
- 触发方式：手动或定时
- 当前状态和阶段
- 实际解析出的分支、浏览器、环境变量、执行命令
- 开始/结束时间、耗时
- 失败码和失败信息

一个 `task` 下面再挂：

- `case_result`：每条测试用例结果
- `artifact`：截图、视频、trace 等产物
- `task_stage_log`：PREPARING / INSTALLING / TESTING 等阶段日志

所以数据库设计不是简单平铺，而是围绕“用户 -> 空间 -> 资源配置 -> 任务执行 -> 结果可观测性”展开。

---

## 6. 核心执行链路

这是面试时最值得讲的一段。

### 6.1 自助注册与空间入驻链路

1. 用户在登录页点击“注册账号”
2. 前端先获取 RSA 公钥并加密密码
3. 调用后端 `POST /api/auth/register`
4. 后端在单事务内完成：
   - 校验用户名唯一
   - 校验昵称唯一
   - 创建 `platform_user`
   - 自动创建个人空间
   - 写入 `space_member.ADMIN`
   - 更新 `last_space_id`
   - 创建 `user_session`
5. 前端拿到登录态和 `lastSpaceId` 后，直接跳转到 `/spaces/{lastSpaceId}/repos`

这条链路能说明项目已经不再是单纯“默认 admin 登录”的演示系统，而是具备最小可用的用户入驻闭环。

### 6.2 手动执行链路

1. 前端在场景页或任务页点击“执行”
2. 调用后端 `POST /api/scenes/{sceneId}/run`
3. 后端创建任务记录，状态先进入 `QUEUED`
4. 后端把任务提交到专用线程池 `taskExecutionExecutor`
5. 后端准备 workspace，拉取测试仓库代码
6. 执行安装命令
7. 执行测试命令
8. 解析 Playwright 结果索引文件
9. 入库 case result
10. 把截图、视频、Trace、日志归档到 MinIO
11. 更新任务最终状态，并刷新场景最近执行结果
12. 前端轮询或刷新后读取任务详情、日志和产物

### 6.3 定时执行链路

1. `SceneServiceImpl` 通过 `@Scheduled(fixedDelay = 60000)` 每分钟扫描一次
2. `SceneSchedulerServiceImpl` 找到到期场景
3. 通过 `SceneScheduleLeaseService` 抢租约，避免重复触发
4. 创建新的定时任务
5. 后续执行流程与手动执行一致

### 6.4 为什么这些链路值得讲

因为它体现了你不是只会做页面和接口，而是理解：

- 用户注册与登录态建立
- 空间隔离和角色边界
- 请求线程和异步执行线程的职责边界
- 任务状态机
- 外部资源调用
- 文件系统 / 对象存储协作
- 调度幂等和重复触发控制

---

## 7. 后端架构与文件职责

后端位于 `playwright-platform-server`，技术栈是：

- Spring Boot 3.5
- Spring Web
- MyBatis 注解式 Mapper
- Flyway
- Spring Data Redis
- MinIO Java SDK
- Maven + JaCoCo

---

## 8. 后端关键文件速览

### 8.1 应用入口与基础配置

- `src/main/java/com/example/platform/PlatformApplication.java`  
  Spring Boot 启动类，开启 `@EnableScheduling`，并通过 `@MapperScan` 扫描所有 MyBatis Mapper。

- `src/main/resources/application.yml`  
  统一定义数据源、Redis、Flyway、日志格式、Runner、缓存、对象存储等配置。

- `src/main/resources/application-dev.yml`  
  开发环境覆盖配置，保持环境变量注入方式一致。

### 8.2 common 包

- `common/ApiResponse.java`、`ApiErrorResponse.java`、`PageResponse.java`  
  定义统一响应结构，前端不用针对每个接口单独解析格式。

- `common/GlobalExceptionHandler.java`  
  做统一异常处理，把参数错误、状态冲突、唯一键冲突和业务错误码转成标准响应。像用户名重复、昵称重复、空间名重复、审批列表加载失败都会在这一层被收口成可读错误，而不是把底层异常直接暴露给前端。

- `common/RequestCorrelationFilter.java`  
  给每次请求生成或透传 `requestId`、`traceId`，写入 MDC，便于日志追踪。

- `common/ApplicationErrorSummaryService.java`  
  用于汇总应用错误，为任务诊断提供补充信息。

### 8.3 auth 模块

- `auth/controller/AuthController.java`
  提供 `/api/auth/public-key`、`/api/auth/login`、`/api/auth/register`、`/api/auth/me`、`/api/auth/profile`、`/api/auth/avatar`、`/api/auth/logout`。

- `auth/service/AuthServiceImpl.java`
  负责登录、注册、session 续期、昵称修改、头像修改。注册时会在单事务里完成“建用户 + 建空间 + 绑管理员角色 + 建 session”。

- `auth/filter/AuthSessionFilter.java`
  从 Cookie 中恢复 session，把当前用户上下文放到请求线程。

- `auth/mapper/PlatformUserMapper.java`
  负责用户查询、插入、昵称和头像更新、默认空间更新。

- `auth/mapper/UserSessionMapper.java`
  负责 session 写入、查询、续期和删除。

### 8.4 repository 模块

- `repository/controller/RepositoryController.java`  
  对外提供仓库的 CRUD API。

- `repository/service/RepositoryServiceImpl.java`  
  处理仓库创建、更新、删除、分页查询、名称唯一性校验，以及详情缓存失效。

- `repository/mapper/TestRepositoryMapper.java`  
  MyBatis 注解式数据库访问层。

- `repository/service/RepositoryCascadeDeleteServiceImpl.java`  
  删除仓库时执行级联清理，而不是只删主表。

### 8.5 scene 模块

- `scene/controller/SceneController.java`  
  提供场景 CRUD 和场景卡片分页查询。

- `scene/service/SceneServiceImpl.java`  
  负责场景参数规范化、仓库合法性校验、场景唯一性校验、`nextRunAt` 计算、缓存失效。

- `scene/service/SceneSchedulerServiceImpl.java`  
  负责定时触发到期场景。

- `scene/service/SceneScheduleLeaseServiceImpl.java`  
  负责调度租约，避免多实例或重复扫描时重复触发。

- `scene/service/SceneScheduleTimeResolver.java`  
  负责根据 Cron 解析下一次执行时间。

### 8.6 space 模块

- `space/controller/SpaceController.java`
  提供我的空间、空间广场、空间创建编辑删除等接口。

- `space/controller/SpaceAccessRequestController.java`
  提供空间申请权限、审批列表、同意/拒绝申请等接口。

- `space/service/SpaceServiceImpl.java`
  负责空间列表、广场数据组装、Owner 信息、权限可见性。

- `space/service/SpaceAccessRequestServiceImpl.java`
  负责提交申请、审批、申请人信息组装，并把审批查询异常统一收口为业务错误。

- `space/service/SpaceAuthorizationServiceImpl.java`
  负责校验当前用户是否能访问某个空间，以及是否具备空间管理员权限。

- `space/mapper/SpaceMapper.java`
- `space/mapper/SpaceMemberMapper.java`
- `space/mapper/SpaceAccessRequestMapper.java`
  三个 mapper 分别负责空间、成员关系、审批流数据访问。

### 8.7 task 模块

这是后端最核心的模块。

- `task/controller/TaskController.java`  
  提供运行任务、取消任务、任务列表、任务详情、日志、产物、用例结果等接口。

- `task/service/TaskServiceImpl.java`  
  是任务应用服务入口。负责创建任务、提交异步执行、读取任务详情、下载产物和日志。

- `task/service/TaskExecutionConfig.java`  
  定义专用线程池 `taskExecutionExecutor`，避免 Playwright 执行阻塞 Web 请求线程。

- `task/service/TaskExecutionOrchestrator.java`  
  整个任务生命周期编排核心。把 PREPARING、INSTALLING、TESTING、ARCHIVING 串起来。

- `task/service/TaskCreationService.java`  
  创建任务初始数据。

- `task/service/TaskExecutionMutationService.java`  
  负责长流程中的数据库写入，避免把外部 IO 和大事务绑在一起。

- `task/service/TaskQueryViewService.java`  
  面向详情页组装任务视图数据。

- `task/service/TaskCaseResultParseServiceImpl.java`  
  解析 Playwright 结果文件。

- `task/service/TaskCaseResultPersistenceServiceImpl.java`  
  把 case result 持久化入库。

- `task/service/TaskArtifactArchiveServiceImpl.java`  
  负责把本地产物归档到对象存储，并建立 artifact 记录。

- `task/service/TaskStageLogServiceImpl.java`  
  阶段日志存储与查询。

- `task/service/TaskRecoveryService.java`  
  负责异常恢复相关逻辑。

### 8.8 runner 模块

这个模块体现了执行环境抽象。

- `runner/service/RunnerCommandExecutorConfig.java`  
  根据配置选择本地执行器或 Docker 执行器。

- `runner/service/RunnerExecutionServiceImpl.java`  
  面向上层暴露统一的“执行阶段命令”能力。

- `runner/service/LocalRunnerCommandExecutor.java`  
  本地模式执行命令，适合可信开发环境。

- `runner/service/DockerRunnerCommandExecutor.java`  
  Docker 模式执行命令，把测试命令放进短生命周期容器里运行，隔离性更好。

- `runner/service/DockerCommandBuilder.java`  
  负责组装 Docker 运行命令。

- `runner/service/RunnerWorkspaceServiceImpl.java`  
  准备和清理任务工作目录，负责代码 checkout 等工作区管理。

### 8.9 storage 模块

- `storage/config/MinioConfig.java`  
  初始化 MinIO 客户端。

- `storage/service/MinioObjectStorageService.java`  
  负责 bucket 检查、文件上传、对象读取、预签名 URL 构造、对象删除。

### 8.10 cache 模块

- `cache/DetailCacheService.java`  
  用 Redis 做详情缓存，带空值缓存、TTL 抖动、分布式互斥锁和进程内锁。

这是很好的工程亮点，说明项目考虑了：

- 缓存穿透
- 缓存击穿
- 缓存雪崩

### 8.11 audit 模块

- `audit/mapper/PlatformAuditLogMapper.java`
- `audit/model/PlatformAuditLogEntity.java`

用于平台审计日志存储，说明系统有一定操作可追溯设计。

---

## 9. 后端分层怎么理解

这个项目后端大致是：

```text
Controller -> Service -> Mapper -> MySQL
                   |-> Redis
                   |-> MinIO
                   |-> Runner / FileSystem / Docker
```

但真正值得讲的不是这句分层口号，而是两个设计点：

### 9.1 Controller 保持薄

像 `AuthController`、`SpaceController`、`RepositoryController`、`SceneController`、`TaskController` 都只做：

- 接收 HTTP 请求
- 参数映射
- 调用 Service
- 返回统一响应

业务逻辑基本都压在 Service 层，这样控制器不会变成“胖 Controller”。

### 9.2 长事务和外部 IO 被拆开了

`TaskExecutionOrchestrator` 需要操作：

- 数据库
- 文件系统
- Git 工作区
- Docker 或本地 Shell
- 对象存储

这种流程如果全部包在一个数据库事务里，会带来：

- 事务时间过长
- 数据库连接占用
- 回滚语义不清晰
- 外部系统无法真正回滚

所以这里通过 `TaskExecutionMutationService` 把数据库写操作和长时间 IO 流程拆开，这是比较成熟的工程处理。

---

## 10. 前端架构与文件职责

前端位于 `playwright-platform-web`，技术栈是：

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Element Plus
- Vitest

这个前端不是复杂的运营后台框架，但结构是清楚的，重点在“页面-状态-接口-类型”四层。

---

## 11. 前端关键文件速览

### 11.1 启动与路由

- `src/main.ts`  
  创建 Vue 应用，挂载 Pinia 和 Router。

- `src/App.vue`  
  页面根组件，负责整体壳层、空间菜单、侧边栏用户区、头像上传弹窗。

- `src/router/index.ts`  
  定义路由：
  - `/login` 登录/注册
  - `/home` 空间广场
  - `/spaces/:spaceId/repos` 仓库管理
  - `/spaces/:spaceId/scenes` 场景管理
  - `/spaces/:spaceId/scenes/:sceneId/tasks` 某场景下任务列表
  - `/spaces/:spaceId/tasks` 全部任务列表
  - `/spaces/:spaceId/tasks/:id` 任务详情
  - `/spaces/:spaceId/access-requests` 空间审批
  - `/spaces/:spaceId/no-access` 无权限用户申请加入页

### 11.2 API 层

- `src/api/http.ts`  
  Axios 封装层，把后端统一响应 `ApiResponse` 解析成前端直接可用的数据。

- `src/api/repository.ts`  
  仓库相关 API。

- `src/api/scene.ts`  
  场景相关 API。

- `src/api/task.ts`  
  任务列表、任务详情、执行、取消、日志、产物、case result 等 API。

- `src/api/auth.ts`
  认证相关 API，包括获取公钥、登录、注册、当前用户、修改昵称、上传头像。

- `src/api/space.ts`
  空间列表、空间广场、空间申请与审批 API。

这一层的价值是隔离页面和 HTTP 细节。

### 11.3 状态管理层

- `src/stores/repository.ts`  
  管理仓库列表、分页、选项数据和保存/删除流程。

- `src/stores/scene.ts`  
  管理场景列表、详情加载、保存删除流程。

- `src/stores/task.ts`  
  管理任务列表、任务详情、日志、用例结果、产物等状态。

- `src/stores/auth.ts`
  管理当前用户、登录态恢复、公钥缓存、登录/注册/头像上传。

- `src/stores/space.ts`
  管理空间列表、空间广场、当前空间、空间审批流以及 Owner 信息同步。

尤其 `task.ts` 里有个设计点值得讲：详情页数据使用 `Promise.allSettled`，即使某个子接口失败，也不一定让整个详情页完全不可用。  
这说明前端在任务详情页场景里考虑了“部分失败容忍”。

### 11.4 页面层

- `src/views/repository/RepositoryListView.vue`  
  仓库管理页面，支持增删改查、启用停用。

- `src/views/scene/SceneListView.vue`  
  场景管理页面，支持关联仓库、配置浏览器、环境变量、Cron 表达式、跳转任务列表。

- `src/views/task/TaskListView.vue`  
  任务列表页面。

- `src/views/task/TaskDetailView.vue`  
  任务详情页，是前端最复杂的页面，展示：
  - 任务状态
  - 用例统计
  - 失败/通过过滤
  - 截图、视频、Trace
  - 阶段日志
  - 重新执行、取消任务

- `src/views/task/useTaskDetailLoader.ts`
- `src/views/task/useTaskListLifecycle.ts`  
  抽取页面逻辑，避免页面组件太重。

- `src/views/auth/LoginView.vue`
  登录和注册双态页面，注册成功后会直接进入个人空间。

- `src/views/home/HomeView.vue`
  空间广场页面，展示 Owner、可访问空间、申请权限状态。

- `src/views/space/SpaceAccessRequestListView.vue`
  空间审批页，展示申请人昵称、账号、头像和审批状态。

- `src/views/space/SpaceNoAccessView.vue`
  无权限用户申请加入空间的页面。

### 11.5 组件与工具

- `src/components/list/ListPageShell.vue`  
  列表页通用外壳，统一分页和页面壳子样式。

- `src/components/layout/SidebarUserPanel.vue`
  侧边栏用户区，负责头像、昵称、菜单和头像点击上传入口。

- `src/components/layout/SpaceSwitcher.vue`
  空间切换器，负责在多个可访问空间之间切换。

- `src/utils/artifact.ts`、`stage-log.ts`、`task-display.ts`、`ui-feedback.ts`  
  负责产物链接处理、日志读取、任务状态展示、消息提示等通用逻辑。

- `src/types/*.ts`  
  前后端数据结构 TypeScript 类型定义，减少类型不一致问题。

---

## 12. 前端页面和后端接口如何映射

### 12.1 认证与空间页

前端：

- `views/auth/LoginView.vue`
- `views/home/HomeView.vue`
- `views/space/SpaceAccessRequestListView.vue`
- `stores/auth.ts`
- `stores/space.ts`
- `api/auth.ts`
- `api/space.ts`

后端：

- `AuthController`
- `AuthServiceImpl`
- `SpaceController`
- `SpaceAccessRequestController`
- `SpaceServiceImpl`
- `SpaceAccessRequestServiceImpl`
- `SpaceAuthorizationServiceImpl`

### 12.2 仓库页

前端：

- `views/repository/RepositoryListView.vue`
- `stores/repository.ts`
- `api/repository.ts`

后端：

- `RepositoryController`
- `RepositoryServiceImpl`
- `TestRepositoryMapper`

### 12.3 场景页

前端：

- `views/scene/SceneListView.vue`
- `stores/scene.ts`
- `api/scene.ts`

后端：

- `SceneController`
- `SceneServiceImpl`
- `SceneMapper`

### 12.4 任务列表 / 详情页

前端：

- `views/task/TaskListView.vue`
- `views/task/TaskDetailView.vue`
- `stores/task.ts`
- `api/task.ts`

后端：

- `TaskController`
- `TaskServiceImpl`
- `TaskExecutionOrchestrator`
- `TaskMapper` / `ArtifactMapper` / `CaseResultMapper` / `TaskStageLogMapper`

---

## 13. 数据库与持久化设计

### 13.1 为什么选 MyBatis + Flyway

项目没有用 JPA，而是使用注解式 MyBatis。这样做的优点是：

- SQL 可控，适合结果查询和分页
- 表结构和 SQL 关系清晰
- 对执行平台这种偏后台管理 + 结果聚合系统来说更直接

Flyway 负责数据库 schema 演进，好处是：

- 不依赖手工建表
- 环境一致性更好
- 可以追踪数据库版本变更

关键文件：

- `src/main/resources/db/migration/V1__init_schema.sql`
- `src/main/resources/db/schema/SCHEMA_OVERVIEW.sql`

### 13.2 数据库表怎么讲

- `test_repository`：测试仓库元数据
- `scene`：测试场景元数据
- `platform_user`：平台用户
- `user_session`：登录态
- `space`：空间主表
- `space_member`：空间成员与角色
- `space_access_request`：申请加入/升级权限审批流
- `task`：任务主记录，保存状态机信息和解析后的执行参数
- `case_result`：测试用例明细
- `artifact`：产物元数据
- `task_stage_log`：各阶段日志
- `scene_schedule_state`：定时调度状态和租约
- `platform_audit_log`：操作审计日志

这套表设计能体现你理解了“身份数据”“配置数据”和“运行数据”的区别：

- 用户、session、空间、成员关系属于身份与协作数据
- 仓库、场景属于配置数据
- task、case_result、artifact、task_stage_log 属于运行数据

---

## 14. Redis 在这个项目里做什么

不是拿 Redis 当主存储，而是做两个补充职责：

### 14.1 详情缓存

`DetailCacheService` 给仓库详情、场景详情这类读多写少接口做缓存。

特点：

- 缓存正常值
- 缓存空值
- TTL 抖动
- Redis 互斥锁
- 本地进程锁

### 14.2 调度辅助

`scene_schedule_state` + 租约服务用于保证定时任务不会重复触发。

你可以这样说：

> Redis 在这个项目里不是业务主数据库，而是提升热点详情读取稳定性、辅助高并发下缓存控制和调度控制。

---

## 15. MinIO 在这个项目里做什么

MinIO 是对象存储，负责保存：

- 截图
- 视频
- Trace
- 阶段日志
- 其他报告文件

为什么不把这些东西直接存 MySQL：

- 文件体积大，不适合关系型数据库
- 对象存储更适合下载、预览、长期归档
- 数据库只需要保存元信息，例如 bucket、objectKey、contentType、url

面试时可以强调：

> 这个项目对“结构化数据”和“二进制产物”做了分离：结构化数据放 MySQL，文件产物放 MinIO。

---

## 16. Docker Runner 设计怎么讲

这个项目一个很像样的点，是把测试执行环境抽象成了 Runner。

### 16.1 两种模式

- `local`：本地执行
- `docker`：Docker 容器执行

配置入口在：

- `application.yml`
- `RunnerCommandExecutorConfig.java`

### 16.2 为什么要抽象 Runner

因为平台执行的是“外部测试仓库命令”，这类命令有几个问题：

- 依赖环境复杂
- 不同仓库依赖可能冲突
- 直接在服务进程所在机器运行风险大

所以抽象出 Runner 后：

- 本地开发可以用 `local`
- 更安全的执行环境可以用 `docker`

这是一个典型的“策略模式 + 基础设施隔离”的工程设计。

### 16.3 Docker 模式的优点

- 隔离性更好
- 环境一致性更好
- 更适合执行第三方测试代码

### 16.4 Docker 模式的风险

仓库里也明确说明了：

- 服务容器挂载了 `/var/run/docker.sock`
- 这意味着服务具备较高 Docker 控制权限

所以你可以补一句：

> 当前方案偏向受控环境部署，优先保证执行能力和研发效率；如果进一步产品化，可以考虑把 Runner 独立成专门执行节点或任务代理服务。

这句话会显得你对工程风险有判断。

---

## 17. `docker-compose.yml` 怎么理解

`docker-compose.yml` 是本地开发环境编排。

它启动了 5 个核心服务：

- `mysql`
- `redis`
- `minio`
- `server`
- `web`

### 17.1 它解决的问题

不是为了“线上高可用”，而是为了让开发环境一键跑起来。

开发机只要有 Docker，就可以统一起：

- 数据库
- 缓存
- 对象存储
- 前端开发容器
- 后端开发容器

### 17.2 关键特点

#### 1. 依赖服务有健康检查

MySQL、Redis、MinIO 都做了 healthcheck，`server` 会等依赖健康后再启动。

#### 2. 用 `.env` 管理本地私有配置

比如：

- 数据库密码
- Redis 密码
- MinIO 账号密码
- 端口映射
- Runner 工作区路径

#### 3. 开发容器挂载源码目录

- `server` 挂载 `./playwright-platform-server:/workspace`
- `web` 挂载 `./playwright-platform-web:/workspace`

这让开发环境更适合热更新和本地修改。

#### 4. 后端容器挂载 Docker Socket

为了让平台在 Docker Runner 模式下再拉起短生命周期测试容器。

#### 5. 前端通过 Vite 代理 `/api`

`VITE_API_PROXY_TARGET` 默认指向 `server:8080`，让前端开发时避免跨域问题。

### 17.3 你可以怎么讲

> 开发环境用 Compose 的目标是标准化本地依赖，降低新人接入成本，同时让前后端和 MySQL、Redis、MinIO 在一套隔离网络里协同工作。它更偏本地研发环境编排，而不是最终线上编排方案。

---

## 18. `docker-compose.prod.yml` 怎么理解

这个文件是单机生产部署版本。

与开发版相比，核心差异是：

- 使用生产 Dockerfile，而不是开发 Dockerfile
- `server` 使用 `prod` profile
- `web` 用 Nginx 提供静态资源
- 不再挂载源码目录
- 服务增加 `restart: unless-stopped`

### 18.1 生产镜像设计

后端：

- `playwright-platform-server/Dockerfile`
- 多阶段构建
- Maven 打包 jar
- 最终只放 JRE + app.jar

前端：

- `playwright-platform-web/Dockerfile`
- 第一阶段 `npm ci && npm run build`
- 第二阶段 Nginx 托管 `dist`

### 18.2 为什么这样设计

因为生产环境不需要：

- Maven
- Node 构建工具链
- 源码挂载

只需要稳定的运行产物。

这就是典型的“构建环境”和“运行环境”分离。

---

## 19. 前端 Nginx 和 Vite 配置怎么讲

### 19.1 `vite.config.ts`

开发阶段负责：

- Vue 插件
- Element Plus 自动导入
- `/api` 代理到后端
- Vitest 配置
- 构建分包策略 `manualChunks`

面试时可以提一句：

> 我们前端开发环境使用 Vite 代理 `/api`，避免本地跨域；生产环境则由 Nginx 把 `/api` 反代到后端。

### 19.2 `nginx.conf`

生产阶段负责：

- 静态资源托管
- SPA 路由回退到 `index.html`
- `/api/` 反向代理到 `server:8080`
- `/assets/` 长缓存

这是一个标准但实用的单页应用部署方案。

---

## 20. CI 怎么理解

CI 文件在：

- `.github/workflows/ci.yml`

它由 GitHub Actions 执行，在 `push` 和 `pull_request` 时触发。

### 20.1 CI 分成两个 Job

#### backend job

- 切到 `playwright-platform-server`
- 安装 Java 21
- 缓存 Maven
- 执行 `mvn test`
- 生成 JaCoCo 覆盖率报告
- 上传 coverage artifact

#### frontend job

- 切到 `playwright-platform-web`
- 安装 Node.js 20
- 缓存 npm
- 执行 `npm ci`
- 执行 `npm test -- --coverage`
- 上传前端 coverage artifact
- 执行 `npm run build`
- 执行 `npm audit --audit-level=high`，但失败不阻塞流水线

### 20.2 这个 CI 体现了什么工程意识

- 前后端独立验证
- 既测单元测试，也验证构建
- 保留覆盖率产物，方便后续查看
- 安全审计先做信息提示，不直接卡死交付

### 20.3 它还缺什么

这是你可以主动加分的地方。当前 CI 做得已经不错，但还可以继续增强：

- 没有集成测试或端到端测试
- 没有自动构建并发布 Docker 镜像
- 没有部署阶段
- 没有质量门禁，例如覆盖率阈值

你可以这样回答：

> 当前 CI 重点是代码层面的快速反馈，包括单测、覆盖率和前端构建验证；如果项目继续往团队化使用方向发展，我会补 Docker 镜像构建、镜像扫描、部署流水线和环境级回归验证。

---

## 21. 测试体系怎么讲

### 21.1 后端测试

后端测试在 `playwright-platform-server/src/test/java`，覆盖：

- Mapper 层测试
- Controller 层测试
- Service 层测试
- Runner 相关测试
- 缓存测试
- MinIO 对象存储测试
- 事务边界测试

这说明后端测试不是只测一层，而是覆盖了核心行为。

### 21.2 前端测试

前端测试在 `playwright-platform-web/tests/unit`，覆盖：

- 登录/注册
- auth/space/task 等 store
- 侧边栏用户区、空间切换、错误处理
- 工具函数
- Task Detail 加载逻辑
- Task List 生命周期
- UI 交互相关逻辑

### 21.3 你可以怎么总结

> 这个项目的测试策略是“后端偏服务与基础设施行为验证，前端偏状态逻辑与页面逻辑验证”，再通过 CI 把测试和覆盖率自动化。

---

## 22. 这个项目的几个工程亮点

### 22.1 异步任务编排

任务创建和任务执行解耦，Web 层只负责入队，不直接长时间阻塞。

### 22.2 Runner 抽象

本地执行和 Docker 执行可以通过统一接口切换，方便开发和部署场景兼容。

### 22.3 对象存储归档

数据库不硬扛二进制产物，结构化元数据和大文件分离。

### 22.4 调度租约

定时触发考虑了重复执行风险，不是简单 `@Scheduled` 里直接下发任务。

### 22.5 缓存设计比较完整

不是简单 `get/set`，而是考虑了穿透、击穿、雪崩。

### 22.6 可观测性意识

- requestId / traceId
- 阶段日志
- 应用错误汇总
- 任务诊断接口

---

## 23. 你可以主动承认的不足和优化方向

这部分非常适合在面试里体现成熟度。

### 23.1 当前不足

- Docker socket 权限较高
- CI 还没覆盖镜像构建和自动部署
- 调度能力还偏单机或轻量多实例模式
- 前端详情页主要靠多接口拼装，后续可以考虑更聚合的后端接口

### 23.2 可演进方向

- 把 Runner 独立为执行节点服务
- 引入消息队列做任务分发
- 增加镜像构建、镜像扫描和 CD 流程
- 增加任务重试策略和更细粒度状态机
- 引入 Prometheus/Grafana 做任务和基础设施监控

这会让你显得不是“只会复述现状”，而是能看出下一步怎么做。

---

## 24. 面试时可直接使用的项目介绍稿

### 24.1 偏业务视角

我做的是一个带空间协作能力的 Playwright 自动化测试平台，主要解决测试执行过程分散、权限边界不清、结果和产物不方便统一管理的问题。用户可以自助注册，系统会自动创建个人空间并让用户成为自己空间的管理员。进入空间后，用户可以维护测试仓库和测试场景，配置分支、浏览器、环境变量和执行命令，然后通过手动或定时方式触发任务。后端负责拉代码、执行安装和测试命令、解析结果文件，并把截图、视频、Trace、日志归档到对象存储，前端统一展示任务状态和明细结果。

### 24.2 偏技术视角

技术上这是一个前后端分离系统。前端使用 Vue 3 + TypeScript + Pinia，后端使用 Spring Boot + MyBatis + Flyway。MySQL 存结构化业务数据，包括用户、空间、仓库、场景、任务和审批流；Redis 负责详情缓存和部分调度辅助；MinIO 存测试产物和头像对象。任务执行不是同步阻塞接口，而是通过专门线程池异步执行，并且把本地执行和 Docker 执行封装成统一 Runner 接口。项目还配了 Docker Compose 开发和生产编排，以及 GitHub Actions 做前后端测试、覆盖率和构建校验。

### 24.3 偏你个人贡献的说法

如果面试官问“你负责了什么”，建议按下面结构答：

1. 我负责理解业务流程，把仓库、场景、任务这三个核心对象串起来
2. 我参与了前后端关键链路，比如任务执行、结果展示、日志和产物查看
3. 我关注了工程化问题，比如 Docker Compose、CI、覆盖率、异常处理、缓存和执行环境隔离
4. 我不仅实现功能，也会考虑如何保证系统可维护、可部署、可扩展

---

## 25. 高频面试追问与回答思路

### Q1：这个项目最核心的业务链路是什么？

答：
如果从产品闭环讲，有两条主链路：第一条是“用户注册 -> 自动创建个人空间 -> 直接进入空间”；第二条是“从场景触发任务 -> 异步执行测试仓库代码 -> 解析结果 -> 归档产物 -> 前端展示任务详情”。这样回答能体现你既理解平台入驻和权限边界，也理解测试执行主流程。

### Q2：为什么任务执行要异步，而不是接口里直接跑？

答：
因为 Playwright 执行时间长，而且还涉及安装依赖、拉代码、上传产物等外部 IO。如果放在请求线程里，接口会超时，也会占住 Web 线程池。异步化后，请求层只负责创建任务和返回结果，执行层在专门线程池里处理长流程。

### Q3：为什么用 MyBatis，不用 JPA？

答：
这个项目数据模型清晰，但查询和持久化更偏控制型，尤其任务列表、详情聚合、分页、结果表等更适合直接掌控 SQL。MyBatis 更轻、更透明，便于精确控制查询和更新行为。

### Q4：Redis 在这里的价值是什么？

答：
主要不是主存储，而是做详情缓存和热点保护。项目里详情缓存不仅有 TTL，还有空值缓存、抖动和互斥锁，说明不是为了“用了 Redis”而用，而是针对读多写少和高并发穿透场景做设计。

### Q5：为什么要引入 MinIO？

答：
因为测试产物像截图、视频、Trace 都是文件，不适合存数据库。数据库只存元信息，对象存储保存大文件，这样职责划分更合理。

### Q6：为什么要有 Docker Runner？

答：
因为平台执行的是外部测试仓库命令，这些命令依赖复杂而且有一定风险。Docker Runner 可以隔离环境，减少依赖冲突，也更接近真实执行节点。

### Q7：定时调度怎么防止重复执行？

答：
项目不是简单每分钟扫描后直接执行，而是通过调度状态和租约机制控制，同一时刻只有拿到租约的实例会创建任务。

### Q8：这个项目的前端有什么值得讲的？

答：
前端结构比较规整，按 API、Store、View、Type 划分。除了仓库、场景、任务，还实现了登录/注册双态页面、空间广场、空间审批、侧边栏用户区和空间切换。任务详情页考虑了部分失败容忍，用 `Promise.allSettled` 加载多个子资源，保证单个接口失败不一定拖垮整个详情页。

### Q9：CI 做了什么？

答：
CI 目前做了前后端单元测试、覆盖率产物上传和前端构建校验，重点是快速反馈代码质量。下一步可以继续补 Docker 镜像构建、镜像扫描和 CD。

### Q10：如果你继续优化这个项目，你会先做什么？

答：
我会先从执行环境安全和交付链路两块入手：一块是把 Runner 执行进一步隔离，减少对 Docker socket 的依赖；另一块是补完整镜像构建和部署流水线，把 CI 扩展到更完整的 CD。

---

## 26. 面试时要避免的讲法

### 不要只报技术栈

错误讲法：

> 这个项目用了 Vue、Spring Boot、MySQL、Redis、Docker。

这种回答没有信息量。

### 要讲“为什么这样设计”

更好的讲法：

> 前端用 Vue 3 + Pinia 做后台管理和状态管理；后端用 Spring Boot + MyBatis 管理仓库、场景和任务；MySQL 存结构化数据，MinIO 存测试产物；任务执行用异步线程池和 Runner 抽象，避免阻塞请求线程并支持本地/容器化执行。

### 不要把功能讲成页面清单

面试官更想听的是：

- 核心对象是什么
- 核心链路是什么
- 关键设计点是什么
- 风险和优化点是什么

---

## 27. 最后一段总结

如果你只记一段话，记下面这段就够了：

> 这个项目本质上是一个 Playwright 自动化测试执行平台。它以仓库、场景、任务为核心对象，前端负责配置管理和结果展示，后端负责异步任务编排、定时调度、执行环境抽象、结果解析和产物归档。MySQL 保存结构化业务数据，Redis 提供详情缓存和调度辅助，MinIO 存储截图、视频、Trace 等运行产物。工程上用 Docker Compose 管理开发和单机生产环境，用 GitHub Actions 做前后端测试、覆盖率和构建校验。相比普通 CRUD 项目，它更能体现我对异步执行、对象存储、缓存设计、部署和 CI 的整体理解。
> 这个项目本质上是一个带空间协作能力的 Playwright 自动化测试执行平台。它以用户、空间、仓库、场景、任务为核心对象，前端负责登录注册、空间切换、配置管理和结果展示，后端负责认证与空间模型、异步任务编排、定时调度、执行环境抽象、结果解析和产物归档。MySQL 保存用户、空间、配置和任务等结构化数据，Redis 提供详情缓存和调度辅助，MinIO 存储截图、视频、Trace、阶段日志和头像等对象。工程上用 Docker Compose 管理开发和单机生产环境，用 GitHub Actions 做前后端测试、覆盖率和构建校验。相比普通 CRUD 项目，它更能体现我对权限边界、异步执行、对象存储、缓存设计、部署和 CI 的整体理解。

---

## 28. 面试前建议你重点复习的文件

如果时间有限，优先看这些文件：

- 后端
  - `playwright-platform-server/src/main/java/com/example/platform/PlatformApplication.java`
  - `playwright-platform-server/src/main/java/com/example/platform/auth/controller/AuthController.java`
  - `playwright-platform-server/src/main/java/com/example/platform/auth/service/AuthServiceImpl.java`
  - `playwright-platform-server/src/main/java/com/example/platform/space/service/SpaceServiceImpl.java`
  - `playwright-platform-server/src/main/java/com/example/platform/space/service/SpaceAccessRequestServiceImpl.java`
  - `playwright-platform-server/src/main/java/com/example/platform/task/controller/TaskController.java`
  - `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskServiceImpl.java`
  - `playwright-platform-server/src/main/java/com/example/platform/task/service/TaskExecutionOrchestrator.java`
  - `playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneServiceImpl.java`
  - `playwright-platform-server/src/main/java/com/example/platform/runner/service/RunnerCommandExecutorConfig.java`
  - `playwright-platform-server/src/main/java/com/example/platform/cache/DetailCacheService.java`
  - `playwright-platform-server/src/main/resources/application.yml`
  - `playwright-platform-server/src/main/resources/db/migration/V1__init_schema.sql`

- 前端
  - `playwright-platform-web/src/main.ts`
  - `playwright-platform-web/src/App.vue`
  - `playwright-platform-web/src/router/index.ts`
  - `playwright-platform-web/src/stores/auth.ts`
  - `playwright-platform-web/src/stores/space.ts`
  - `playwright-platform-web/src/api/http.ts`
  - `playwright-platform-web/src/api/auth.ts`
  - `playwright-platform-web/src/api/space.ts`
  - `playwright-platform-web/src/stores/task.ts`
  - `playwright-platform-web/src/views/auth/LoginView.vue`
  - `playwright-platform-web/src/views/home/HomeView.vue`
  - `playwright-platform-web/src/views/scene/SceneListView.vue`
  - `playwright-platform-web/src/views/space/SpaceAccessRequestListView.vue`
  - `playwright-platform-web/src/views/task/TaskDetailView.vue`
  - `playwright-platform-web/vite.config.ts`

- 工程化
  - `docker-compose.yml`
  - `docker-compose.prod.yml`
  - `playwright-platform-server/Dockerfile`
  - `playwright-platform-web/Dockerfile`
  - `.github/workflows/ci.yml`
  - `docs/deployment.md`

把这些串起来，基本就能把项目讲完整。
