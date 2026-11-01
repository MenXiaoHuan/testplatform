# Playbook · 模型评测 - 被评目标（Agent 管理）

## 1. 业务定位

「被评目标（Target）」是评测系统统一建模的被测对象。无论被测的是大模型 API、自建 HTTP Agent，还是平台内置 Agent，都通过同一套 Target 配置接入，从而复用评测集、评分器、离线批跑与在线监控。

适用场景：

- 接入 DeepSeek / OpenAI 兼容的 LLM 接口
- 接入自建 Agent 的 HTTP 服务
- 让外部 Agent 上报 Trace（在线监控）
- 对内置 Agent 做回归评测

## 2. 前置条件与权限

- 空间成员可查看；新增 / 编辑 / 删除建议由评测负责人操作。
- HTTP Agent 需要服务端可访问目标地址（会做 SSRF 校验，内网地址可能被拒绝）。

## 3. 功能与操作说明

| 功能 | 操作路径 | 说明 |
|------|----------|------|
| Agent 列表 | `/spaces/{id}/evaluation/targets` | 展示名称、类型、状态、能力、连接配置 |
| 新增 Agent | 右上角「新增 Agent」 | 按类型填写不同配置 |
| 一键示例 | 弹窗「一键填入示例配置」 | 按当前类型填充可运行的示例值 |
| 编辑 | 行内「编辑」 | 修改连接配置与状态 |
| 测试 | 行内「测试」 | 仅具备离线调用能力的 Agent 可健康检查 |
| 接入 Trace | 行内「接入 Trace」 | 生成 SDK 密钥，供外部 Agent 上报 |
| 删除 | 行内「删除」 | 已产生评测结果时建议先保留，避免历史不可追溯 |

### 三种目标类型

| 类型 | 适用 | 关键配置 |
|------|------|----------|
| `LLM_API` | OpenAI 兼容的模型接口 | Base URL、模型名、API Key、请求路径、Temperature、Max Tokens |
| `HTTP_AGENT` | 自建 Agent 服务 | 离线评测 Endpoint（留空则仅在线 Trace）、请求方法、答案 JSONPath、请求头、请求体模板 |
| `BUILTIN_AGENT` | 平台内置 Agent | 选择内置 Agent key |

### 敏感配置

- API Key / Header 值支持 `REF:ENV_NAME` 形式，从服务端环境变量读取，避免把密钥写进数据库明文。
- 外部 Agent 上报 Trace 使用平台生成的密钥对，可在「接入 Trace」中查看与轮换。

## 4. 关键规则与限制

| 规则 | 说明 |
|------|------|
| 名称唯一 | 同一空间内 Agent 名称唯一 |
| 能力区分 | 未配置离线 Endpoint 的 `HTTP_AGENT` 只能做在线 Trace，不能参与离线评测 |
| SSRF 防护 | Endpoint / Base URL 必须通过 SSRF 校验，禁止内网探测 |
| 健康检查 | 调用 `/api/evaluation/targets/{id}/health-check`，失败会返回具体错误 |
| 状态 | 停用后不能新建评测运行，但历史结果保留 |
| 密钥轮换 | 轮换后旧密钥立即失效，外部 Agent 需同步更新 |
| 统一类型 | 外部 Agent 统一落为 `HTTP_AGENT`，在线与离线共用同一个 `target_id` |

## 5. 典型流程：接入一个自建 Agent

1. 在「Agent 管理」新增，类型选择 `HTTP_AGENT`。
2. 填写名称、离线评测 Endpoint 与请求方法（如 `POST`）。
3. 按被测服务的返回结构填写「答案 JSONPath」，如 `$.data.answer`。
4. 请求头中如需鉴权，使用 `REF:MY_AGENT_TOKEN` 引用服务端环境变量。
5. 保存后点「测试」，确认健康检查通过。
6. 需要在线监控时点「接入 Trace」，把生成的密钥配置到被测 Agent 的 SDK。

## 6. 常见问题

| 现象 | 排查 |
|------|------|
| 测试失败 401 / 403 | 鉴权头缺失或 `REF:` 引用的环境变量不存在 |
| 测试失败 SSRF | Endpoint 指向内网地址，按安全策略被拒绝 |
| 离线评测选不到该 Agent | 没有配置离线 Endpoint |
| 在线监控没有数据 | 未接入 Trace、密钥错误或 Langfuse 未配置 |
| 答案解析为空 | 答案 JSONPath 与被测服务返回结构不匹配 |

## 7. 相关接口与代码位置

- 后端：`evaluation/target/TargetController`、`evaluation/agent/AgentController`、`evaluation/agent/AgentIngestController`、`evaluation/agent/AgentTokenService`、`evaluation/target/*Adapter`
- 前端：`views/evaluation/TargetListView.vue`
- 数据表：`eval_target`、`eval_agent_connection`；迁移 `V11__evaluation_schema.sql`、`V12__agent_connection.sql`、`V13__merge_external_agent_into_http_agent.sql`
