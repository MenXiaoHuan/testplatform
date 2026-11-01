# Playbook · AI 智能助手

## 1. 业务定位

AI 助手是基于 ReAct 循环的排障与咨询入口：它能读取当前空间的仓库、场景、任务与日志，按需调用工具，用结构化内容回答「任务为什么失败」「这个场景怎么配」等问题。

适用场景：

- 失败任务根因分析（结合阶段日志与用例结果）
- 场景 / 仓库配置咨询
- 平台使用答疑（内置 skills 提供业务知识）
- 评测结果与 Prompt 调试建议

## 2. 前置条件与权限

- 需要登录；助手默认继承当前空间上下文，只能看到当前用户有权限的数据。
- 服务端需配置 `PLATFORM_DEEPSEEK_API_KEY`，否则对话会直接报错。

## 3. 功能与操作说明

| 功能 | 操作 | 说明 |
|------|------|------|
| 打开助手 | 页面右下角悬浮按钮 | 全局可用，不离开当前页面 |
| 快捷问题 | 对话框顶部推荐问题 | 一键填入常见排障问题 |
| 发送消息 | 输入框回车 / 发送按钮 | 流式返回，支持中途停止 |
| 停止生成 | 生成中的「停止」按钮 | 中断当前 SSE 流 |
| 清空对话 | 对话框「清空」 | 重置当前会话上下文 |
| 任务上下文分析 | 任务详情页「AI 分析」 | 自动携带任务、场景、阶段日志 |

## 4. 关键规则与限制

| 规则 | 默认值 / 说明 |
|------|--------------|
| 单用户每日调用 | 50 次（`PLATFORM_AI_DAILY_LIMIT`） |
| 全局并发 | 4 路（`PLATFORM_AI_MAX_CONCURRENT`） |
| 单次对话模型调用 | 最多 20 次，超过熔断 |
| 单次模型超时 | 60 秒，最多重试 2 次 |
| 上下文压缩 | Token 超过上限 80% 或消息数超过 50 时触发；保留最近 3 条并摘要历史 |
| 单条消息截断 | 4000 字符，超出部分截断 |
| 输出格式 | sections-only 结构化块（heading / paragraph / list / code / quote / table） |
| 内置工具 | TaskTool、SceneTool、RepositoryTool、LogPreprocessingTool |
| Skills | 启动时只加载索引，模型判断需要时再加载正文（error-analysis / business-knowledge） |
| 会话存储 | 本地会话缓存，默认 30 分钟过期；多实例部署需迁移到 Redis |
| Trace | 全链路写入 Langfuse，可在「模型评测 → 在线监控」按 traceId 查看 |

## 5. 典型流程：分析一个失败任务

1. 打开失败任务详情，点「AI 分析」。
2. 助手读取任务状态、当前阶段、阶段日志与失败用例。
3. 必要时调用 TaskTool 查询相邻任务、调用 LogPreprocessingTool 压缩日志。
4. 输出结构化结论：失败阶段 → 直接原因 → 证据 → 修复建议。
5. 按建议修正仓库命令 / 场景配置 / 测试代码后「重新执行」。

## 6. 常见问题

| 现象 | 排查 |
|------|------|
| 提示「今日 AI 调用次数已达上限」 | 等待次日重置或调整 `PLATFORM_AI_DAILY_LIMIT` |
| 提示「当前繁忙」 | 全局并发已满，稍后重试 |
| 回答与当前空间无关 | 确认已切换到目标空间再打开助手 |
| 一直无响应后超时 | 模型接口不可达 / API Key 无效，检查服务端日志 |
| 看不到 AI 给的数据 | 助手只读取当前用户有权限的数据，权限不足时不会越权返回 |
| 多实例下会话丢失 | 本地会话不共享，按 DEPLOYMENT 的多实例改造迁移会话存储 |

## 7. 相关接口与代码位置

- 后端：`ai/controller/AgentController`、`ai/service/AgentService`、`ai/context/ContextCompressionService`、`ai/quota/AgentQuotaService`、`ai/tools/*`、`ai/trace/AgentTraceRecorder`
- 前端：`components/ai/AiAssistantDialog.vue`、`stores/ai.ts`
- 配置：`platform.ai.*`、`platform.deepseek.*`
