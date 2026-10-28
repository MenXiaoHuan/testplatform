# PRD-03 · AI 智能助手模块

> 版本 v1.0 · 2026-10-28 · **对应现有代码模块**：`ai`（config / controller / service / session / output / tools / hook / skill / Aspect）
> 
> **v1.4 重要变更**：Prompt 托管中心引入后，SystemPromptHook 的 `classpath:AGENT.md` 首次启动时 seed 为 `promptKey=agent-system-prompt` 的 DRAFT 版本，用户发布 ACTIVE 后生效。

---

## 一、需求目标

### 1.1 为什么做

UI 自动化测试平台已搭建，但日常使用中存在三个高频痛点：

| 痛点 | AI 助手解决什么 |
|------|----------------|
| 任务失败后手动 ssh 进机器翻日志、查用例 | **故障排查**：「帮我分析任务 123 为什么失败了」→ Agent 自动调 TaskTool + LogPreprocessingTool，结构化输出故障类型、根因、解决方案 |
| 想知道"最近谁改了什么场景配置"、"空间里有哪些仓库"，要手动点好几级菜单 | **业务咨询**：「当前空间有哪些测试场景」「列出最近 5 个失败任务」→ Agent 直接调 SceneTool / TaskTool |
| Agent 自己在做什么、为什么这么回答完全不可观测 | **全链路追踪**：每次对话生成 traceId，90 天内可回溯完整 ReAct 循环（每次 model call + tool call 都有记录） |

### 1.2 能力边界

| 做 | 不做 |
|----|------|
| ReAct 循环推理（Think → Call Tool → Observe → Answer） | 多 Agent 协作（一个会话同时用多个 Agent） |
| 6 个内置工具（TaskTool、SceneTool、RepositoryTool、LogPreprocessingTool、TraceQueryTool、LoadSkillContentTool + LoadSkillDocumentTool） | 工具热插拔（要加工具必须重启） |
| Skills 按需加载（启动时只扫 frontmatter 生成索引，LLM 判断需要时再加载正文） | Skills 热插拔（新增 Skill 必须重启后端） |
| 上下文压缩（Smart 压缩 + Aggressive 压缩 + LLM 摘要优先 + 规则提取回退） | 向量数据库 RAG（当前 skills 是 Markdown 文档 + frontmatter 索引，不是向量检索） |
| sections-only 结构化输出（heading/paragraph/list/code/quote/table 六种块） + 从 sections 派生纯文本 | LLM 直接输出纯 Markdown 文本（已废弃 response 字段） |
| 全链路 Trace（Redis 存储 90 天 TTL + 多阶段结构化日志） | 外部 Jaeger / OpenTelemetry 集成（当前是自定义 Trace 方案） |
| SSE 流式返回（meta 事件 + chunk 事件 + complete 事件） | WebSocket 连接 |
| AgentObservability（调用量 / 错误率 / Token 用量监控） | 完整 Grafana Dashboard（当前只提供 API） |

### 1.3 成功标准

| 指标 | 目标 |
|------|------|
| 响应延迟 | 简单查询（如「当前空间有哪些场景」）首轮 Tool 调用返回 ≤ 3s |
| 故障排查成功率 | 「帮我分析任务 XXX 为什么失败」中，Agent 能正确识别 Playwright / Docker / 调度三大类故障之一的比例 ≥ 80% |
| Token 效率 | sections-only 输出比 response + sections 双写节省 ≥ 40% token |
| Trace 完整性 | traceId 对应的全链路阶段日志覆盖率 100%（无 MODEL_CALL / TOOL_CALL 阶段丢失） |
| 上下文控制 | 压缩触发后历史消息 token 数降至 maxTokens × 40% 以下 |
| 并发稳定性 | 10 个用户同时对话无崩溃；Agent 可并发数 ≥ 10 |

---

## 二、核心架构

### 2.1 总体架构图

```
用户对话框（AiAssistantDialog.vue）
  │
  │ SSE /api/ai/chat/stream
  ▼
AgentController（SseEmitter）
  │
  ▼
AgentService（对话主入口）
  │
  ├── 1. AgentTraceContext.setTraceId() → 生成唯一 traceId
  ├── 2. InputSanitizer.sanitize(message) → 输入清洗 + Prompt 注入检测
  ├── 3. ChatSessionManager.getOrCreateSession(sessionId) → 拿历史消息
  ├── 4. ContextCompressionService.compressIfNeeded(session) → 压缩历史
  ├── 5. AgentCallManager.call(ReactAgent, prompt) → ReAct 循环（含超时 + 重试）
  │       │
  │       │ ReAct 循环（最多 20 次模型调用）
  │       │   Think → 选 Tool → Call Tool → Observe → ... → Answer
  │       │
  │       ├── ModelCallTraceAspect（AOP）
  │       │     └── 拦截 ChatModel.call()，记录 MODEL_CALL_* 阶段 + 真实 token 用量
  │       └── ToolTraceAspect（AOP）
  │             └── 拦截 @Tool 方法，记录 TOOL_CALL_* 阶段 + 入参 + 结果
  │
  ├── 6. OutputFormatFallbackService.parseAgentOutput() → 四层兜底解析
  ├── 7. AgentTraceLogService.log(REQUEST_COMPLETED) → 全链路 Trace 落 Redis
  └── 8. 构建 SSE 事件序列 → SseEmitter.send()
```

### 2.2 ReAct 循环上限

| 限制 | 说明 |
|------|------|
| 最多 20 次模型调用 | `ModelCallLimitHook` 强制熔断，防止死循环 |
| 单次调用超时 60s | `AgentCallManager` 控制 |
| 失败重试 2 次 | 指数退避 1s → 2s → 4s |
| SSE 流超时 300s | `SseEmitter` 超时设置 |

### 2.3 上下文管理

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `max-tokens` | 8000 | 上下文 Token 硬上限 |
| `compression-threshold` | 0.8 | Smart 压缩触发阈值（8000 × 0.8 = 6400） |
| `keep-recent-messages` | 3 | 压缩后保留最近 N 条原始消息 |
| `use-llm-summary` | true | Smart 压缩优先用 LLM 生成结构化摘要（30s 超时） |
| Token 估算 | 中文 × 1.5 + 英文 × 0.25 | 启发式估算，与 LLM 真实用量对比（ModelCallTraceAspect 会记录差异） |

**压缩策略**：

```
promptTokens > maxTokens × 0.8（Smart 压缩）：
  ├── use-llm-summary=true → 先调 LLM 生成结构化摘要（30s 超时）
  │     └── 超时/失败 → 回退规则提取
  ├── 保留最近 3 条原始消息（user/assistant）
  └── 历史消息按轮次生成结构化摘要

promptTokens > maxTokens（Aggressive 压缩）：
  ├── 规则提取精简摘要
  └── 只保留最近 3 条原始消息

promptTokens 仍然超限：
  └── 截断所有历史消息 → 只保留最近 1 条 user 消息 + 1 条 assistant 回复的摘要
```

### 2.4 全链路 Trace 存储（Redis）

| Redis Key | 类型 | TTL | 内容 |
|-----------|------|-----|------|
| `agent:trace:{traceId}` | List<JSON> | 90 天 | 全链路阶段日志条目，按时间顺序 push |
| `agent:trace:index` | ZSet | 90 天 | score = timestamp，member = traceId，用于最近查询 |

**Trace 阶段分组**（固定顺序，前端按此分组渲染）：

```
0  REQUEST_RECEIVED          请求到达
1  SANITIZATION_FAILED       输入清洗失败（安全拦截）
2  SESSION_READY             会话就绪
3  CONTEXT_COMPRESSED        上下文压缩完成
4  CONTEXT_READY             上下文就绪
5  SYSTEM_PROMPT_LOADED      系统提示词加载完成
6  PROMPT_TOKEN_BUDGET       Token 预算计算
7  PROMPT_TRUNCATED          Token 超上限被截断
8  PROMPT_BUILT              最终 prompt 构建完成
9  INTENT_RECOGNIZED         意图识别完成
10 INTENT_LLM_CLASSIFYING    LLM 意图分类中
11 INTENT_LLM_CLASSIFIED/FAILED
12 PLAN_GENERATED/PLAN_SKIPPED
13 AGENT_CALL_STARTING       ReAct 循环开始
14 ReAct 阶段（按时间排序）
   ├── MODEL_CALL_STARTING
   ├── MODEL_CALL_COMPLETED/FAILED
   ├── TOOL_CALL_STARTING
   ├── TOOL_CALL_COMPLETED/FAILED
15 AGENT_CALL_SUCCESS/FAILED ReAct 循环结束
16 OUTPUT_PARSED             输出解析完成
17 OUTPUT_VALIDATED/OUTPUT_VALIDATED_WARNING
18 OUTPUT_PARSE_FALLBACK
19 REQUEST_COMPLETED         请求完成
20 UNEXPECTED_ERROR
```

---

## 三、Agent 工具集

### 3.1 内置工具

| 工具 | 方法 | 输入 | 输出 | 依赖 |
|------|------|------|------|------|
| **TaskTool** | `getTask(taskId, spaceId)` | taskId, spaceId | 任务详情（状态、结果、用例统计、阶段日志预览） | MySQL |
| | `listTasks(sceneId?, spaceId)` | spaceId, sceneId? | 空间下任务列表（sceneId 可选过滤） | MySQL |
| **SceneTool** | `listScenes(repoId?, spaceId)` | spaceId, repoId? | 空间下场景列表（repoId 可选过滤） | MySQL |
| | `getSceneDetail(sceneId, spaceId)` | sceneId, spaceId | 场景详情（浏览器、分支、选择器、环境变量） | MySQL |
| **RepositoryTool** | `getRepository(repositoryId, spaceId)` | repositoryId, spaceId | 仓库配置（Git 地址、默认分支、执行命令） | MySQL |
| | `searchRepository(keyword, spaceId)` | keyword, spaceId | 按名称关键词搜索仓库 | MySQL |
| **LogPreprocessingTool** | `analyzeLogs(taskId, spaceId)` | taskId, spaceId | 错误摘要 + 失败用例列表 | MySQL + MinIO |
| **TraceQueryTool** | `queryTrace(traceId)` | traceId | 完整调用链路摘要 | Redis |
| | `listRecentTraces(limit?)` | limit | 最近 Agent 调用记录（默认 10 条） | Redis |
| | `getTraceStats()` | — | Trace 存储统计（总数、保留策略） | Redis |
| **LoadSkillContentTool** | `loadSkill(name)` | skill 名称 | `SKILL.md` 正文 + 子文档清单 | classpath |
| **LoadSkillDocumentTool** | `loadSkillDocument(skillName, docName)` | skillName, docName | 子文档正文 | classpath |

### 3.2 工具统一安全约束

- 所有 TaskTool / SceneTool / RepositoryTool / LogPreprocessingTool / TraceQueryTool 调用**必须**传入 `spaceId` 参数
- `AgentService` 收到请求后校验当前用户是否为该 space 的成员（`SpaceAuthorizationService`）
- 非成员直接 403，不触发 Tool 执行

---

## 四、Skills 技能体系

### 4.1 按需加载模型

```
启动时：
  SkillIndexLoader 扫描 resources/skills/*/SKILL.md 的 YAML frontmatter
  只提取 name + description → 生成技能索引 → 追加到系统提示词末尾
  正文和子文档不进入上下文

LLM 判断需要某技能时：
  ReAct 循环中调用 loadSkill(name)
    → 读取 SKILL.md 正文
    → 返回正文 + 可用子文档清单
    → Agent 按需调用 loadSkillDocument(skillName, docName) 加载子文档
```

### 4.2 内置 Skills

| Skill | 路径 | 子文档 | 覆盖内容 |
|-------|------|--------|----------|
| `error-analysis` | `skills/error-analysis/` | `playwright-error.md`、`scheduler-error.md`、`docker-error.md`、`cache-db-error.md`、`llm-agent-error.md` | 平台故障排查 SOP（Playwright UI 异常、调度冲突、Docker、基础设施、LLM-Agent 评测五大类） |
| `business-knowledge` | `skills/business-knowledge/` | `tech-architecture.md`、`data-model.md`、`business-functions.md`、`llm-agent.md` | 业务知识（技术架构、数据模型、业务功能、AI Agent 评测） |

### 4.3 新增 Skill 的方式

1. 在 `playwright-platform-server/src/main/resources/skills/` 下新建目录（如 `new-skill/`）
2. 创建带 YAML frontmatter 的 `SKILL.md`
3. 可选：在该目录下创建子文档
4. **重启后端**（SkillIndexLoader 启动时扫描，运行时不热加载）

---

## 五、输出格式

### 5.1 sections-only 结构化输出

**LLM 只输出 sections 数组**，不再输出 response 纯文本。后端从 sections 派生纯文本。

```json
{
  "traceId": "agent-trace-xxxxxxxx",
  "usedTools": ["getTask", "analyzeLogs"],
  "confidence": "HIGH",
  "responseType": "ANALYSIS",
  "faultDetail": {
    "fault_type": "PLAYWRIGHT_UI_ERROR",
    "root_cause": "元素 #submit 未找到，可能页面加载超时",
    "immediate_solution": "提高 actionTimeout 到 30000ms",
    "long_term_optimize": "增加 Playwright 的 waitUntil: 'networkidle'",
    "test_risk": "中等，可能影响依赖该用例的回归测试",
    "reproduce_steps": "1. 打开页面 → 2. 点击按钮 → 3. 等待超时"
  },
  "sections": [
    {"type": "heading", "level": 2, "text": "故障分析"},
    {"type": "paragraph", "text": "任务 123 失败原因为 Playwright 元素定位异常"},
    {"type": "list", "items": ["元素 #submit 超时", "等待时间 5000ms", "实际页面加载用时 8000ms"], "ordered": false},
    {"type": "code", "language": "java", "code": "// 建议提高 timeout\nconfig.setActionTimeout(30000);"}
  ]
}
```

### 5.2 ContentBlock 六种类型

| type | 字段 | 说明 |
|------|------|------|
| `heading` | level(1/2/3), text | 标题 |
| `paragraph` | text | 段落 |
| `list` | items[], ordered | 有序/无序列表 |
| `code` | language, code | 代码块（前端 highlight.js 高亮） |
| `quote` | text | 引用块 |
| `table` | headers[], rows[][] | 表格 |

### 5.3 四层输出解析兜底（OutputFormatFallbackService）

```
Layer 1：BeanConverter
  → 直接把 LLM 返回的 JSON 反序列化为 ChatAssistantResult

Layer 2：JSON 提取（bracket balance 方法）
  → 从 LLM 返回的文本中提取第一个完整的 JSON 对象
  → 修复常见问题：多余的 ```json 包裹、前后有解释文字、JSON 字符串内有未转义引号

Layer 3：Lenient Parse + BeanConverter
  → 用 Jackson lenient 模式解析（允许单引号、注释、缺失值）

Layer 4：sections 重建 + 纯文本包装
  → 从文本中提取 heading/code/table 等结构化块重建 sections
  → 保证至少返回纯文本 response
```

### 5.4 SSE 流式事件序列

```
meta    → { sections: [...], traceId: "..." }  // 结构化 sections 供前端最终渲染
chunk   → "我来帮你分析..."                    // 逐字符纯文本，打字机效果
chunk   → "任务 123 的状态是"
chunk   → "FAILED"
...（N 次 chunk）
complete → "任务 123 失败原因是..."            // 完整纯文本
```

- `chunk` 事件的 content-type = `text/plain`（避免 JSON 序列化导致中文被转义）
- 前端三路分支渲染：
  1. 流式打字中 → `marked.parse(content)` + 保护表格 + 转义 `|` + DOMPurify
  2. 完成 + 有 sections → Vue `v-for` 按 type 结构化渲染（fade-in 动画）
  3. 完成 + 无 sections → 降级到 `marked.parse` + DOMPurify

---

## 六、API 接口

| 接口 | 方法 | 输入 | 输出 | 说明 |
|------|------|------|------|------|
| 同步对话 | `POST /api/ai/chat` | { spaceId, message, sessionId? } | ChatAssistantResult | 完整响应后返回，不走 SSE |
| **流式对话** | `POST /api/ai/chat/stream` | { spaceId, message, sessionId? } | SSE 事件流（meta → chunk×N → complete） | **前端主用**，打字机效果 |
| 清理会话 | `DELETE /api/ai/session/{sessionId}` | sessionId | void | 清除 Caffeine 中的会话缓存 |
| 活跃会话数 | `GET /api/ai/sessions/count` | — | { count } | AgentObservability 用 |
| **Trace 列表** | `GET /api/ai/trace` | limit? (默认 10, 上限 100) | 最近 Trace 摘要列表 | 前端「日志追踪」页面用 |
| **Trace 详情** | `GET /api/ai/trace/{traceId}` | traceId | 完整 Trace 时间线（按时间升序） | 前端「Agent Trace 时间线」页用 |

---

## 七、安全与可靠性

| 机制 | 参数 | 说明 |
|------|------|------|
| 输入长度限制 | 10000 字符 | 超长拒绝 |
| Prompt 注入检测 | InputSanitizer | 检测绕过系统约束的尝试 |
| 单次调用超时 | 60 秒 | AgentCallManager |
| 重试次数 | 2 次 | 指数退避 1s → 2s → 4s |
| 最大模型调用 | 20 次 | ModelCallLimitHook 防死循环 |
| SSE 流超时 | 300 秒 | SseEmitter |
| 会话过期 | 30 分钟 | Caffeine，touch() 更新访问时间 |
| 会话上限 | 10000 个 | Caffeine |
| Trace 保留 | 90 天 | Redis TTL |

---

## 八、验收方式

| 验收项 | 说明 |
|--------|------|
| AC1 简单查询 | 「当前空间有哪些测试场景」→ Agent 调 SceneTool → 返回场景列表；traceId 有值 |
| AC2 故障排查 | 「帮我分析任务 123 为什么失败了」→ Agent 调 TaskTool + LogPreprocessingTool → sections 中有故障详情（FaultDetail 非 null） |
| AC3 ReAct 循环 | 「列出最近 5 个失败任务并分析原因」→ 观察 Trace → Agent 至少调 2 次 TaskTool（list + 逐个分析） |
| AC4 工具权限隔离 | 把 spaceId 篡改为其他空间 → 工具返回空结果或 403（后端 SpaceAuthorizationService 拦截） |
| AC5 技能按需加载 | 首次对话不触发 loadSkill → Trace 中 LOAD_SKILL 阶段为 0；问一个 Playwright 故障问题 → Trace 中出现 loadSkill(playwright-error.md) |
| AC6 上下文压缩 | 50 轮对话后触发 Smart 压缩 → Trace 中 CONTEXT_COMPRESSED 阶段出现 → 后续对话继续正常（不丢失最近 3 条上下文） |
| AC7 最大模型调用熔断 | 故意给 Agent 一个无法回答的问题 → Agent 最多调用 20 次模型 → Trace 中 AGENT_CALL_FAILED 阶段出现，错误消息为「超过最大模型调用次数」 |
| AC8 sections-only 输出 | LLM 返回 → 检查 ChatAssistantResult 无 response 字段 → deriveResponse() 从 sections 派生纯文本 |
| AC9 SSE 流式 | 打开浏览器 DevTools → 看 SSE 事件序列 → meta 先到 → N 次 chunk → complete |
| AC10 Trace 完整性 | 每次对话生成 traceId → 调 `GET /api/ai/trace/{traceId}` → 看到完整阶段日志（含 MODEL_CALL_* + TOOL_CALL_*） |
| AC11 Trace 真实 token | ModelCallTraceAspect 记录 promptTokens/completionTokens/totalTokens → 与 AgentObservability 统计一致 |
| AC12 并发稳定性 | 10 个用户同时对话 → 全部正常返回 → 无 RejectedExecutionException |

---

## 九、测试方式

### 9.1 单元测试

| 模块 | 覆盖重点 |
|------|----------|
| OutputFormatFallbackService | 四层兜底解析分别覆盖（BeanConverter / bracket balance / lenient / 重建） |
| ContextCompressionService | Smart/Aggressive 压缩逻辑 + keepRecentMessages=3 + 规则提取回退 |
| ChatSessionManager | getOrCreateSession + touch + Caffeine TTL |
| InputSanitizer | 超长输入拒绝 + Prompt 注入检测 |
| AgentCallManager | 超时 + 重试 + 指数退避 |
| Token 估算 | 中文 × 1.5 + 英文 × 0.25 估算正确性 |
| 工具权限 | 非成员调用 Tool → SpaceAuthorizationService 拦截 |

### 9.2 集成测试

| 场景 | 覆盖重点 |
|------|----------|
| 完整对话链路 | 输入 → Agent → Tools → 输出 → SSE |
| 完整 Trace 链路 | ReAct 循环 → 每个阶段记录 → Trace 可查询 |
| 上下文压缩链路 | 多轮对话 → 触发压缩 → 压缩后继续 |
| 重试链路 | mock Tool 抛异常 → 观察指数退避重试 |
| Skill 按需加载链路 | loadSkill + loadSkillDocument |
| Output 兜底链路 | mock LLM 返回 malformed JSON → 四层兜底生效 |

### 9.3 手动测试 Checklist

- [ ] 简单查询：「当前空间有哪些测试场景」
- [ ] 故障排查：「帮我分析任务 XXX 为什么失败了」
- [ ] Trace 查询：「查询 traceId: xxx」
- [ ] 上下文压缩：连续对话 30+ 轮
- [ ] 清理会话：点对话框右上角清除 → 重新对话 → 无历史
- [ ] Trace 时间线：打开任务详情 → 点 AI 助手生成 traceId → 跳转 Trace 详情页 → 看到完整时间线
- [ ] 并发：10 个浏览器标签页同时对话

---

## 十、已知限制与后续迭代

| 当前限制 | 后续规划 |
|----------|----------|
| 单一 LLM（deepseek-chat） | 多 LLM 可选（平台支持切换 / 对比） |
| Skills 热插拔需重启 | 运行时动态加载（Spring DevTools 或显式 reload） |
| 无外部 Jaeger 集成 | 接入 OpenTelemetry SDK 导出到 Jaeger / Zipkin |
| 无向量数据库 RAG | 新增 Chroma / pgvector，对 Skills 做向量检索 + rerank |
| Trace 存 Redis | 后续迁移到 ClickHouse / Elasticsearch 做长周期分析 |
| Agent 工具固定 7 个 | 支持 Tool 注册中心，按 promptKey 绑定不同工具集 |
| 无 Agent 调用限流 | 增加每分钟请求配额（类似大模型评测的三层预算墙） |
| Prompt 写死 classpath:AGENT.md | **v1.4 已修复** → Prompt 托管中心 API 拉取 + Caffeine 缓存 |
