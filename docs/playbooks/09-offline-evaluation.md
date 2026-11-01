# Playbook · 模型评测 - 离线评测（离线运行）

## 1. 业务定位

离线评测用固定评测集批量跑目标：给定「被测 Agent + 评测集 + 用例范围 + 并发数」，平台逐条调用目标、按评分器打分、汇总总分，并保留每条用例的输出、耗时、Token 与 Trace，用于版本对比与回归。

适用场景：

- Prompt 新版本上线前的回归验证
- 更换模型 / 调整 Agent 参数后的效果对比
- 版本间横向对照（同评测集、不同 Target）

## 2. 前置条件与权限

- 空间成员可查看运行记录；启动 / 重跑 / 删除建议由评测负责人操作。
- 目标 Agent 必须配置离线调用能力（`LLM_API` 或带离线 Endpoint 的 `HTTP_AGENT`）。
- 评测集至少包含一条用例。

## 3. 功能与操作说明

| 功能 | 操作路径 | 说明 |
|------|----------|------|
| 运行列表 | `/spaces/{id}/evaluation/runs` | 展示序号、Agent、评测集、状态、总分、创建时间 |
| 启动评测 | 右上角「+ 启动评测」 | 选择 Agent、评测集、用例范围、并发数 |
| 查看结果 | 行内「结果」 | 抽屉展示进度、总分与逐条用例结果 |
| 用例筛选 | 用例范围「全部 / 指定用例」 | 指定模式可打开用例选择器 |
| 重跑 | 行内「重跑」 | 复用原配置创建新运行 |
| 删除 | 行内「删除」 | 删除运行记录，不影响评测集与 Target |
| 链路查看 | 结果行「链路」 | 打开该用例的 Trace 详情 |

### 启动表单

| 字段 | 说明 |
|------|------|
| Agent | 仅列出具备离线能力的 Target |
| 评测集 | 显示版本与用例数，切换后刷新用例范围 |
| 用例范围 | 全部 / 指定用例 |
| 并发数 | 单次运行内并发，受全局与 per-Target 限流约束 |

### 结果抽屉

- 运行状态与进度（已完成 / 总数）
- 总分（各评分器加权）
- 用例表：输入、Agent 输出、得分、耗时、Token 入/出、状态、链路

## 4. 关键规则与限制

| 规则 | 说明 |
|------|------|
| 状态 | `PENDING` / `RUNNING` / `SUCCESS` / `FAILED` / `CANCELED` |
| 幂等 | 相同配置短时间内重复提交会被去重（`RUN_DEDUPLICATED`，409） |
| 并发隔离 | 全局并发墙 + per-Target 共享隔离，避免单个 Target 打满资源 |
| Judge 预算 | 三层预算：日预算 → 空间预算 → 分钟级 rateLimit；超限时降级或拒绝 |
| 评分器 | 规则匹配 / 关键词 / 精确匹配 / JSON Schema / LLM-as-Judge / Trace 快照 |
| 单条失败 | 单条调用失败不阻塞整批，结果中标记该条状态 |
| 版本冲突 | 同一运行并发更新返回 `RUN_VERSION_CONFLICT` |
| 删除 | 运行记录删除后不可恢复；历史对比请先导出结果 |

## 5. 典型流程：验证 Prompt 新版本

1. 在 Prompt 托管把新版本保存为 `DRAFT`。
2. 在「离线运行」启动评测：选择对应 Agent、业务回归评测集、全部用例。
3. 并发数先用默认值，观察耗时与 Token。
4. 运行结束后看总分与失败用例，点「链路」定位具体输出。
5. 与上一版本结果对比，确认提升后把 Prompt 激活为 `ACTIVE`。

## 6. 常见问题

| 现象 | 排查 |
|------|------|
| 启动按钮不可用 | 没有离线可用的 Agent，或评测集为空 |
| 提交返回 409 | 相同配置正在运行，去重拦截；等待完成后重试 |
| 运行长期 RUNNING | 目标接口慢 / 超时；看逐条用例状态与目标健康检查 |
| 总分异常低 | 评分器配置与参考答案不匹配，或 Judge 预算被拒 |
| 看不到 Token 数据 | 目标返回未提供 usage，平台按调用记录估算 |
| 重跑后结果不一致 | 模型本身有随机性（Temperature），建议固定参数对照 |

## 7. 相关接口与代码位置

- 后端：`evaluation/offline/OfflineEvaluationController`、`OfflineEvaluationOrchestrator`、`OfflineConcurrencyGuard`、`OfflineEvaluatorService`、`OfflineRunIdempotentService`、`evaluator/*`
- 前端：`views/evaluation/OfflineRunListView.vue`、`components/evaluation/TraceDetailDrawer.vue`
- 数据表：`eval_run`、`eval_run_case_result`；迁移 `V11__evaluation_schema.sql`
