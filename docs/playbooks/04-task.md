# Playbook · UI 自动化 - 任务执行、日志与产物

## 1. 业务定位

任务（Task）是一次真实的 Playwright 执行记录：由场景手动触发或定时触发产生，在 Docker Runner 短生命周期容器中运行，结束后归档阶段日志、截图、视频、Trace 与结构化用例结果。

适用场景：

- 定位一次失败回归的具体失败用例
- 下载失败截图 / 视频 / Trace 给开发复现
- 取消卡住的任务，或对失败任务重新执行
- 让 AI 助手分析失败原因

## 2. 前置条件与权限

- `OPERATOR` / `ADMIN` 可触发、取消、重跑任务；`VIEWER` 只读。
- 服务端 Docker Runner 预检必须通过（Docker CLI 可用、workspace 可写）。

## 3. 功能与操作说明

| 功能 | 操作路径 | 说明 |
|------|----------|------|
| 任务列表 | `/spaces/{id}/tasks` | 全部任务；按场景进入时为该场景任务 |
| 场景任务 | `/spaces/{id}/scenes/{sceneId}/tasks` | 「执行当前场景」可立即触发 |
| 任务详情 | 行内「查看」→ `/spaces/{id}/tasks/{taskId}` | 阶段、用例结果、日志、产物 |
| 取消任务 | 详情页「取消任务」 | 仅运行中的任务可取消，取消后状态为 CANCELED |
| 重新执行 | 详情页「重新执行」 | 以相同场景配置创建新任务 |
| AI 分析 | 详情页「AI 分析」 | 把任务上下文交给 AI 助手生成失败分析 |
| 查看 Trace | 用例结果中的「查看 Trace」 | 打开 Playwright Trace 查看器 |
| 下载产物 | 产物列表的下载 / 预览 | 截图、视频、Trace、阶段日志 |

### 任务列表关键列

| 列 | 含义 |
|----|------|
| 状态 | `QUEUED` / `RUNNING` / `SUCCESS` / `FAILED` / `CANCELED` |
| 触发方式 | 手动 / 定时 / AI 对话 |
| 当前阶段 | 任务执行到哪一步 |
| 结果归因 | 失败归类（如安装失败、测试失败、超时） |
| 耗时 | 从开始到结束的时长 |

### 任务详情结构

1. **概览**：状态、触发方式、开始/结束时间、耗时、场景与分支。
2. **阶段日志**：`PREPARING`（准备 workspace / 拉代码）、`INSTALLING`（装依赖）、`TESTING`（执行测试）、`ARCHIVING`（归档产物）。
3. **用例结果**：来自 Playwright JSON reporter 的用例级结果，含状态、耗时、错误摘要。
4. **产物**：截图、视频、Trace、报告下载链接（MinIO 预签名 URL，有效期约 1 小时）。

## 4. 关键规则与限制

| 规则 | 默认值 / 说明 |
|------|--------------|
| 执行线程池 | 核心 2、最大 4、队列 50（可通过 `PLATFORM_TASK_*` 调整） |
| 队列满 | 新任务直接失败并提示「任务执行队列已满」，不无限排队 |
| 安装超时 | 600 秒（`PLATFORM_TASK_INSTALL_TIMEOUT_SECONDS`） |
| 测试超时 | 1800 秒（`PLATFORM_TASK_TEST_TIMEOUT_SECONDS`） |
| 取消机制 | 通过 `cancelRequested` 标记轮询生效，非强制杀进程 |
| 单产物大小 | 默认 100MB（`PLATFORM_TASK_MAX_ARTIFACT_FILE_BYTES`） |
| 产物总量 | 默认 1GB（`PLATFORM_TASK_MAX_ARTIFACT_TOTAL_BYTES`） |
| 日志大小 | 测试日志默认上限 200MB（`PLATFORM_RUNNER_MAX_TEST_LOG_BYTES`） |
| 保留策略 | `PLATFORM_TASK_RETENTION_DAYS=0` 表示不自动清理；生产建议 90 天 |
| Runner 资源 | 默认 2GB 内存 / 2 CPU / bridge 网络（`PLATFORM_RUNNER_DOCKER_*`） |

> 任务状态由平台维护，**不要**手工改数据库状态；卡在 RUNNING 的孤儿任务由服务启动时的恢复逻辑处理。

## 5. 典型流程：定位一次失败回归

1. 在任务列表按「结果归因」筛选失败任务，打开详情。
2. 查看「当前阶段」：若停在 INSTALLING，多为依赖或网络问题；若停在 TESTING，进入用例结果。
3. 在用例结果中筛失败用例，点「查看 Trace」看失败步骤与截图。
4. 下载失败截图 / 视频，附到缺陷单。
5. 需要文字分析时点「AI 分析」，由助手结合阶段日志与用例结果给出原因假设。
6. 修复后点「重新执行」，复用原场景配置生成新任务。

## 6. 常见问题

| 现象 | 排查 |
|------|------|
| 一直 QUEUED | 线程池已满或 Runner 不可用；看队列容量与服务日志 |
| PREPARING 失败 | 仓库地址、分支、凭据问题；参考仓库 playbook |
| INSTALLING 超时 | 依赖安装慢；调整安装命令或调大超时 |
| TESTING 无结果 | reporter 未输出 JSON，或结果索引路径配置错误 |
| 产物下载 403 / 过期 | 预签名 URL 有效期约 1 小时，重新打开详情获取新链接 |
| 取消后仍显示 RUNNING | 取消为协作式，等待当前阶段结束或超时 |
| 服务重启后任务变 FAILED | 启动恢复逻辑会把遗留 RUNNING 标记失败，需重新执行 |

## 7. 相关接口与代码位置

- 后端：`task/controller/TaskController`、`task/service/TaskServiceImpl`、`task/service/TaskExecutionOrchestrator`、`task/service/TaskExecutionProperties`、`runner/service/DockerCommandBuilder`
- 前端：`views/task/TaskListView.vue`、`views/task/TaskDetailView.vue`、`components/evaluation/TraceDetailDrawer.vue`（Trace 查看）
- 数据表：`task`、`task_stage_log`、`task_artifact`、`case_result`
