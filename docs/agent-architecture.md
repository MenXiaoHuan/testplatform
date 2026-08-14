# AI Agent 技术架构详解

本文档详细阐述 Playwright Test Platform 的 AI Agent 技术实现，包括架构设计、Agent Loop、上下文管理、工具调用、输出兜底和工程约束。

---

## 1. 架构总览

### 1.1 技术栈

| 组件 | 实现 | 版本 |
|---|---|---|
| Agent 框架 | Spring AI Alibaba Agent | 1.1.2.2 |
| Agent 类型 | ReactAgent (ReAct 模式) | - |
| LLM | deepseek-chat | - |
| 模型调用限制 | ModelCallLimitHook (max 20 次) | - |
| 技能加载 | SkillIndexLoader 索引 + loadSkill/loadSkillDocument 按需加载 | - |
| 模型调用追踪 | ModelCallTraceAspect 拦截 ChatModel.call() 记录真实 token 用量 | - |
| 工具调用追踪 | ToolTraceAspect 拦截 @Tool 方法记录工具名/入参/结果 | - |
| Trace 上下文 | AgentTraceContext (ThreadLocal) | - |
| 上下文摘要 | ContextCompressionService (LLM 摘要优先 + 规则回退) | - |
| 会话存储 | Caffeine 本地缓存 | 30min TTL, max 10K |
| Trace 存储 | Redis (List + ZSet) | 90 天 TTL |
| 调度事件 | schedule_event 表 (CRON/AGENT/MANUAL) | V8 迁移 |
| 流式响应 | SseEmitter (chunk 使用 MediaType.TEXT_PLAIN) | SSE 协议 |

### 1.2 架构分层图

```
┌─────────────────────────────────────────────────────────────┐
│                       前端 (Vue 3)                           │
│  AiAssistantDialog.vue · ai.ts store · SSE EventSource       │
│  AgentTraceDetailView.vue (固定侧边栏) · protectTablesAndEscape │
└──────────────────────────┬──────────────────────────────────┘
                           │ SSE /api/ai/chat/stream
┌──────────────────────────▼──────────────────────────────────┐
│                    AgentController                           │
│  同步 chat() · 流式 chatStream() · 会话管理 · traceId 返回    │
│  SSE: meta{sections} → chunk(TEXT_PLAIN) → complete          │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                     AgentService                             │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ InputSanitizer 输入清洗 + Prompt 注入检测            │    │
│  │ ChatSessionManager 会话获取/创建                     │    │
│  │ ContextCompressionService 上下文压缩                │    │
│  │ Token Budget Check 硬 token 上限检查                 │    │
│  │ AgentCallManager 超时 + 重试                        │    │
│  │ ReactAgent ReAct 循环调用                           │    │
│  │ OutputFormatFallbackService 四层输出解析 (sections) │    │
│  │ AgentTraceLogService 全链路 trace 记录              │    │
│  │ ScheduleEventService 写入/完成 AGENT 调度事件      │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  AOP Aspects (独立拦截层):                                    │
│  ├── ModelCallTraceAspect  拦截 ChatModel.call()           │
│  │   → MODEL_CALL_STARTING / COMPLETED / FAILED             │
│  │   → 记录真实 token 用量 (prompt/completion/total)       │
│  ├── ToolTraceAspect       拦截 @Tool 方法                  │
│  │   → TOOL_CALL_STARTING / COMPLETED / FAILED             │
│  │   → 记录工具名、入参、结果、耗时                          │
│  └── AgentTraceContext     ThreadLocal traceId 持有器       │
└──────────────────────────┬──────────────────────────────────┘
                           │ .call(prompt)
┌──────────────────────────▼──────────────────────────────────┐
│                    ReactAgent (ReAct)                         │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ SystemPromptHook 注入系统提示词 (AGENT.md + 技能索引) │    │
│  │ ModelCallLimitHook 限制最多 20 次模型调用           │    │
│  │                                                     │    │
│  │  ReAct Loop:                                        │    │
│  │   Think → Call Tool → Observe → Think → ... → Answer │    │
│  │   (按需) loadSkill → loadSkillDocument 加载技能正文  │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  Tools:                                                      │
│  ├── TaskTool          任务查询/分析                        │
│  ├── SceneTool         场景查询                              │
│  ├── RepositoryTool    仓库查询                             │
│  ├── LogPreprocessingTool 日志预处理分析                    │
│  ├── TraceQueryTool    traceId 链路查询                     │
│  ├── LoadSkillContentTool  按需加载技能 SKILL.md 正文       │
│  └── LoadSkillDocumentTool 按需加载技能子文档              │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                    deepseek-chat (LLM)                       │
│  推理 · 工具调用决策 · 结构化 sections 输出                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Agent Loop (ReAct 循环)

### 2.1 ReAct 模式

Agent 采用 **ReAct (Reasoning + Acting)** 模式工作，每一轮循环包含 4 个阶段：

```
┌─────────────────────────────────────────────────────────────┐
│                    ReAct 循环（最多 20 轮）                   │
│                                                              │
│  1. Think: LLM 分析当前上下文，决定下一步行动                 │
│     └─ 输出: 调用哪个工具 / 直接回答                         │
│                                                              │
│  2. Act:   执行工具调用                                      │
│     └─ TaskTool.getTask(taskId=123)                         │
│                                                              │
│  3. Observe: 获取工具返回结果                                │
│     └─ 任务 FAILED, 状态: RUNNING→FAILED                    │
│                                                              │
│  4. Think: LLM 根据新结果重新决策                            │
│     └─ 决定再调一个工具 / 生成最终回答                       │
│                                                              │
│  └── 重复直到 LLM 输出最终回答（sections 非空）或达到 20 轮上限 ──┘
```

### 2.2 循环约束

| 约束 | 值 | 实现 |
|---|---|---|
| 最大模型调用次数 | 20 | `ModelCallLimitHook.runLimit(20)` |
| 单次调用超时 | 60 秒 | `AgentCallManager.timeoutSeconds` |
| 重试次数 | 2 次 | `AgentCallManager.maxRetries` |
| 重试策略 | 指数退避 | 1s → 2s → 4s |
| 线程池 | 4-8 核心线程 | `AgentCallManager` 内置线程池 |

### 2.3 Hooks 机制

Agent 通过 2 个 Hook 注入增强能力：

| Hook | 作用 | 说明 |
|---|---|---|
| `SystemPromptHook` | 注入系统提示词 | 从 `AGENT.md` 加载基础提示词，并由 `SkillIndexLoader` 在末尾追加技能索引（name + description），定义 Agent 行为规则、输出格式、安全约束 |
| `ModelCallLimitHook` | 限制模型调用次数 | 防止无限循环消耗 token |

> Skills 不再通过 Hook 预加载全文。`SkillIndexLoader` 在启动时扫描 `skills/*/SKILL.md` 的 frontmatter 生成索引，只把索引追加到系统提示词；正文由 LLM 按需调用 `loadSkill` / `loadSkillDocument` 工具加载（见 §4.2）。

### 2.4 完整调用时序

```
用户: "帮我分析任务 123 为什么失败了"
  │
  ├─ Think#1: 需要先获取任务详情
  │   └─ Call: TaskTool.getTask(taskId=123, spaceId=7)
  │       └─ ToolTraceAspect → TOOL_CALL_STARTING / COMPLETED (记录入参、结果、耗时)
  │
  ├─ Observe#1: 返回任务详情 (FAILED, 超时)
  │
  ├─ Think#2: 任务超时，需要看日志
  │   └─ Call: LogPreprocessingTool.analyzeLogs(taskId=123, spaceId=7)
  │       └─ ToolTraceAspect → TOOL_CALL_STARTING / COMPLETED
  │
  ├─ Observe#2: 发现 "TimeoutError: 页面加载超时"
  │
  ├─ Think#3: 需要查看更多 trace 细节
  │   └─ Call: TraceQueryTool.queryTrace(traceId="xxx")
  │       └─ ToolTraceAspect → TOOL_CALL_STARTING / COMPLETED
  │
  ├─ Observe#3: 返回完整 Agent 调用链路
  │
  └─ Think#4: 综合所有信息生成最终回答
      └─ 输出 sections 数组 (heading/paragraph/list/code/quote/table)
         "任务 123 失败原因是页面加载超时..."
         └─ ModelCallTraceAspect → MODEL_CALL_COMPLETED (记录真实 token 用量)

注：每一次 LLM 调用都会触发 ModelCallTraceAspect 拦截，
    记录 promptTokens / completionTokens / totalTokens / durationMs
```

---

## 3. 上下文管理

### 3.1 上下文管理架构图

```
┌──────────────────────────────────────────────────────────────┐
│                    上下文管理流程                              │
│                                                              │
│  新消息进入                                                  │
│       │                                                      │
│       ▼                                                      │
│  ChatSessionManager.getOrCreateSession(sessionId)            │
│       │                                                      │
│       ├── 命中缓存 → touch() 更新 lastAccessedAt             │
│       └── 未命中 → 创建新会话 (空 messages, systemPrompt=null) │
│       │                                                      │
│       ▼                                                      │
│  ChatSession (record)                                        │
│    ├── sessionId                                             │
│    ├── messages: List<ChatMessage>     ← 对话历史            │
│    ├── systemPrompt: String            ← 系统提示词          │
│    ├── estimatedTokens: int            ← 当前 token 估算     │
│    └── createdAt / lastAccessedAt      ← 时间戳              │
│       │                                                      │
│       ▼                                                      │
│  ContextCompressionService.compressIfNeeded(session)         │
│       │                                                      │
│       ├── 检查 token 是否 > 80% × maxTokens (6400)          │
│       ├── 检查 messageCount 是否 > maxMessages (50)          │
│       │                                                      │
│       ├── 未触发 → 原样返回                                  │
│       │                                                      │
│       └── 触发压缩 →                                         │
│           ├── token > maxTokens (8000)                       │
│           │   └── applyAggressiveCompression()              │
│           │       历史消息 → 精简摘要                        │
│           │       只保留最近 3 条 user/assistant 消息        │
│           │                                                  │
│           └── token > 80% × maxTokens                        │
│               └── applySmartCompression()                   │
│                   优先 LLM 摘要 (30s 超时, use-llm-summary)  │
│                   失败/未启用 → 规则提取结构化轮次摘要         │
│                   保留最近 3 条原始消息                       │
│                                                              │
│       ▼                                                      │
│  Token Budget Check (硬上限)                                 │
│    ├── promptTokens <= maxTokens → 放行                     │
│    └── promptTokens > maxTokens →                            │
│        ├── 再次压缩 + truncateLongMessages                   │
│        └── 仍超限 → 返回错误 "上下文过长，无法处理"           │
│                                                              │
│       ▼                                                      │
│  AgentService.buildPrompt(session, request)                  │
│    ├── System: systemPrompt                                 │
│    ├── 上下文信息 (spaceId, taskId 等)                      │
│    ├── 历史对话摘要 (结构化)                                 │
│    ├── 最近对话 (最近 3 条原始消息)                          │
│    └── 组装成完整 prompt 传给 ReactAgent                    │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 Token 估算

Token 估算采用字符数 × 系数的方式，在 `ChatSession.estimateTextTokens()` 中实现：

```
tokenEstimate = max(1, 中文字符数 × 1.5 + 其他字符数 × 0.25)
```

| 字符类型 | 系数 | 说明 |
|---|---|---|
| 中文 (U+4E00 ~ U+9FFF) | 1.5 | 中文 token 密度较高 |
| 英文/标点/其他 | 0.25 | 英文单词通常 4 字符 ≈ 1 token |

**注意**：此估算值用于内部压缩决策，与 LLM 实际计费的 token 数可能有偏差。

### 3.3 结构化摘要生成

`ContextCompressionService` 注入 `ChatModel`，Smart 压缩优先用 LLM 生成摘要，失败/超时/未启用时回退到规则提取；Aggressive 压缩仍使用规则提取。

#### 3.3.1 LLM 摘要（Smart 压缩首选）

当 `platform.ai.context.use-llm-summary=true`（默认）且触发 Smart 压缩时，调用 LLM 对历史消息做结构化总结：

- 输入：把待压缩消息渲染为 `用户/助手/[工具名]` 文本（单条 user/assistant 截断 800 字符，tool 截断 400 字符）
- System 指令：中文输出、不超过 600 字、按轮次列出关键信息（用户问题/工具名与参数/助手结论）、保留所有 taskId/sceneId/spaceId/错误类型、保留故障根因结论
- 超时：30 秒（`CompletableFuture.get` + 超时取消）
- 失败回退：超时或异常时降级为规则提取 `generateStructuredSummary`

```
[历史对话摘要·LLM]
共5轮对话;
轮次1: 用户: 为什么测试失败了？; 工具: TaskTool(taskId=88); 结论: 任务失败于执行阶段
轮次2: ...
```

#### 3.3.2 规则提取摘要（LLM 回退 / Aggressive 压缩）

LLM 不可用时回退到规则提取。压缩前消息按轮次（Turn）分组（`groupIntoTurns`）：

```
消息列表: [user1, tool1, tool2, assistant1, user2, tool3, assistant2]
                         ↓ groupIntoTurns
轮次: [Turn(user1, [tool1, tool2], assistant1), Turn(user2, [tool3], assistant2)]
```

每个 `Turn` 包含：
- `userText`: 用户原始消息
- `toolCalls`: 工具调用列表（toolName + params + result）
- `assistantText`: 助手回复摘要

规则提取 Smart 摘要格式：

```
[历史对话摘要]
共5轮对话;
轮次1: 用户: 为什么测试失败了？
  工具调用: getTask(taskId=123, spaceId=7), analyzeLogs(taskId=123)
  工具结果: 任务FAILED, 发现NullPointerException at UserService:45
  助手结论: 任务因空指针异常在 UserService 第45行失败
轮次2: 用户: 能看看日志吗？
  ...
```

#### 3.3.3 Aggressive 压缩摘要格式

当触发 Aggressive 压缩时（token 超 100% maxTokens），使用规则提取的精简摘要：

```
[历史对话摘要·精简]
对话概要: 共5轮, 7次工具调用;
最近3轮:
- 用户: 为什么测试失败了？ → 调用: getTask, analyzeLogs → 任务因空指针异常失败
- 用户: 能看看日志吗？ → 调用: getTask → 已获取日志
- 用户: 帮我修复 → ...
```

#### 3.3.4 压缩后滑动窗口

压缩后，只保留最近 3 条原始消息（user / assistant），历史消息被摘要替代：

```
压缩前: [msg1, msg2, msg3, msg4, msg5, msg6, msg7, msg8, msg9, msg10]
                                        ↑ keepFrom = 10 - 3 = 7
压缩后: [summary, msg8, msg9, msg10]
         ↑ 摘要    ↑── 最近 3 条原始消息 ──↑
```

### 3.4 Token 预算检查与熔断

```
┌─────────────────────────────────────────────────────────────┐
│                  Token Budget Check 流程                     │
│                                                              │
│  1. 计算 promptTokens = estimateTotalTokens                 │
│     (messages + systemPrompt)                               │
│                                                              │
│  2. 记录 PROMPT_TOKEN_BUDGET trace:                         │
│     {promptTokens, maxAllowedTokens, headroomTokens, usage%} │
│                                                              │
│  3. if promptTokens > maxTokens (8000):                     │
│     a. 调用 compressIfNeeded() → 强制 Aggressive 压缩        │
│     b. 调用 truncateLongMessages() → 截断超长消息            │
│     c. 再次计算 promptTokens                                │
│     d. if 仍超限:                                            │
│         → 返回错误 "上下文过长，无法在 token 限制内处理"      │
│         → sessionId 标记为 contextCompressed=true            │
│                                                              │
│  4. if promptTokens <= maxTokens:                           │
│     → 正常传给 Agent                                        │
└─────────────────────────────────────────────────────────────┘
```

### 3.5 会话生命周期

```
创建 → 使用 → 过期
  │       │        │
  │       │        └─ 30min 无访问 → Caffeine 自动驱逐
  │       │           removalListener 记录日志
  │       │
  │       └─ touch() 更新 lastAccessedAt, 重置 TTL
  │          appendMessage() 追加消息，重算 estimatedTokens
  │          updateMessages() 压缩后更新消息列表
  │
  └─ createSession(sessionId, systemPrompt)
     getOrCreateSession(sessionId)
        ├── sessionId 为空 → 自动生成 UUID
        └── sessionId 不为空 → 查缓存，未命中则创建
```

### 3.6 上下文爆炸防护汇总

| 层级 | 机制 | 阈值 | 说明 |
|---|---|---|---|
| L1 | Token 估算 | - | 中文字×1.5 + 英文×0.25，统一公式 |
| L2 | 消息数量限制 | 50 条 | 超过触发压缩 |
| L3 | Smart 压缩 | 80% maxTokens | LLM 摘要优先（失败回退规则提取） + 保留 3 条最近消息 |
| L4 | Aggressive 压缩 | 100% maxTokens | 精简摘要 + 只保留最近 3 条 user/assistant |
| L5 | 长消息截断 | 4000 字符/条 | 超过自动截断并标注 |
| L6 | 硬截断兜底 | 80% maxTokens | 从最新消息往前保留，达到预算即停 |
| L7 | 完全熔断 | 仍超限 | 返回错误，不发送超长上下文给 LLM |

---

## 4. 工具调用

### 4.1 工具注册

工具通过 `ReactAgentConfig` 注册到 Agent：

```java
String basePrompt = systemPromptConfig.loadSystemPrompt(resourceLoader, systemPromptPath);
// 在系统提示词末尾追加 skill 索引（仅 name+description，不包含正文）
String systemPrompt = basePrompt + skillIndexLoader.getIndexText();

ReactAgent.builder()
    .name("intelligent-assistant")
    .model(model)
    .methodTools(
        repositoryTool,        // 仓库查询
        sceneTool,             // 场景查询
        taskTool,              // 任务查询/分析
        logPreprocessingTool,  // 日志预处理分析
        traceQueryTool,        // traceId 链路查询
        loadSkillContentTool,  // 按需加载技能 SKILL.md 正文
        loadSkillDocumentTool  // 按需加载技能子文档
    )
    .outputType(ChatAssistantResult.class)
    .hooks(SystemPromptHook.builder().systemText(systemPrompt).build())
    .hooks(ModelCallLimitHook.builder().runLimit(20).build())
    .build();
```

### 4.2 工具列表

| 工具 | 方法 | 功能 | 输入 | 输出 |
|---|---|---|---|---|
| **TaskTool** | `getTask(taskId, spaceId)` | 查询任务详情（状态、结果、用例统计、阶段日志预览） | taskId, spaceId | 任务状态、结果、用例统计、阶段日志摘要 |
| | `listTasks(sceneId?, spaceId)` | 列出空间下的任务 | sceneId?, spaceId | 任务列表（状态、耗时、通过/失败数） |
| **SceneTool** | `getSceneDetail(sceneId, spaceId)` | 查询场景详情（浏览器、分支、选择器、环境变量） | sceneId, spaceId | 场景完整配置 |
| | `listScenes(repoId?, spaceId)` | 列出空间下的测试场景 | repoId?, spaceId | 场景列表（状态、调度开关） |
| **RepositoryTool** | `getRepository(repositoryId, spaceId)` | 查询仓库配置（Git 地址、默认分支、安装/执行命令） | repositoryId, spaceId | 仓库完整配置 |
| | `searchRepository(keyword, spaceId)` | 按名称关键词搜索仓库 | keyword, spaceId | 匹配的仓库列表 |
| **LogPreprocessingTool** | `analyzeLogs(taskId, spaceId)` | 分析任务日志，提取错误摘要和失败用例 | taskId, spaceId | 各阶段错误摘要、失败用例列表 |
| **TraceQueryTool** | `queryTrace(traceId)` | 查询某条 Agent 调用的完整链路 | traceId | 所有阶段日志条目 + 链路摘要 |
| | `listRecentTraces(limit?)` | 列出最近的 Agent 调用记录 | limit? (默认 10, 最大 50) | traceId 摘要列表（日志条数、最后更新时间） |
| | `getTraceStats()` | Trace 存储统计 | 无 | trace 总数、保留策略 |
| **LoadSkillContentTool** | `loadSkill(name)` | 按需加载技能 `SKILL.md` 正文 | skill name | 技能正文 + 子文档清单 |
| **LoadSkillDocumentTool** | `loadSkillDocument(skillName, docName)` | 按需加载技能子文档 | skillName, docName | 子文档正文 |

### 4.3 Skills 按需加载

为避免把所有技能正文塞入上下文浪费 token，Skills 采用**索引固定 + 正文按需加载**的两段式设计：

```
启动阶段 (SkillIndexLoader.load @PostConstruct)
  ├── 扫描 classpath:skills/*/SKILL.md
  ├── 解析 YAML frontmatter (name + description)
  ├── 列出每个 skill 目录下的子文档清单
  └── 拼接成「可用技能清单」追加到系统提示词末尾
        ↓
运行阶段 (LLM 决策)
  ├── LLM 根据用户问题判断是否需要某技能
  ├── 调用 loadSkill(name)  → 读取 SKILL.md 正文（剥离 frontmatter）
  ├── SKILL.md 正文给出决策树/路由，指引加载哪个子文档
  └── 调用 loadSkillDocument(skillName, docName) → 读取子文档详细 SOP
```

可用技能：

| 技能 | 子文档 |
|---|---|
| `error-analysis` | playwright-error、scheduler-error、docker-error、cache-db-error、llm-agent-error |
| `business-knowledge` | tech-architecture、data-model、business-functions、llm-agent |

新增技能只需在 `resources/skills/<name>/` 下放入带 frontmatter 的 `SKILL.md`，重启后端即自动加入索引。

### 4.4 工具调用流程

```
LLM 输出工具调用指令
    │
    ▼
Spring AI 框架解析函数调用
    │
    ▼
根据 methodTools 列表匹配工具
    │
    ▼
反射调用对应 Java 方法
    │
    ▼
工具执行业务逻辑（查数据库/缓存）
    │
    ▼
返回工具结果给 LLM
    │
    ▼
LLM 基于新结果继续推理或生成最终回答
```

### 4.5 工具调用异常处理

`ToolErrorFallback` 分析工具使用情况，检测异常模式：
- Agent 在没有足够信息时反复调用同一工具
- 工具调用返回空结果后 Agent 仍继续尝试
- 检测到异常时在 trace 日志中记录 `ToolCallAnalysis`，提供改进建议

---

## 5. 输出工程兜底

### 5.1 四层输出解析

Agent 使用 sections-only 结构化输出（`ChatAssistantResult.sections`），不再输出纯文本 response。但 LLM 输出可能不稳定，采用 4 层解析策略逐级兜底：

```
┌─────────────────────────────────────────────────────────────┐
│                  OutputFormatFallbackService                 │
│                                                              │
│  LLM 原始输出                                                │
│       │                                                      │
│       ▼                                                      │
│  第一层: BeanOutputConverter                                 │
│    Spring AI 内置转换器，直接映射到 ChatAssistantResult       │
│    ├── 成功 → 返回 (strategy: "bean_converter")              │
│    │          自动从 JSON 中提取 sections 数组                │
│    └── 失败 → 继续                                          │
│       │                                                      │
│       ▼                                                      │
│  第二层: JSON 提取                                           │
│    用正则提取 ```json``` 块或 { } 包裹的 JSON                 │
│    ├── 成功 → 解析 JSON 构造 ChatAssistantResult             │
│    │         重点解析 sections 数组（含 type/level/text/etc）│
│    │         (strategy: "json_extraction")                  │
│    └── 失败 → 继续                                          │
│       │                                                      │
│       ▼                                                      │
│  第三层: sections 重建                                        │
│    当 JSON 中 sections 缺失或不完整时                         │
│    尝试从原始文本中识别 heading/list/code 等模式              │
│    ├── 成功 → 构造 ChatAssistantResult (strategy: "sections_rebuild") │
│    └── 失败 → 继续                                          │
│       │                                                      │
│       ▼                                                      │
│  第四层: 纯文本兜底                                          │
│    直接把原始文本包装成 paragraph 类型的 sections              │
│    └── (strategy: "fallback")                               │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 ChatAssistantResult 结构（sections-only）

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatAssistantResult(
    String traceId,                // 追踪 ID（自动生成 UUID）
    List<String> usedTools,       // 使用过的工具名列表
    String confidence,             // 置信度 (HIGH/MEDIUM/LOW)
    String responseType,          // 响应类型 (ANALYSIS/QA/TRACE/...)
    FaultDetail faultDetail,      // 故障详情 (可选)
    List<ContentBlock> sections   // 结构化内容块（六种类型）
) {
    /** 从 sections 派生纯文本，用于历史存储和 SSE streaming chunk */
    public String deriveResponse() {
        return OutputFormatFallbackService.deriveTextFromSections(sections);
    }

    public record FaultDetail(
        String fault_type,         // 故障类型
        String root_cause,         // 根因
        String immediate_solution, // 即时解决方案
        String long_term_optimize, // 长期优化建议
        String test_risk,          // 测试风险
        String reproduce_steps     // 复现步骤
    )
}
```

**设计决策**：
- 移除 `response` 字段，LLM 只输出 `sections` 结构化数组，避免 response/sections 双写不一致
- 节省约 40-50% token（不需要同时写 markdown 文本和结构化 JSON）
- 后端统一通过 `deriveTextFromSections(sections)` 派生纯文本，用于：
  - SSE streaming chunk 逐字符发送（打字机效果）
  - ChatSession 历史消息存储
  - Trace 日志记录

### 5.3 ContentBlock 六种类型

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContentBlock(
    String type,          // heading / paragraph / list / code / quote / table
    Integer level,        // heading 专用: 1/2/3
    String text,          // heading / paragraph / quote 专用
    List<String> items,   // list 专用
    Boolean ordered,      // list 专用: true=有序, false=无序
    String language,      // code 专用: 语言
    String code,          // code 专用: 代码内容
    List<String> headers, // table 专用: 表头
    List<List<String>> rows // table 专用: 二维数据
)
```

`ContentBlock` 提供静态工厂方法：`heading()`、`paragraph()`、`list()`、`code()`、`quote()`、`table()`。

### 5.4 预处理

解析前先做文本预处理：
- 去除 markdown 代码块标记
- 清理多余的 ```json / ``` 标记
- 确保文本是干净的 JSON 或纯文本

---

## 6. 工程约束与安全

### 6.1 输入安全

| 机制 | 实现 | 说明 |
|---|---|---|
| 输入长度限制 | 10000 字符 | 超长拒绝并提示 |
| Prompt 注入检测 | `InputSanitizer` | 检测是否尝试绕过系统约束 |
| 特殊字符清洗 | `InputSanitizer.sanitize()` | 清洗危险输入 |

### 6.2 调用可靠性

| 机制 | 参数 | 说明 |
|---|---|---|
| 超时控制 | 60 秒 | `CompletableFuture.get(timeout)` |
| 重试 | 2 次 | 指数退避：1s → 2s → 4s |
| 调用线程池 | 4-8 核心线程 | 独立线程池，不占用 Web 请求线程 |
| 熔断后降级 | 返回友好错误消息 | "智能助手暂时不可用，请稍后重试" |

### 6.3 输出安全

| 机制 | 说明 |
|---|---|
| SSE 流式传输 | chunk 使用 `MediaType.TEXT_PLAIN` 避免被 JSON 序列化；meta 事件携带 sections 数组 |
| Markdown 渲染 | 前端用 `marked` + DOMPurify 安全渲染；代码块用 highlight.js 高亮 |
| 表格符号保护 | `protectTablesAndEscape()` 保护代码块/内联代码，识别真实表格分隔行（`|---|---|`），转义普通文本中的 `\|` 防 marked 误判为 GFM 表格 |
| 前端渲染三路分支 | streaming 打字中 → marked + protectTablesAndEscape + DOMPurify；完成+有 sections → Vue v-for 按 type 结构化渲染；完成+无 sections → 降级到 marked |
| 结构化 sections 渲染 | Vue 组件按 ContentBlock.type 直接渲染（h1/h2/h3、p、ul/ol、pre.hljs、blockquote、table），无解析歧义 |
| 纯文本兜底 | 四层解析全部失败时用原始文本包装成 paragraph 类型的 sections |

### 6.4 可观测性

#### 6.4.1 Trace 日志

每个对话生成唯一 `traceId` (UUID)，全链路各阶段记录结构化日志到 Redis：

| 阶段 (Stage) | 含义 | 关键元数据 |
|---|---|---|
| `REQUEST_RECEIVED` | 接收到对话请求 | sessionId, spaceId, messageLength |
| `SESSION_READY` | 会话准备完成 | messageCount, estimatedTokens |
| `CONTEXT_COMPRESSED` | 上下文压缩完成 | originalTokens, compressedTokens, messageCount |
| `CONTEXT_READY` | 上下文就绪（未压缩） | estimatedTokens, messageCount |
| `SYSTEM_PROMPT_LOADED` | 系统提示词加载完成 | systemPromptTokens, totalEstimatedTokens |
| `PROMPT_TOKEN_BUDGET` | Token 预算检查 | promptTokens, maxAllowedTokens, usage% |
| `AGENT_CALL_STARTING` | Agent 调用开始 | model, maxRetries, timeoutSeconds |
| **`MODEL_CALL_STARTING`** | AOP 拦截：LLM 模型调用开始 | modelClassName, messageCount, messageRoles, estimatedInputTokens |
| **`MODEL_CALL_COMPLETED`** | AOP 拦截：LLM 模型调用完成 | durationMs, promptTokens, completionTokens, totalTokens, tokenDiffVsEstimate, generationFull |
| **`MODEL_CALL_FAILED`** | AOP 拦截：LLM 模型调用失败 | errorType, errorMessage, durationMs |
| **`TOOL_CALL_STARTING`** | AOP 拦截：工具调用开始 | toolName, args (完整), targetClass |
| **`TOOL_CALL_COMPLETED`** | AOP 拦截：工具调用完成 | toolName, durationMs, result (完整), resultLength |
| **`TOOL_CALL_FAILED`** | AOP 拦截：工具调用失败 | toolName, errorType, errorMessage, args |
| `AGENT_CALL_SUCCESS` | Agent 调用成功 | sectionCount, responseLength, callDurationMs |
| `AGENT_CALL_FAILED` | Agent 调用失败 | errorMessage |
| `OUTPUT_PARSED` | 输出解析完成 | parsingStrategy, responseType, usedTools |
| `REQUEST_COMPLETED` | 请求完成 | processingTime, compressed |
| `SANITIZATION_FAILED` | 输入清洗失败 | rejectionReason |

> **MODEL_CALL_* 和 TOOL_CALL_*** 阶段由 AOP 切面（`ModelCallTraceAspect`、`ToolTraceAspect`）独立拦截记录，每个 ReAct 循环中的每次 LLM 调用和工具调用都会产生这些阶段，完整还原 Agent 的思考-行动链路。
>
> 所有 metadata 值保留完整内容（不再区分 Preview/Full），仅当单值超过 `MAX_METADATA_VALUE_LENGTH`（200000 字符）时截断；List 类型超过 200 条时截断并标注总数。token 用量来自 LLM API 真实返回的 usage 字段（非启发式估算），可用于成本统计和 tokenDiffVsEstimate 对比。

#### 6.4.2 Trace 查询

Trace 提供两种查询途径：

**1. Agent 工具查询**（对话内）：

```
用户: "查询 traceId: abc-123-def"
Agent: 调用 TraceQueryTool.queryTrace("abc-123-def")
       → 返回该 traceId 的完整链路日志
       → 按时间顺序展示每个阶段的时间、耗时、元数据
```

**2. HTTP 接口查询**（前端时间线页 / 调度事件跳转）：

| 接口 | 说明 |
|---|---|
| `GET /api/ai/trace?limit=20` | 返回最近 trace 摘要列表（`TraceSummary`: traceId, entryCount, lastUpdatedAt），按时间倒序，limit 上限 100 |
| `GET /api/ai/trace/{traceId}` | 返回某条 trace 的完整时间线（`List<TraceLogEntry>`），按时间升序，直接读 Redis List |

前端调度事件列表中 Agent 调度事件可通过 `traceId` 跳转到 `AgentTraceDetailView` 时间线页，调用 `GET /api/ai/trace/{traceId}` 渲染完整链路。

#### 6.4.3 Redis 存储

| Redis Key | 类型 | TTL | 用途 |
|---|---|---|---|
| `agent:trace:{traceId}` | List\<JSON\> | 90 天 | 存储单个 trace 的全链路日志条目 |
| `agent:trace:index` | ZSet | 90 天 | 按时间排序的 traceId 索引 |

每个 trace 日志条目结构（`TraceLogEntry` record）：
```json
{
  "id": "entry-uuid",
  "traceId": "trace-uuid",
  "timestamp": "2026-08-13T13:00:00Z",
  "level": "INFO",
  "stage": "PROMPT_TOKEN_BUDGET",
  "message": "Token budget check before Agent call",
  "metadata": {
    "promptTokens": 5230,
    "maxAllowedTokens": 8000,
    "headroomTokens": 2770,
    "budgetUsagePercent": 65
  }
}
```

> 字段说明：metadata 中所有字符串值统一保留**完整内容**（不再区分 Preview/Full），仅当单值超过 `MAX_METADATA_VALUE_LENGTH`（200000 字符）时截断；List 类型超过 200 条时截断并标注总数。

### 6.5 性能参数

| 参数 | 默认值 | 配置项 |
|---|---|---|
| maxTokens | 8000 | `platform.ai.context.max-tokens` |
| maxMessages | 50 | `platform.ai.context.max-messages` |
| compressionThreshold | 0.8 | `platform.ai.context.compression-threshold` |
| keepRecentMessages | 3 | `platform.ai.context.keep-recent-messages` |
| maxMessageContentLength | 4000 | `platform.ai.context.max-message-content-length` |
| useLlmSummary | true | `platform.ai.context.use-llm-summary` |
| llmSummaryTimeoutSeconds | 30 | `ContextCompressionService.LLM_SUMMARY_TIMEOUT_SECONDS` |
| callTimeoutSeconds | 60 | `platform.ai.call.timeout-seconds` |
| callMaxRetries | 2 | `platform.ai.call.max-retries` |
| maxModelCalls | 20 | `ModelCallLimitHook.runLimit()` |
| sessionTtlMinutes | 30 | Caffeine `expireAfterWrite` |
| maxSessions | 10000 | Caffeine `maximumSize` |
| traceTtlDays | 90 | Redis TTL |

### 6.6 Agent 调度事件

每次 Agent 对话（同步/流式）都会在 `schedule_event` 表写入一条 `schedule_type=AGENT` 的调度事件，便于在调度事件模块统一观测 Agent 运行情况。

```
AgentService.chat / chatStream 入口
  ├── scheduleEventService.createAgentEvent(spaceId, traceId, sessionId, userMessage)
  │     → status=RUNNING, schedule_type=AGENT, 记录 traceId/sessionId/userMessage
  ├── ... Agent 执行 ...
  └── finally:
        scheduleEventService.completeAgentEvent(eventId, success, errorMessage)
          → success=true  → status=COMPLETED
          → success=false → status=FAILED + errorMessage
```

`schedule_event` 表新增字段（V8 迁移）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `schedule_type` | varchar(32) | 调度类型：`CRON`（定时）/ `AGENT`（Agent 对话）/ `MANUAL`（手动） |
| `trace_id` | varchar(64) | Agent 对话的 traceId，可反查完整时间线 |
| `session_id` | varchar(64) | Agent 会话 ID |
| `user_message` | varchar(1024) | 触发该次 Agent 对话的用户消息 |

前端 `EventListView`（调度事件列表页）默认展示 Agent 调度事件（`scheduleType=AGENT`），支持通过 `traceId` 精确筛选和 `sceneName` 场景名称筛选，同时可切换到定时调度（CRON）或手动调度（MANUAL）查看。Agent 调度事件可通过 `traceId` 跳转到 `AgentTraceDetailView` 时间线页。

前端侧边栏菜单顺序：**仓库管理 → 场景管理 → 空间审批 → 日志追踪**（非管理员用户不显示"空间审批"，且"日志追踪"对应调度事件列表页）。

---

## 7. 配置说明

### 7.1 Spring 配置项

```yaml
platform:
  ai:
    system-prompt-path: classpath:AGENT.md
    call:
      timeout-seconds: 60
      max-retries: 2
      retry-delay-ms: 1000
    context:
      max-tokens: 8000
      max-messages: 50
      compression-threshold: 0.8
      keep-recent-messages: 3
      max-message-content-length: 4000
      use-llm-summary: true
```

### 7.2 新增配置项说明

| 配置项 | 变更 | 原因 |
|---|---|---|
| `keep-recent-messages` | 从 10 改为 3 | 用户明确不需要保留太多历史，只保留最近 3 条 |
| `max-tokens` | 保持 8000 | deepseek-chat 支持 32K context，8000 留足安全余量 |
| `compression-threshold` | 保持 0.8 | 80% 触发压缩，给 Agent 输出预留空间 |
| `use-llm-summary` | 新增，默认 true | Smart 压缩优先用 LLM 生成更合理的结构化摘要，失败回退规则提取 |

---

## 8. 文件索引

| 文件 | 路径 | 职责 |
|---|---|---|
| `AgentService.java` | `ai/service/` | AI 对话主入口，串联全链路，写入/完成 AGENT 调度事件 |
| `AgentController.java` | `ai/controller/` | HTTP 接口 (chat/chatStream/trace 查询) |
| `ReactAgentConfig.java` | `ai/config/` | Agent Bean 配置，注册 Tools/Hooks，追加技能索引 |
| `SystemPromptConfig.java` | `ai/config/` | 系统提示词加载 |
| `DeepSeekChatModelConfig.java` | `ai/config/` | deepseek-chat LLM 模型配置 |
| `SystemPromptHook.java` | `ai/hook/` | 注入系统提示词（含技能索引） |
| `SkillIndexLoader.java` | `ai/skill/` | 扫描 SKILL.md frontmatter 生成技能索引（name+description），按需读取正文/子文档 |
| `ChatSession.java` | `ai/session/` | 会话数据模型 (record)，含 token 估算方法 |
| `ChatSessionManager.java` | `ai/session/` | 会话管理 (Caffeine 缓存，30min TTL) |
| `ContextCompressionService.java` | `ai/session/` | 上下文压缩 (LLM 摘要优先 + 规则回退 + 滑动窗口) |
| `ChatMessage.java` | `ai/session/` | 对话消息模型 (record) |
| `AgentCallManager.java` | `ai/` | 调用超时 + 重试封装 |
| `InputSanitizer.java` | `ai/` | 输入清洗 + Prompt 注入检测 |
| `ModelCallTraceAspect.java` | `ai/` | AOP 拦截 `ChatModel.call()`，记录 MODEL_CALL_STARTING/COMPLETED/FAILED，获取真实 token 用量 |
| `ToolTraceAspect.java` | `ai/` | AOP 拦截 `@Tool` 方法，记录 TOOL_CALL_STARTING/COMPLETED/FAILED |
| `AgentTraceContext.java` | `ai/` | ThreadLocal 持有当前 traceId，供 AOP 切面使用 |
| `OutputFormatFallbackService.java` | `ai/output/` | 四层输出解析兜底（sections-only），`deriveTextFromSections()` 从 sections 派生纯文本 |
| `ChatAssistantResult.java` | `ai/output/` | 输出数据模型 (record)，含 traceId/usedTools/confidence/sections/FaultDetail，`@JsonInclude(NON_NULL)` |
| `ContentBlock.java` | `ai/output/` | 结构化内容块 (record)，六种类型：heading/paragraph/list/code/quote/table |
| `AgentTraceLogService.java` | `ai/` | Trace 日志存储 (Redis List + ZSet，90 天 TTL)，支持完整 metadata 保留 |
| `AgentObservability.java` | `ai/` | 调用量/错误率/token 用量监控 |
| `ToolErrorFallback.java` | `ai/tools/` | 工具调用异常分析 |
| `ChatRequest.java` | `ai/dto/` | 对话请求 DTO (record) |
| `ChatResponse.java` | `ai/dto/` | 对话响应 DTO (record) |
| `AGENT.md` | `resources/` | 系统提示词（定义 sections-only 输出格式、六种 ContentBlock 类型、安全约束、Few-shot 示例） |
| `skills/` | `resources/skills/` | Skills 技能文档 (error-analysis, business-knowledge，含子文档) |
| `TaskTool.java` | `ai/tools/` | 任务查询工具：`getTask(taskId, spaceId)` 查询详情（含阶段日志预览）、`listTasks(sceneId?, spaceId)` 列出任务 |
| `SceneTool.java` | `ai/tools/` | 场景查询工具：`getSceneDetail(sceneId, spaceId)` 查询详情、`listScenes(repoId?, spaceId)` 列出场景 |
| `RepositoryTool.java` | `ai/tools/` | 仓库查询工具：`getRepository(repositoryId, spaceId)` 查询详情、`searchRepository(keyword, spaceId)` 按名称搜索 |
| `LogPreprocessingTool.java` | `ai/tools/` | 日志预处理分析工具：`analyzeLogs(taskId, spaceId)` 分析各阶段错误并列出失败用例 |
| `TraceQueryTool.java` | `ai/tools/` | Trace 查询工具：`queryTrace(traceId)` 查完整链路、`listRecentTraces(limit?)` 列最近记录、`getTraceStats()` 统计 |
| `LoadSkillContentTool.java` | `ai/tools/` | 按需加载技能 SKILL.md 正文：`loadSkill(name)` 返回正文 + 子文档清单 |
| `LoadSkillDocumentTool.java` | `ai/tools/` | 按需加载技能子文档：`loadSkillDocument(skillName, docName)` 读取子文档正文 |

---

## 9. 典型问题

### Q1: Agent 的 ReAct 循环最多多少次？

最多 20 次模型调用，由 `ModelCallLimitHook.runLimit(20)` 限制。这意味着 Agent 最多可以进行 20 轮 "思考→调用工具→观察" 循环。如果 20 轮后仍未生成最终回答，会强制中断并返回当前已有的回答。

### Q2: 上下文压缩什么时候触发？

两个触发条件满足其一即触发：
1. token 数超过 `compressionThreshold × maxTokens`（默认 80% × 8000 = 6400）
2. 消息数量超过 `maxMessages`（默认 50 条）

### Q3: 压缩后历史对话的信息会丢失吗？

会丢失细节，但保留结构化摘要。Smart 压缩优先用 LLM 生成摘要（保留关键标识符与根因结论），LLM 不可用时回退到规则提取，按轮次组织信息，保留：
- 每轮的用户提问（截断到 80 字符）
- 调用的工具名和参数摘要
- 工具返回结果的摘要
- 助手的结论（截断到 100 字符）

对于故障分析场景，这些信息足以让 Agent 理解历史对话脉络。

### Q4: 如果上下文仍然超长怎么办？

有 4 层保护：
1. **再次压缩**：token 仍超限则再次调用 `compressIfNeeded()`
2. **长消息截断**：单条消息超过 4000 字符自动截断
3. **硬截断兜底**：从最新消息往前保留，达到 80% token 预算即停止
4. **完全熔断**：全部策略用尽后仍超限，返回错误 "上下文过长，无法在 token 限制内处理。请开启新对话或简化问题"

### Q5: traceId 怎么用？

两种方式：
1. **对话内查询**：告诉 AI 助手 "查询 traceId: {traceId}"，Agent 调用 `TraceQueryTool.queryTrace(traceId)` 返回完整链路。
2. **HTTP 接口/前端时间线**：调用 `GET /api/ai/trace/{traceId}` 直接读 Redis List，或在调度事件列表中点击 Agent 调度事件的「查看 Trace」跳转到 `AgentTraceDetailView` 时间线页。

包含所有阶段日志、耗时、工具调用信息、token 预算等。

### Q6: Skills 是预加载到上下文的吗？

不是。启动时 `SkillIndexLoader` 只把各 `SKILL.md` 的 name + description 索引追加到系统提示词，正文不进入上下文。LLM 判断需要某技能时调用 `loadSkill(name)` 加载正文，再按正文指引用 `loadSkillDocument(skillName, docName)` 加载子文档，显著节省 token。

### Q7: 会话会过期吗？

会。会话存储在 Caffeine 本地缓存中，30 分钟无访问自动过期。过期后下次对话会创建新会话。用户可以通过刷新页面重新开始对话。

### Q8: 输出解析 4 层策略的意义是什么？

LLM 输出不稳定，可能返回：
- 标准 JSON（理想情况，BeanConverter 可直接解析 sections 数组）
- Markdown 包裹的 JSON（需要正则提取）
- sections 缺失或不完整（尝试从原始文本重建 sections）
- 纯文本（兜底包装成 paragraph 类型的 sections）

4 层策略确保任何格式的输出都能被正确解析为 ChatAssistantResult，最大程度保证 AI 回复的可用性。

### Q9: 为什么要用 sections-only 输出，移除 response 字段？

三个原因：
1. **避免双写不一致**：如果 LLM 同时输出 response（markdown 文本）和 sections（结构化 JSON），两者可能不一致，前端渲染时产生矛盾
2. **节省 token**：LLM 输出 sections-only 比同时输出 markdown 文本节省约 40-50% token（不需要写两次内容）
3. **前后端职责分离**：后端只负责结构化 sections，通过 `deriveTextFromSections()` 统一派生纯文本用于历史存储和 SSE streaming；前端可以选择结构化渲染（sections 解析完成后）或 markdown 渲染（流式打字中），互不干扰

### Q10: ModelCallTraceAspect 和 AgentService 中的 token 估算有什么区别？

- **AgentService 中的启发式估算**（中文×1.5 + 英文×0.25）：用于 token 预算检查（PROMPT_TOKEN_BUDGET trace 阶段），判断是否需要上下文压缩，是贯穿全链路的"近似值"
- **ModelCallTraceAspect 中的真实 token**（从 LLM API response.metadata.usage 获取）：用于成本统计和 tokenDiffVsEstimate 对比，是每次模型调用的"真实值"。记录在 MODEL_CALL_COMPLETED 阶段的 metadata 中，包含 promptTokens、completionTokens、totalTokens