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
| 会话存储 | Caffeine 本地缓存 | 30min TTL, max 10K |
| Trace 存储 | Redis (List + ZSet) | 90 天 TTL |
| 流式响应 | SseEmitter | SSE 协议 |

### 1.2 架构分层图

```
┌─────────────────────────────────────────────────────────────┐
│                       前端 (Vue 3)                           │
│  AiAssistantDialog.vue · ai.ts store · SSE EventSource       │
└──────────────────────────┬──────────────────────────────────┘
                           │ SSE /api/ai/chat/stream
┌──────────────────────────▼──────────────────────────────────┐
│                    AgentController                           │
│  同步 chat() · 流式 chatStream() · 会话管理 · traceId 返回    │
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
│  │ OutputFormatFallbackService 四层输出解析            │    │
│  │ AgentTraceLogService 全链路 trace 记录              │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────────────┬──────────────────────────────────┘
                           │ .call(prompt)
┌──────────────────────────▼──────────────────────────────────┐
│                    ReactAgent (ReAct)                         │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ SystemPromptHook 注入系统提示词                      │    │
│  │ SkillsAgentHook 加载 Skills 技能文档                 │    │
│  │ ModelCallLimitHook 限制最多 20 次模型调用           │    │
│  │                                                     │    │
│  │  ReAct Loop:                                        │    │
│  │   Think → Call Tool → Observe → Think → ... → Answer │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  Tools:                                                      │
│  ├── TaskTool          任务查询/分析                        │
│  ├── SceneTool         场景查询                              │
│  ├── RepositoryTool    仓库查询                             │
│  ├── LogPreprocessingTool 日志预处理分析                    │
│  └── TraceQueryTool    traceId 链路查询                     │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                    deepseek-chat (LLM)                       │
│  推理 · 工具调用决策 · 结构化输出生成                         │
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
│  └── 重复直到 LLM 输出最终回答（response 非空）或达到 20 轮上限 ──┘
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

Agent 通过 3 个 Hook 注入增强能力：

| Hook | 作用 | 说明 |
|---|---|---|
| `SystemPromptHook` | 注入系统提示词 | 从 `AGENT.md` 加载，定义 Agent 行为规则、输出格式、安全约束 |
| `SkillsAgentHook` | 加载 Skills 技能文档 | 从 `skills/` 目录加载 `error-analysis` 和 `business-knowledge` 两个技能 |
| `ModelCallLimitHook` | 限制模型调用次数 | 防止无限循环消耗 token |

### 2.4 完整调用时序

```
用户: "帮我分析任务 123 为什么失败了"
  │
  ├─ Think#1: 需要先获取任务详情
  │   └─ Call: TaskTool.getTask(taskId=123, spaceId=7)
  │
  ├─ Observe#1: 返回任务详情 (FAILED, 超时)
  │
  ├─ Think#2: 任务超时，需要看日志
  │   └─ Call: LogPreprocessingTool.analyzeLogs(taskId=123, spaceId=7)
  │
  ├─ Observe#2: 发现 "TimeoutError: 页面加载超时"
  │
  ├─ Think#3: 需要查看更多 trace 细节
  │   └─ Call: TraceQueryTool.queryTrace(traceId="xxx")
  │
  ├─ Observe#3: 返回完整 Agent 调用链路
  │
  └─ Think#4: 综合所有信息生成最终回答
      "任务 123 失败原因是页面加载超时..."
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
│                   历史消息 → 结构化轮次摘要                   │
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
│  AgentPromptBuilder.buildPrompt(session, request)            │
│    ├── System: systemPrompt                                 │
│    ├── 上下文信息 (spaceId, taskId 等)                      │
│    ├── 历史对话摘要 (结构化)                                 │
│    ├── 最近对话 (最近 3 条原始消息)                          │
│    └── 组装成完整 prompt 传给 Agent                         │
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

#### 3.3.1 轮次分组 (`groupIntoTurns`)

压缩前，消息列表被按轮次（Turn）分组：

```
消息列表: [user1, tool1, tool2, assistant1, user2, tool3, assistant2]
                         ↓ groupIntoTurns
轮次: [Turn(user1, [tool1, tool2], assistant1), Turn(user2, [tool3], assistant2)]
```

每个 `Turn` 包含：
- `userText`: 用户原始消息
- `toolCalls`: 工具调用列表（toolName + params + result）
- `assistantText`: 助手回复摘要

#### 3.3.2 Smart 压缩摘要格式

当触发 Smart 压缩时（token 超 80% 但未超 100%）：

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

当触发 Aggressive 压缩时（token 超 100% maxTokens）：

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
| L3 | Smart 压缩 | 80% maxTokens | 结构化摘要 + 保留 3 条最近消息 |
| L4 | Aggressive 压缩 | 100% maxTokens | 精简摘要 + 只保留最近 3 条 user/assistant |
| L5 | 长消息截断 | 4000 字符/条 | 超过自动截断并标注 |
| L6 | 硬截断兜底 | 80% maxTokens | 从最新消息往前保留，达到预算即停 |
| L7 | 完全熔断 | 仍超限 | 返回错误，不发送超长上下文给 LLM |

---

## 4. 工具调用

### 4.1 工具注册

工具通过 `ReactAgentConfig` 注册到 Agent：

```java
ReactAgent.builder()
    .model(model)
    .methodTools(
        repositoryTool,       // 仓库查询
        sceneTool,            // 场景查询
        taskTool,             // 任务查询/分析
        logPreprocessingTool, // 日志预处理分析
        traceQueryTool        // traceId 链路查询
    )
    .outputType(ChatAssistantResult.class)
    .hooks(...)
    .build();
```

### 4.2 工具列表

| 工具 | 方法 | 功能 | 输入 | 输出 |
|---|---|---|---|---|
| **TaskTool** | `getTask(taskId, spaceId)` | 查询任务详情 | taskId, spaceId | 任务状态、结果、用例统计 |
| | `listTasks(spaceId, sceneId)` | 列出空间/场景下的任务 | spaceId, sceneId? | 任务列表 |
| | `analyzeTask(taskId, spaceId)` | AI 分析任务 | taskId, spaceId | 根因分析、修复建议 |
| **SceneTool** | `getScene(sceneId, spaceId)` | 查询场景详情 | sceneId, spaceId | 场景配置信息 |
| | `listScenes(spaceId)` | 列出空间场景 | spaceId | 场景列表 |
| **RepositoryTool** | `getRepository(repoId, spaceId)` | 查询仓库详情 | repoId, spaceId | 仓库配置 |
| **LogPreprocessingTool** | `analyzeLogs(taskId, spaceId)` | 分析任务日志 | taskId, spaceId | 错误摘要、根因、建议 |
| **TraceQueryTool** | `queryTrace(traceId)` | 查询 Agent 调用链路 | traceId | 全链路 trace 日志 |

### 4.3 工具调用流程

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

### 4.4 工具调用异常处理

`ToolErrorFallback` 分析工具使用情况，检测异常模式：
- Agent 在没有足够信息时反复调用同一工具
- 工具调用返回空结果后 Agent 仍继续尝试
- 检测到异常时在 trace 日志中记录 `ToolCallAnalysis`，提供改进建议

---

## 5. 输出工程兜底

### 5.1 四层输出解析

Agent 回复可能不稳定（LLM 输出格式不规范），采用 4 层解析策略逐级兜底：

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
│    └── 失败 → 继续                                          │
│       │                                                      │
│       ▼                                                      │
│  第二层: JSON 提取                                           │
│    用正则提取 ```json``` 块或 { } 包裹的 JSON                 │
│    ├── 成功 → 解析 JSON 构造 ChatAssistantResult             │
│    │         (strategy: "json_extraction")                  │
│    └── 失败 → 继续                                          │
│       │                                                      │
│       ▼                                                      │
│  第三层: 字段级正则提取                                       │
│    用正则分别提取 response、usedTools、confidence 等字段     │
│    ├── 成功 → 构造 ChatAssistantResult                      │
│    │         (strategy: "field_extraction")                 │
│    └── 失败 → 继续                                          │
│       │                                                      │
│       ▼                                                      │
│  第四层: 纯文本兜底                                          │
│    直接使用原始文本作为 response，其他字段给默认值            │
│    └── (strategy: "fallback")                               │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 ChatAssistantResult 结构

```java
public record ChatAssistantResult(
    String response,              // AI 回复文本 (Markdown)
    List<String> usedTools,       // 使用过的工具名列表
    String confidence,            // 置信度 (HIGH/MEDIUM/LOW)
    String responseType,          // 响应类型 (ANALYSIS/QA/TRACE/...)
    FaultDetail faultDetail       // 故障详情 (可选)
) {
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

### 5.3 预处理

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
| Markdown 渲染 | 前端用 `marked` + DOMPurify 安全渲染 |
| 代码块保护 | `render-markdown.ts` 处理代码块溢出 |
| 纯文本兜底 | 四层解析全部失败时用原始文本作为回复 |

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
| `AGENT_CALL_SUCCESS` | Agent 调用成功 | responseLength, callDurationMs |
| `AGENT_CALL_FAILED` | Agent 调用失败 | errorMessage |
| `OUTPUT_PARSED` | 输出解析完成 | parsingStrategy, responseType, usedTools |
| `REQUEST_COMPLETED` | 请求完成 | processingTime, compressed |
| `SANITIZATION_FAILED` | 输入清洗失败 | rejectionReason |

#### 6.4.2 Trace 查询

用户可以在 AI 对话中让 Agent 查询 trace：
```
用户: "查询 traceId: abc-123-def"
Agent: 调用 TraceQueryTool.queryTrace("abc-123-def")
       → 返回该 traceId 的完整链路日志
       → 按时间顺序展示每个阶段的时间、耗时、元数据
```

#### 6.4.3 Redis 存储

| Redis Key | 类型 | TTL | 用途 |
|---|---|---|---|
| `agent:trace:{traceId}` | List\<JSON\> | 90 天 | 存储单个 trace 的全链路日志条目 |
| `agent:trace:index` | ZSet | 90 天 | 按时间排序的 traceId 索引 |

每个 trace 日志条目结构：
```json
{
  "traceId": "uuid",
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

### 6.5 性能参数

| 参数 | 默认值 | 配置项 |
|---|---|---|
| maxTokens | 8000 | `platform.ai.context.max-tokens` |
| maxMessages | 50 | `platform.ai.context.max-messages` |
| compressionThreshold | 0.8 | `platform.ai.context.compression-threshold` |
| keepRecentMessages | 3 | `platform.ai.context.keep-recent-messages` |
| maxMessageContentLength | 4000 | `platform.ai.context.max-message-content-length` |
| callTimeoutSeconds | 60 | `platform.ai.call.timeout-seconds` |
| callMaxRetries | 2 | `platform.ai.call.max-retries` |
| maxModelCalls | 20 | `ModelCallLimitHook.runLimit()` |
| sessionTtlMinutes | 30 | Caffeine `expireAfterWrite` |
| maxSessions | 10000 | Caffeine `maximumSize` |
| traceTtlDays | 90 | Redis TTL |

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
```

### 7.2 新增配置项说明

| 配置项 | 变更 | 原因 |
|---|---|---|
| `keep-recent-messages` | 从 10 改为 3 | 用户明确不需要保留太多历史，只保留最近 3 条 |
| `max-tokens` | 保持 8000 | deepseek-chat 支持 32K context，8000 留足安全余量 |
| `compression-threshold` | 保持 0.8 | 80% 触发压缩，给 Agent 输出预留空间 |

---

## 8. 文件索引

| 文件 | 路径 | 职责 |
|---|---|---|
| `AgentService.java` | `ai/service/` | AI 对话主入口，串联全链路 |
| `AgentController.java` | `ai/controller/` | HTTP 接口 (chat/chatStream) |
| `ReactAgentConfig.java` | `ai/config/` | Agent Bean 配置，注册 Tools/Skills/Hooks |
| `ChatSession.java` | `ai/session/` | 会话数据模型 (record) |
| `ChatSessionManager.java` | `ai/session/` | 会话管理 (Caffeine 缓存) |
| `ContextCompressionService.java` | `ai/session/` | 上下文压缩 (结构化摘要 + 滑动窗口) |
| `ChatMessage.java` | `ai/session/` | 对话消息模型 (record) |
| `AgentCallManager.java` | `ai/` | 调用超时 + 重试封装 |
| `InputSanitizer.java` | `ai/` | 输入清洗 + Prompt 注入检测 |
| `OutputFormatFallbackService.java` | `ai/output/` | 四层输出解析兜底 |
| `AgentTraceLogService.java` | `ai/` | Trace 日志存储 (Redis) |
| `AgentObservability.java` | `ai/` | 调用量/错误率监控 |
| `ToolErrorFallback.java` | `ai/` | 工具调用异常分析 |
| `SystemPromptConfig.java` | `ai/config/` | 系统提示词加载 |
| `AGENT.md` | `resources/` | 系统提示词 |
| `skills/` | `resources/skills/` | Skills 技能文档 (error-analysis, business-knowledge) |
| `TaskTool.java` | `ai/tools/` | 任务查询工具 |
| `SceneTool.java` | `ai/tools/` | 场景查询工具 |
| `RepositoryTool.java` | `ai/tools/` | 仓库查询工具 |
| `LogPreprocessingTool.java` | `ai/tools/` | 日志预处理分析工具 |
| `TraceQueryTool.java` | `ai/tools/` | Trace 查询工具 |

---

## 9. 典型问题

### Q1: Agent 的 ReAct 循环最多多少次？

最多 20 次模型调用，由 `ModelCallLimitHook.runLimit(20)` 限制。这意味着 Agent 最多可以进行 20 轮 "思考→调用工具→观察" 循环。如果 20 轮后仍未生成最终回答，会强制中断并返回当前已有的回答。

### Q2: 上下文压缩什么时候触发？

两个触发条件满足其一即触发：
1. token 数超过 `compressionThreshold × maxTokens`（默认 80% × 8000 = 6400）
2. 消息数量超过 `maxMessages`（默认 50 条）

### Q3: 压缩后历史对话的信息会丢失吗？

会丢失细节，但保留结构化摘要。压缩策略按轮次组织信息，保留：
- 每轮的用户提问（截断到 80 字符）
- 调用的工具名和参数摘要
- 工具返回结果的摘要
- 助手的结论（截断到 100 字符）

对于故障分析场景，这些信息足以让 Agent 理解历史对话脉络。

### Q4: 如果上下文仍然超长怎么办？

有 3 层保护：
1. **再次压缩**：token 仍超限则再次调用 `compressIfNeeded()`
2. **长消息截断**：单条消息超过 4000 字符自动截断
3. **硬截断兜底**：从最新消息往前保留，达到 80% token 预算即停止
4. **完全熔断**：全部策略用尽后仍超限，返回错误 "上下文过长，无法在 token 限制内处理。请开启新对话或简化问题"

### Q5: traceId 怎么用？

1. 在 AI 对话回答底部复制 traceId
2. 告诉 AI 助手 "查询 traceId: {traceId}"
3. Agent 调用 `TraceQueryTool.queryTrace(traceId)` 返回完整链路
4. 包含所有阶段日志、耗时、工具调用信息、token 预算等

### Q6: 会话会过期吗？

会。会话存储在 Caffeine 本地缓存中，30 分钟无访问自动过期。过期后下次对话会创建新会话。用户可以通过刷新页面重新开始对话。

### Q7: 输出解析 4 层策略的意义是什么？

LLM 输出不稳定，可能返回：
- 标准 JSON（理想情况，BeanConverter 可直接解析）
- Markdown 包裹的 JSON（需要正则提取）
- 非标准格式（需要字段级正则提取）
- 纯文本（兜底使用）

4 层策略确保任何格式的输出都能被正确解析，最大程度保证 AI 回复的可用性。