# Playbook · 模型评测 - 在线监控

## 1. 业务定位

在线监控面向真实生产流量：通过 Langfuse 采集 Agent 的每次调用 Trace，在平台内按 Agent、时间、traceId、成功/失败筛选查看，并按策略对低质量回答自动扫描、评分与聚合，形成延迟、Token 与评分的监控视图。

适用场景：

- 线上回答质量巡检（抽样 / 定时扫描）
- 故障排查：按 traceId 回放一次真实请求
- 成本与延迟观察（P50 / P95、Token 用量）
- 与离线评测对齐：同一条用例在线上表现如何

## 2. 前置条件与权限

- 需要在部署环境启动 Langfuse（`docker-compose.yml` 已包含 Langfuse 自托管组件），并在 `.env` 配置 Langfuse 相关变量。
- 目标 Agent 必须已「接入 Trace」（生成 SDK 密钥并配置到被测服务）。
- 空间成员可查看；策略新建 / 启停建议由评测负责人操作。

## 3. 功能与操作说明

页面路径：`/spaces/{id}/evaluation/monitor`，包含两个 Tab。

### 3.1 Trace 浏览器

| 功能 | 说明 |
|------|------|
| Agent 筛选 | 默认「全部 Agent」，可按单个 Target 过滤 |
| 日期范围 | 起始 / 结束日期（按天） |
| Trace ID 搜索 | 精确查找单条链路 |
| 状态筛选 | 全部 / 成功 / 失败 |
| 列表列 | 时间、名称、Trace ID、输入、输出、延迟（s）、操作 |
| 查看链路 | 打开 Trace 详情抽屉（Observation 树、模型调用、Token、耗时） |

### 3.2 在线策略

| 功能 | 说明 |
|------|------|
| 策略列表 | 名称、目标 Agent、状态、间隔、下次扫描 |
| 新建策略 | 策略名称、目标 Agent、扫描间隔、回看小时数、单次最多扫描、低分阈值 |
| 立即扫描 | 手动触发一次扫描 |
| 暂停 / 恢复 | 切换 `ACTIVE` / `PAUSED` |
| 删除 | 删除策略，不影响已产生的评分记录 |

### 3.3 监控指标

页面顶部展示聚合指标：调用量、P50 / P95 延迟、Token 用量、自动评分分布（低分占比）。

## 4. 关键规则与限制

| 规则 | 说明 |
|------|------|
| 扫描间隔 | 默认 5 分钟；可按策略调整 |
| 回看窗口 | 每次扫描回看最近 N 小时，避免重复扫历史 |
| 单次扫描上限 | 防止一次拉取过多 Trace 压垮 Langfuse |
| 低分阈值 | 低于阈值的 Trace 进入待复核集合 |
| 状态机 | 策略仅 `ACTIVE` / `PAUSED` |
| 数据来源 | Trace 与评分均以 Langfuse 为准，平台不重复存储原始链路 |
| 上游继承 | Agent 调用需携带 `traceparent`，trace-id 与 Langfuse 双向绑定 |
| 保留策略 | Trace 保留由 Langfuse 项目配置决定，平台不额外清理 |
| A/B 实验 | 后端已提供分流与显著性分析组件（`evaluation/abtest`），当前无独立前端页面，属于路线图能力 |

## 5. 典型流程：建立客服回答质量巡检

1. 在「Agent 管理」给客服 Agent 点「接入 Trace」，把密钥配置到线上服务。
2. 在「在线监控 → Trace 浏览器」确认能看到真实调用。
3. 切到「在线策略」，新建策略：选择客服 Agent，间隔 5 分钟，回看 24 小时，低分阈值 0.6。
4. 保存后策略默认运行，观察「下次扫描」与低分 Trace。
5. 对低分样本在离线评测集补充对应用例，形成「线上发现 → 离线回归」闭环。

## 6. 常见问题

| 现象 | 排查 |
|------|------|
| Trace 浏览器为空 | Agent 未接入 Trace / 密钥错误 / Langfuse 不可用 |
| 日期筛选无数据 | 时区与日期边界；确认 Langfuse 中同一时间窗有记录 |
| 策略不扫描 | 状态为 PAUSED，或 `nextScanAt` 未到 |
| 扫描重复计分 | 同一 Trace 被多个策略覆盖，属预期，按策略维度查看 |
| 延迟/Token 指标缺失 | 上游未返回 usage，或 Trace 未记录模型事件 |

## 7. 相关接口与代码位置

- 后端：`evaluation/online/OnlineTraceController`、`OnlinePolicyController`、`OnlineMonitorController`、`OnlinePolicyScanner`、`OnlinePolicyEngine`、`evaluation/langfuse/*`
- 前端：`views/evaluation/OnlineMonitorView.vue`、`components/evaluation/TraceDetailDrawer.vue`
- 数据表：`eval_online_policy`、`eval_online_scoring`、`eval_ab_experiment`、`eval_ab_assignment`
- 部署：`docker-compose.yml` 中 `langfuse-web` / `langfuse-worker` / `langfuse-postgres` / `langfuse-clickhouse` / `langfuse-redis`
