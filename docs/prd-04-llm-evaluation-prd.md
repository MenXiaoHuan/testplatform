# PRD-04 · 大模型评测模块

> 版本 v1.4 · 2026-10-28
> 
> **相对 v1.3 的关键变更**：① Prompt 管理定位修正——从"离线评测参数"升级为**被测 Agent 的 Prompt 托管中心**，Agent 通过平台 API 拉取当前生效版本；② 平台定位修正——Playwright 测试平台定位为**智能自动化测试平台**，包含 UI 自动化（已完成）、大模型评测（当前）、性能测试（后续规划）、接口自动化（后续规划）四条能力线。
> 
> **相对 v1.2 的关键变更**：补齐多租户并发问题——离线评测双层并发限流、Target 级共享隔离、在线策略扫描抖动、LLM-as-Judge 三层预算墙、EvalRun 幂等 + 乐观锁、Langfuse 查询全局闸门 + 缓存、A/B 流量分配纯计算无锁。
> 
> **相对 v1.1 的关键变更**：在线评测模型重构——从"自己发请求采样"改为"Langfuse SDK 先采集所有线上请求 → 评测平台基于已有的真实 Trace 做策略配置 + 自动评分 + 监控聚合"。在线评测**零自有流量、全部配置驱动**。
> 
> **相对 v1.0 的关键变更**：引入「被评目标 + 适配器」架构，从"只能评自家 Agent"升级为"可评任何 LLM、任何 Agent 服务"，通用性作为 P0 硬约束。

---

## 一、需求背景与目标

### 1.1 平台定位

本项目是一个**智能自动化测试平台**，覆盖四类测试能力：

```
┌─────────────────────────────────────────────────────────────────┐
│                    智能自动化测试平台                              │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  │ UI 自动化测试  │  │ 大模型评测   │  │ 性能测试      │  │ 接口自动化   │
│  │  ✅ 已完成     │  │  🚧 当前建设  │  │  📋 后续规划  │  │  📋 后续规划  │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
│                                                                 │
│  共享：用户体系 · space 多租户 · 仓库管理 · 任务执行 · MinIO 产物  │
└─────────────────────────────────────────────────────────────────┘
```

当前 UI 自动化模块已稳定运行（基于 Playwright + Docker 执行），AI 智能体模块已具备对话、工具调用、Trace 追踪等能力。**下一条能力线是大模型评测**——它既是独立的评测体系，也为后续智能判断（接口自动化的断言智能生成、性能测试的稳定性异常检测）提供可复用的评分器基础设施。

### 1.2 为什么需要大模型评测

AI 智能体已经在生产中使用，但缺少一套系统化的**模型效果度量与持续优化**手段：不知道当前 prompt / 模型版本在核心场景下表现如何，上线前无法量化比较候选方案，线上效果劣化无法及时感知，无法与行业通用基准（MMLU、HumanEval 等）做横向对比。

### 1.3 需求目标

基于 **Langfuse**（开源 LLM 可观测 + 评测平台），在现有平台内构建一套**通用的**大模型评测体系——不仅能评测本平台的 Agent，还能评测任何 LLM（自研或商用 API）、任何外部 Agent 服务（OpenAI Assistants、LangChain、AutoGen、自研 HTTP Agent 等）。

| 能力 | 说明 |
|------|------|
| **① Prompt 托管中心**（v1.4 定位修正） | 被测 Agent 的系统提示词、工具调用提示词、Judging 提示词等**统一托管在本平台**——Agent 通过平台 API 拉取当前生效版本，评测时自动关联 Prompt 版本与 Trace |
| **② 被评目标管理** | 统一注册所有"要被评测的对象"，通过适配器适配任意形态（LLM API / Agent HTTP / 内置 Agent / Python 脚本） |
| **离线评测** | 对固定评测集批量跑批，自动打分，产出评测报告 |
| **在线评测** | Langfuse SDK 先采集每个被评目标的所有**真实生产请求 → Trace**；评测平台再基于这些 Trace 做**策略配置**、**自动评分**、**监控指标聚合**。零自有流量，全部配置驱动 |
| **A/B 实验** | 同一请求在不同 Prompt / 模型版本 / Agent 实现下并行执行，对比效果并做显著性分析 |
| **评测集管理** | 自定义评测集的创建、标注、版本管理、分层（按业务场景） |
| **外部评测集接入** | 一键导入通用评测集（MMLU、HumanEval、GSM8K、TruthfulQA 等）作为基准线 |

### 1.4 成功标准

- Prompt 托管 API `GET /api/evaluation/prompts/{promptKey}?version=active` 提供 ≤ 10ms 响应，带本地缓存
- 能在 5 分钟内完成一次"改 Prompt → 选评测集 → 跑离线评测 → 看报告"的完整闭环
- 同一评测集可以一键切换不同被评目标（DeepSeek-chat / GPT-4o / Claude-3.5-Sonnet / 自家 Agent）跑横向对比
- A/B 实验页面能直接回答"新版本 / 新 Agent 是否优于旧版本（p < 0.05）"
- 任何 EvalTarget 的生产调用**默认自动产生 Langfuse Trace**，无需额外埋点，Trace 自动带上当前生效的 promptVersionId 标签
- 一条在线评测策略可以用 3 个字段描述：`哪个 Target` + `什么 Trace（按标签/时间/场景过滤）` + `跑哪些评分器`
- 线上监控页面默认能看到每个 Target 的 P50/P95 延迟、Token 消耗、自动评分均值/分布、低分率趋势——全部基于 Langfuse Trace 实时聚合

---

## 二、核心架构设计（本章为 v1.1 新增，是通用性的基石）

### 2.1 设计原则

> **离线评测引擎**只做两件事："喂样本 → 收输出 → 打分"。它不关心"喂给谁"，"谁"由适配器决定。
>
> **在线评测**零自有流量——Langfuse SDK 先把所有生产调用自动采集成 Trace，评测平台再基于 Trace 做**策略配置 + 自动评分 + 监控聚合**。

```
╔══════════════════════════════════════════════════════════════════════╗
║                     离线评测引擎（左侧）                               ║
║                                                                      ║
║  (数据集) ──► (TargetAdapter.invoke) ──► (Evaluator) ──► (报告)       ║
║                 ▲                                                    ║
║            只认接口，不认具体 Agent                                   ║
╚═════════════════╪════════════════════════════════════════════════════╝
                  │ 依赖倒置（TargetAdapter 接口）
                  │
                  ├──┬────────────┬────────────┬────────────┐
                  ▼  ▼            ▼            ▼            ▼
             ┌─────────┐  ┌────────────┐  ┌────────────┐  ┌─────────────┐
             │ LLM_API │  │ HTTP_AGENT │  │ BUILTIN_   │  │ PYTHON_     │
             │ Adapter │  │  Adapter   │  │ AGENT      │  │ SCRIPT      │
             └────┬────┘  └────┬───────┘  │  Adapter   │  │  Adapter    │
                  │            │           └────┬───────┘  └──────┬──────┘
                  ▼            ▼                ▼                 ▼
             DeepSeek    外部 Agent       自家 Agent            本地 .py
             GPT-4o      HTTP Service     AgentCallManager      subprocess
             Claude...   OpenAI Assist.                        执行
                  │            │                │                 │
                  ║            ║                ║                 ║
        ──────────╬════════════╬════════════════╬═════════════════╬────────── 生产请求
                  ║            ║                ║                 ║
                  ▼            ▼                ▼                 ▼
╔══════════════════════════════════════════════════════════════════════╗
║  Langfuse SDK 自动注入层（TargetAdapter 内部统一埋点）                 ║
║  每次生产调用 → 自动生成 Langfuse Trace + Observation + Token 统计      ║
╚══════════════════════════════════════════════════════════════════════╝
                  │
                  ▼
╔══════════════════════════════════════════════════════════════════════╗
║                     在线评测（右侧，v1.2 重构）                        ║
║                                                                      ║
║  Langfuse Trace 池                                                    ║
║  （全空间所有 Target 的真实生产请求，已带 target_id + scenario_tag）    ║
║       │                                                              ║
║       ├──► 策略引擎（eval_online_policy 表，可配置）                    ║
║       │        "Target X 的 trace，只要含标签「代码生成」，             ║
║       │         就跑 LLM-as-Judge 打分，分低于 60 告警"                ║
║       │                                                              ║
║       ├──► 监控指标聚合（实时从 Langfuse 查询 + 聚合）                  ║
║       │        P50/P95 延迟 · Token 消耗 · 自动评分均值/分布 · 低分率    ║
║       │                                                              ║
║       └──► 告警（低分率突增 / Token 异常 / 延迟突增）                   ║
╚══════════════════════════════════════════════════════════════════════╝
```

### 2.2 EvalTarget（被评目标）数据结构

一个 EvalTarget 就是"我要评谁"的描述，所有评测运行都绑定一个 targetId。

```json
{
  "id": 101,
  "spaceId": 1,
  "name": "DeepSeek-chat (温度 0.7)",
  "targetType": "LLM_API",          // LLM_API / HTTP_AGENT / BUILTIN_AGENT / PYTHON_SCRIPT
  "adapterConfig": { ... },          // 下面分类型说明
  "runtimeOptions": {
    "timeoutMs": 60000,
    "maxRetries": 2
  },
  "langfuseTracing": {               // ★ v1.2 新增：Langfuse 生产 Trace 采集配置
    "enabled": true,                // 默认 true；关掉则该 Target 的生产调用不进 Langfuse
    "publicKeyRef": "LANGFUSE_PUBLIC_KEY",   // 引用环境变量
    "secretKeyRef": "LANGFUSE_SECRET_KEY",
    "hostRef": "LANGFUSE_HOST",
    "sessionIdExtraction": {        // 如何从生产请求里提取"用户会话标识"
      "strategy": "HTTP_HEADER",
      "key": "X-User-Id"
    },
    "scenarioTagging": {            // 如何给每条 Trace 自动打场景标签
      "enabled": true,
      "rule": "TARGET_TAGS"         // 先简单用 EvalTarget 的 tags 字段；后续可扩展 LLM 自动分类
    }
  },
  "tags": ["生产使用", "主模型"],
  "status": "ACTIVE",
  "createdAt": "2026-10-28T10:00:00Z"
}
```

> **关键设计**：`langfuseTracing.enabled = true` 是默认值——即任何新注册的 EvalTarget，其生产调用**自动进 Langfuse**，无需额外埋点。Langfuse SDK 的初始化和 Trace 注入由 TargetAdapter 内部统一处理，调用方无感知。

### 2.3 四种 Adapter 的 `adapterConfig` 示例

#### ① LLM_API Adapter（纯大模型，无工具调用）

```json
{
  "provider": "DEEPSEEK",            // DEEPSEEK / OPENAI / ANTHROPIC / QWEN / 自定义 OpenAI-compatible
  "baseUrl": "https://api.deepseek.com",
  "apiKeyRef": "PLATFORM_DEEPSEEK_API_KEY",  // 引用环境变量名，不直接存 Key
  "model": "deepseek-chat",
  "temperature": 0.7,
  "maxTokens": 4096,
  "supportsSystemPrompt": true,
  "supportsJsonMode": true
}
```

#### ② HTTP_AGENT Adapter（任何提供了 HTTP Chat 接口的外部 Agent）

```json
{
  "endpoint": "https://external-agent.example.com/v1/chat",
  "httpMethod": "POST",
  "authType": "BEARER_TOKEN",
  "authCredentialRef": "EXTERNAL_AGENT_TOKEN",
  "requestTemplate": {               // 评测引擎会把 prompt 注入到 template 里的 {{input}} 位置
    "model": "agent-v2",
    "messages": [
      {"role": "system", "content": "{{system_prompt}}"},
      {"role": "user", "content": "{{input}}"}
    ],
    "temperature": 0.7
  },
  "responsePath": "$.choices[0].message.content",   // JSONPath 提取输出文本
  "supportsTraceCallback": false,
  "customHeaders": {"X-Trace-Source": "evaluation-platform"}
}
```

#### ③ BUILTIN_AGENT Adapter（本平台自己的 Agent）

```json
{
  "agentEntryPoint": "AgentCallManager.chat()",
  "systemPromptStrategy": "USE_ACTIVE_PROMPT_VERSION",  // 或 DIRECT_PASS_THROUGH
  "toolEnabled": true,
  "bindSpaceId": 1
}
```

#### ④ PYTHON_SCRIPT Adapter（本地 .py 脚本，subprocess 执行）

```json
{
  "scriptPath": "/opt/eval-scripts/my_agent.py",
  "pythonBinary": "/usr/bin/python3.11",
  "workingDirectory": "/opt/eval-scripts",
  "argsTemplate": ["--input", "{{input}}", "--system", "{{system_prompt}}"],
  "timeoutMs": 120000,
  "outputFormat": "STDOUT_TEXT",       // STDOUT_TEXT / STDOUT_JSON / FILE_PATH
  "env": {"API_KEY_REF": "MY_CUSTOM_KEY"}
}
```

### 2.4 TargetAdapter 接口（Java 伪代码）

```java
public interface TargetAdapter {

    /** 适配器类型标识，与 EvalTarget.targetType 对应 */
    TargetType type();

    /** 执行一次调用：输入 Prompt（可能多轮），返回 AgentResponse
     *  @param promptOverride null = Agent 自己拉 ACTIVE Prompt；有值 = Adapter 强制注入指定版本（A/B 实验用）
     */
    AgentResponse invoke(PromptInput prompt, TargetConfig config, PromptOverride promptOverride) throws TargetInvokeException;

    /** ★ Langfuse 自动埋点开关：该适配器是否支持在生产调用中自动生成 Trace */
    default boolean supportsLangfuseAutoTracing() { return true; }

    /** ★ 包装生产调用：在原始生产调用的前后自动注入 Langfuse trace/observation 生命周期 */
    <T> T wrapWithLangfuseTracing(Supplier<T> originalCall, TargetConfig config) throws TargetInvokeException;

    /** ★ 在线 Trace 消费：从 Langfuse 拉取该 Target 的生产 Trace，按过滤条件分页返回 */
    Page<LangfuseTrace> queryOnlineTraces(LangfuseTraceFilter filter, Pageable pageable);

    /** 健康检查：上线前探活 */
    HealthCheckResult healthCheck(TargetConfig config);

    /** 可选：这个 Adapter 的能力标签 */
    default Set<Capability> capabilities() { return Set.of(); }
}

/** v1.2 新增：在线 Trace 查询过滤条件 */
public record LangfuseTraceFilter(
    String targetId,            // 必填，哪个 EvalTarget 的 Trace
    Long startTimeMs,           // 时间窗口起始（ms epoch）
    Long endTimeMs,
    Set<String> scenarioTags,   // 场景标签过滤
    Set<String> sessionIds,     // 会话过滤
    Boolean onlyFailed,         // 只看失败的
    String searchQuery          // 文本搜索（搜 input/output 内容）
) {}

public record LangfuseTrace(
    String langfuseTraceId,
    String targetId,
    String sessionId,
    String inputText,
    String outputText,
    Long inputTokens,
    Long outputTokens,
    Long durationMs,
    Set<String> scenarioTags,
    Boolean success,
    Map<String, Object> rawObservation  // 透传 Langfuse Observation 原始字段
) {}

public record AgentResponse(
    String output,
    Long inputTokens,
    Long outputTokens,
    Long durationMs,
    Map<String, Object> rawMetadata,
    String externalTraceId
) {}
```

---

## 三、最终交付物

### 3.1 后端交付物（Spring Boot 工程内新增）

| 交付物 | 路径（建议） | 说明 |
|--------|-------------|------|
| **被评目标管理**（v1.1 新增） | `com.example.platform.evaluation.target.*` | Adapter 接口 + 4 种实现 + TargetService + TargetController + 健康检查 + **Langfuse 埋点自动注入** |
| Langfuse 客户端封装 | `com.example.platform.evaluation.langfuse.*` | Langfuse Java SDK 封装：Trace/Observation 写入 + Trace 查询（给在线评测消费）+ Score 写入 |
| 评测集管理 | `com.example.platform.evaluation.dataset.*` | CRUD、导入、导出、分层 |
| 提示词管理 | `com.example.platform.evaluation.prompt.*` | 版本化、diff、发布；适用目标类型标签 |
| 离线评测引擎 | `com.example.platform.evaluation.offline.*` | 并发调度、TargetAdapter 调用、自动评分、结果持久化 |
| **在线评测**（v1.2 重构） | `com.example.platform.evaluation.online.*` | Trace 消费服务 + 策略引擎 + 自动评分触发器 + 监控指标聚合 + 告警服务 |
| A/B 实验 | `com.example.platform.evaluation.abtest.*` | 实验创建、流量分配、并行执行、指标对比、显著性分析 |
| 评分器（Evaluator）体系 | `com.example.platform.evaluation.evaluator.*` | 规则、LLM-as-Judge、代码执行、相似度、JSON 结构校验、自定义 Python 脚本 |
| Flyway 迁移脚本 | `V11__evaluation_schema.sql` 起 | 含 `eval_target` + **`eval_online_policy`** 表 |
| 配置项 | `application.yml` `platform.evaluation.*` | Langfuse Key、并发、超时、Adapter 开关、Python 沙箱、**在线评测策略扫描间隔** |

### 3.2 前端交付物

| 交付物 | 路径（建议） | 说明 |
|--------|-------------|------|
| **被评目标管理页**（v1.1 新增） | `src/views/evaluation/TargetManagement.vue` | 目标列表、创建向导（按类型动态表单）、健康检查、在线编辑 adapterConfig、**Langfuse tracing 开关** |
| 评测集管理页 | `src/views/evaluation/DatasetManagement.vue` | 列表、创建、导入、标注 |
| **Prompt 托管中心**（v1.4 升级） | `src/views/evaluation/PromptManagement.vue` | promptKey 维度的版本链视图；创建 Draft、发布 ACTIVE、diff 对比、版本历史；**显示当前 ACTIVE 版本的拉取 API 地址（带 curl 示例）**；按 promptType 分类（SYSTEM/JUDGING/EXTRACTION） |
| 离线评测运行页 | `src/views/evaluation/OfflineRun.vue` | **先选 Target → 再选 评测集 → 再选 Prompt → 启动运行** |
| 评测报告页 | `src/views/evaluation/EvaluationReport.vue` | radar、breakdown、失败聚类、**多目标横向对比视图** |
| **在线评测策略配置页**（v1.2 新增） | `src/views/evaluation/OnlinePolicy.vue` | 策略 CRUD：选 Target → 过滤规则（场景标签/时间/会话）→ 选评分器 + 阈值告警 → 启停 |
| **在线监控大盘**（v1.2 重构） | `src/views/evaluation/OnlineMonitor.vue` | **左侧 Target 列表** + **右侧指标面板**：P50/P95 延迟、Token 消耗、自动评分均值/分布/低分率趋势；下方 Trace 浏览（可手动打分、看评分详情、跳 Langfuse UI） |
| A/B 实验页 | `src/views/evaluation/ABTest.vue` | **支持跨目标实验**（Control = DeepSeek, Treatment = GPT-4o） |
| 侧边栏导航 | 新增「大模型评测」分组 | 与「AI 助手」并列，权限跟随 spaceId |
| 路由 & API client | `src/api/evaluation.ts` | 统一封装所有评测接口 |

### 3.3 外部依赖

| 依赖 | 说明 |
|------|------|
| Langfuse 自部署实例 或 Langfuse Cloud | 官方 Docker Compose 或直接 SaaS |
| Python 3.11+（可选） | 若启用 PYTHON_SCRIPT Adapter，后端宿主需预装 |

### 3.4 非代码交付物

| 交付物 | 说明 |
|--------|------|
| `docs/evaluation-api.md` | OpenAPI 格式接口文档 |
| V11 迁移脚本 header 注释 | 每张表的用途、关键字段 |
| `docs/langfuse-integration.md` | 部署与集成指南 |
| **`docs/target-adapter-guide.md`**（v1.1 新增） | 四种 Adapter 配置字段详解 + 自定义扩展新 Adapter 的开发者指南 |

---

## 四、核心模块输入输出

### 4.1 被评目标管理（EvalTarget）——v1.1 新增模块

**输入**

| 输入类型 | 格式 | 约束 |
|----------|------|------|
| Web 创建向导 | 按 targetType 动态表单，必填字段校验（如 HTTP Agent 必须填 endpoint） | spaceId 必填，名称在 space 内唯一 |
| API 创建 | `POST /api/evaluation/targets`，body 即 EvalTarget 结构 | adapterConfig 里的 API Key 走环境变量引用，禁止明文 |
| 健康检查 | `POST /api/evaluation/targets/{id}/health` | 同步执行，超时 ≤ 10s，返回 status + latency |

**输出**

| 输出 | 说明 |
|------|------|
| Target Entity | 持久化到 `eval_target` 表 |
| 健康检查报告 | target 是否可达、调用延迟、是否支持流式/JSON mode 等能力标签 |

### 4.2 评测集管理

**输入**

| 输入类型 | 格式 | 约束 |
|----------|------|------|
| Web 表单 | 名称、描述、spaceId、场景标签、推荐目标类型（可选 hint） | spaceId 必填，名称在 space 内唯一 |
| 文件导入 | JSONL / CSV / Parquet | 单文件 ≤ 50MB，JSONL 每行必须含 `input` 字段 |
| 外部评测集导入 | HuggingFace datasets URL / Langfuse Dataset API / 内置目录（MMLU、GSM8K、HumanEval 子集） | 内置目录直接可选，无需网络；URL 导入需白名单 |
| 标注 | Golden Answer、Reference Context、Metadata | Golden Answer 支持多版本 |

**输出**

| 输出 | 说明 |
|------|------|
| Dataset Entity | 持久化到 `eval_dataset` 表，含版本号 |
| 样本列表 | 分页查询，支持按标签、难度、是否已标注筛选 |
| 导出 | JSONL / CSV |

### 4.3 Prompt 托管中心（v1.4 定位修正——Agent 的 Prompt 源，评测默认不碰，只在 A/B 时强制注入）

#### 核心概念

> **被测 Agent 的 Prompt 托管在本平台，而不是硬编码在 Agent 代码里。Agent 通过拉取 API 获取当前生效版本。**

Prompt 托管中心的角色分两种场景：

| 场景 | Prompt 由谁决定 | 平台怎么管 |
|------|---------------|-----------|
| Agent 日常生产 + 离线评测（常规跑） | **Agent 自己拉** ACTIVE 版本 | Prompt 托管中心提供拉取 API；评测引擎只喂样本拿输出，**不碰 Prompt** |
| A/B 实验 + 固定版本 benchmark | **评测引擎强制注入** | Control 组绑 Prompt v1.0、Treatment 组绑 Prompt v2.1，Adapter 层绕过 Agent 自己的拉取逻辑，直接注入指定版本 |

> 也就是说：**Prompt 托管中心本质上是 Agent 的依赖服务**，不是评测引擎的依赖服务——除非在做 A/B 对比。

#### Adapter 层的 Prompt 注入能力（v1.4 新增，实现"强制指定 vs 正常拉取"的切换）

```java
public record PromptOverride(
    String promptVersionId,       // null = Agent 自己拉 ACTIVE；有值 = 强制注入
    String promptKey              // 哪个 promptKey 的哪个版本
) {}

// TargetAdapter.invoke 签名升级
AgentResponse invoke(
    PromptInput prompt, 
    TargetConfig config, 
    PromptOverride promptOverride   // ★ 新增，默认 null
) throws TargetInvokeException;
```

Adapter 内部逻辑：

```
promptOverride == null?
  ├─ YES (默认) → 让 Agent 正常走自己的逻辑（BUILTIN_AGENT 调 Prompt 托管 API 拉 ACTIVE；HTTP_AGENT 发请求给外部 Agent，它自己决定用什么 Prompt）
  └─ NO        → Adapter 先拉取 promptOverride.promptVersionId 对应的模板 → 直接构造 PromptInput → 跳过 Agent 自己的拉取逻辑
```

这个切换点是 Prompt 托管中心和评测引擎交互的唯一接口。

#### Prompt 版本数据结构

```json
{
  "id": 456,
  "spaceId": 1,
  "promptKey": "agent-system-prompt",        // 业务唯一标识，Agent 通过这个 key 拉
  "name": "Agent 系统提示词",
  "description": "主 Agent 的 system prompt，定义角色、能力边界、工具调用规则",
  "promptType": "SYSTEM",                     // SYSTEM / JUDGING / EXTRACTION / CUSTOM
  "template": [                               // 支持多轮 Message
    {"role": "system", "content": "你是一个智能测试平台的 AI 助手...{{context}}"},
    {"role": "user", "content": "{{user_input}}"}
  ],
  "variables": ["context", "user_input"],
  "version": "v2.1",
  "changeLog": "修复了工具调用格式说明，新增安全红线约束",
  "status": "ACTIVE",                         // DRAFT / ACTIVE / DEPRECATED
  "createdBy": "admin",
  "createdAt": "2026-10-28T10:00:00Z",
  "supersededBy": null
}
```

#### Prompt 版本管理（CRUD，评测平台内部）

| 操作 | API | 说明 |
|------|-----|------|
| 创建 Draft 版本 | `POST /api/evaluation/prompts` | promptKey 在 space 内唯一 |
| 编辑 Draft 版本 | `PUT /api/evaluation/prompts/{id}` | 只允许改 status=DRAFT 的 |
| 发布为 ACTIVE | `POST /api/evaluation/prompts/{id}/activate` | 同一 promptKey 同一 space 同时只能 1 个 ACTIVE（数据库唯一约束）；发布时旧 ACTIVE 自动 DEPRECATED + supersededBy |
| 废弃 | `POST /api/evaluation/prompts/{id}/deprecate` | 可指定 supersededBy |
| Diff 对比 | `GET /api/evaluation/prompts/diff?a=455&b=456` | 前端渲染 |
| 版本历史 | `GET /api/evaluation/prompts?promptKey=agent-system-prompt` | 按 version desc |
| 版本详情 | `GET /api/evaluation/prompts/{id}` | |

#### Prompt 拉取 API（Agent 日常调用的接口）

**设计原则**：像配置中心一样轻量——RESTful + 短平快 + ETag/缓存头。

| 操作 | API | 说明 |
|------|-----|------|
| 拉取 ACTIVE 版本 | `GET /api/evaluation/prompts/{promptKey}?spaceId=1` | Agent 主用接口；**不填 version 默认拿 ACTIVE** |
| 拉取指定版本 | `GET /api/evaluation/prompts/{promptKey}?spaceId=1&version=v2.1` | Agent 调试用；评测引擎 A/B 实验用 |

**响应示例**：

```json
// GET /api/evaluation/prompts/agent-system-prompt?spaceId=1
// 200 OK
{
  "promptKey": "agent-system-prompt",
  "version": "v2.1",
  "status": "ACTIVE",
  "promptType": "SYSTEM",
  "template": [
    {"role": "system", "content": "你是一个智能测试平台的 AI 助手...{{context}}"},
    {"role": "user", "content": "{{user_input}}"}
  ],
  "variables": ["context", "user_input"],
  "etag": "W/\"v2.1:sha256:abc123\""
}
// 带 If-None-Match 命中 → 304 Not Modified

// 403 跨 space / 401 未登录 / 404 promptKey 不存在
```

**拉取接口性能**：P95 ≤ 10ms，Caffeine 本地缓存（key = promptKey + spaceId + version），`activate` 时立即失效。

#### Prompt 与 Langfuse Trace 的关系

```
Agent 生产调用 / 离线评测（不指定 Prompt）
  │
  ├── Agent 自己拉 ACTIVE Prompt v2.1  ← Prompt 托管中心
  ├── 跑起来
  └── Langfuse Trace 自动写入标签：
        prompt_version_id = 456
        prompt_version_name = v2.1
        → 监控大盘可以按"用的哪个 Prompt 版本"分组看质量趋势

A/B 实验（指定 Prompt）
  │
  ├── Control 组：Adapter 强制注入 Prompt v1.0 → Langfuse 标签写 v1.0
  └── Treatment 组：Adapter 强制注入 Prompt v2.1 → Langfuse 标签写 v2.1
        → Langfuse 里两个版本的 Trace 可以直接并排对比
```

> **关键点**：Langfuse Trace 的 prompt_version_id 永远是"Agent 实际使用的那个版本"——无论来源是 Agent 自己拉的 ACTIVE 还是 A/B 注入的指定版本。这个标签是 TargetAdapter 在 `wrapWithLangfuseTracing` 里统一写入的，评测引擎不直接管。

#### 与现有系统的集成点

| 现有模块 | 集成方式 |
|----------|----------|
| `SystemPromptConfig` / `SystemPromptHook` | **BUILTIN_AGENT Target** 的 SystemPromptHook 改为调用 `GET /api/evaluation/prompts/{promptKey}`（不指定 version 拿 ACTIVE）。`classpath:AGENT.md` 保留为首次启动时的 seed 数据（C24 约束：首次启动自动注册为 `agent-system-prompt` 的 DRAFT 版本） |
| Agent 代码里写死的 Prompt | 逐步迁移：system prompt → Judge prompt → 工具描述，全部从硬编码改成拉取 API |

### 4.4 离线评测引擎（v1.1 关键改动：Target 驱动；v1.4 修正：默认不指定 Prompt）

**输入**

| 输入 | 说明 |
|------|------|
| **Target ID**（必填） | 指定"评谁" |
| Prompt 版本 ID（可选） | **不填** → Agent / LLM 按自己的正常逻辑拉 ACTIVE 版本（Agent 调平台托管 API）；**填了** → Adapter 层强制注入这个版本，Agent 跳过自己的拉取逻辑。用于想固定某个旧版本做 benchmark 的场景 |
| 评测集 ID | 必填 |
| 评分器列表 | 可多选：规则匹配 / 精确匹配 / LLM-as-Judge / 代码执行 / 语义相似度 / JSON Schema 校验 |
| Target 运行时覆盖 | 可临时覆盖 Target 配置里的 temperature / timeout 等 |
| 并发数 | 默认 2，上限受 `platform.evaluation.offline.max-concurrent` 约束 |
| 采样比例 | 默认 100%，可设 5% 快速冒烟 |

**输出**

| 输出 | 说明 |
|------|------|
| 运行状态机 | PENDING → RUNNING → COMPLETED / FAILED，进度实时 SSE 推送 |
| 每条样本结果 | Target 输出、各评分器分数、Golden Answer 对比、耗时、token 消耗、target 外部 traceId（如有） |
| 聚合报告 | 总分、各维度分、失败样本 TopN、常见错误聚类 |
| Langfuse 同步 | 每个样本一条 Trace + 各评分器 Score |
| **横向对比数据**（v1.1 新增） | 同一评测集可以跑多个 Target，报告页生成并排对比矩阵 |

**离线评测执行流程图（v1.1）**

```
用户点击「启动评测」
      │
      ▼
创建 EvalRun（PENDING）— 绑定 targetId + datasetId + promptVersionId
      │
      ▼
并发调度 Executor 线程池
      │
      ├─ 逐样本：构造 PromptInput
      │           │
      │           ▼
      │     targetAdapter.invoke(prompt, targetConfig)   ◄── 通用性核心：这里调谁不关心
      │           │
      │           ├─── LLM_API Adapter ───► DeepSeek API
      │           ├─── HTTP_AGENT Adapter ───► 外部 https://.../chat
      │           ├─── BUILTIN_AGENT Adapter ───► AgentCallManager
      │           └─── PYTHON_SCRIPT Adapter ───► subprocess python script.py
      │           │
      │           ▼
      │     依次调用各 Evaluator 评分
      │           │
      │           ▼
      │     写入 EvalCaseResult + 上报 Langfuse
      │
      ▼
所有样本完成 → 聚合计算 → 更新 EvalRun（COMPLETED）
      │
      ▼
SSE 通知前端 → 展示报告 / 对比矩阵
```

### 4.5 在线评测（v1.2 重构：Trace 驱动 + 零自有流量 + 全配置化）

#### 核心概念

在线评测**不自己发请求**。它的工作分两条流水线：

```
流水线 A：生产 Trace 自动采集
──────────────────────────────
  EvalTarget 的生产调用
       │
       ▼
  TargetAdapter.wrapWithLangfuseTracing(originalCall)   ← 自动注入，调用方无感知
       │
       ▼
  Langfuse Trace + Observation + Token + 延迟 + 场景标签
       │
       ▼
  存入 Langfuse（自部署或 Cloud）

流水线 B：基于 Trace 的评测与监控
──────────────────────────────
  Langfuse Trace 池（流水线 A 已写入）
       │
       ├─► 策略引擎（eval_online_policy 表）
       │      周期扫描（默认每 5min），对新 Trace 跑匹配的评分器
       │      评分结果回写 Langfuse Score
       │      低分 → 告警
       │
       ├─► 监控聚合 API
       │      前端请求 → 后端实时查 Langfuse → 按 Target + 时间窗口聚合指标
       │      延迟(P50/P95) · Token · 自动评分均值/分布/低分率 · 成本
       │
       └─► 手动评分入口
              运营/测试人员在监控大盘浏览 Trace → 对单条手动打分
              → 打分同步回 Langfuse Score
```

#### 策略（eval_online_policy）数据结构

> **一条策略 = 一个 Target + 一组 Trace 过滤条件 + 一组评分器 + 告警阈值**。这就是用户说的"可配置"的核心载体。

```json
{
  "id": 5001,
  "spaceId": 1,
  "name": "DeepSeek-代码生成-自动评分",
  "targetId": 101,                          // 必填：评哪个 Target
  "status": "ACTIVE",                       // ACTIVE / PAUSED
  "filter": {                               // 选哪些 Trace 出来跑评分器
    "timeWindowMinutes": 5,                 // 最近 N 分钟内的新 Trace
    "scenarioTags": ["代码生成", "代码修复"],  // 场景标签匹配（AND 语义：Trace 必须同时含这些标签；OR 也可配）
    "minInputLength": 10,                   // 过滤掉过短的噪声 Trace
    "excludeStatus": ["FAILED"]             // 排除生产调用本身就失败的 Trace
  },
  "evaluators": [                           // 按顺序依次执行
    {
      "type": "JSON_SCHEMA",                // ★ 对代码生成先用 JSON 结构校验
      "config": {"schemaRef": "code_output_schema_v1.json"},
      "weight": 0.25
    },
    {
      "type": "RULE_MATCH",
      "config": {"keywords": ["function", "return", "import"], "matchAll": false},
      "weight": 0.25
    },
    {
      "type": "LLM_AS_JUDGE",               // ★ 再用 LLM-as-Judge 评语义质量
      "config": {
        "judgePromptRef": "code_quality_judge_v2",
        "scoreRange": [0, 100],
        "goldenAnswerDatasetId": 200         // 可选：关联一个评测集做半监督 Judge
      },
      "weight": 0.50
    }
  ],
  "alerting": {                             // 全部可关掉
    "enabled": true,
    "lowScoreThreshold": 60,                // 单条 Trace 的加权总分低于此值 → 单条告警
    "highLowScoreRate": 0.15,               // 窗口内低分率 > 15% → 聚合告警
    "timeWindowMinutes": 30,
    "channels": ["LOG"]                     // v1.2 只有日志；后续加邮件/飞书/Webhook
  },
  "rateLimit": {
    "maxEvaluationsPerMinute": 60,          // 该策略每分钟最多评多少条 Trace，防 LLM-as-Judge 打爆
    "cooldownMinutesAfterAlert": 10          // 告警后冷静期内不再重复告警
  }
}
```

#### 输入

| 输入类型 | 格式 | 约束 |
|----------|------|------|
| 策略创建 | `POST /api/evaluation/online-policies`，body 即上面的 Policy 结构 | `targetId` 必填；`evaluators` 至少 1 个；同 space 内同名不可重复 |
| 策略启停 | `PATCH /api/evaluation/online-policies/{id}` 只改 `status` | ACTIVE 策略开始周期扫描；PAUSED 立即停止 |
| 策略测试 | `POST /api/evaluation/online-policies/{id}/test`，body 含一条 Langfuse Trace 样例 | 同步执行，返回各评分器的分数明细，用于调参 |
| 手动评分 | `POST /api/evaluation/online-traces/{langfuseTraceId}/manual-score`，body 含 `score(1-5)` + `tags[]` + `comment` | 手动分数会同步回写 Langfuse Score |
| 监控查询 | `GET /api/evaluation/online-metrics?targetId=101&from=...&to=...` | 返回聚合指标 JSON |
| Trace 浏览 | `GET /api/evaluation/online-traces?targetId=101&scenarioTag=代码生成&page=1&size=20` | 分页拉取 Langfuse Trace 摘要 + 已关联评分 |

#### 输出

| 输出 | 说明 |
|------|------|
| 策略 Entity | 持久化到 `eval_online_policy` 表 |
| 在线评分结果 | 每条 Trace 跑完后回写 Langfuse Score，同时本地 `eval_online_scoring` 表存一份留痕 |
| 监控指标 JSON | 前端直接渲染图表，包含：延迟 P50/P95/P99、延迟分布直方图、Token 消耗量、成本估算、自动评分均值 + 分布直方图 + 低分率 |
| Trace 详情页 | 并排展示 input / output / 各评分器分数 + 手动评分区 + "跳转到 Langfuse UI" 按钮 |
| 告警日志 | 格式：`[OnlineEvalAlert] policy=5001 target=101 type=LOW_SCORE_RATE value=22% threshold=15% window=30min` |

#### 监控指标聚合 API 响应示例

```json
{
  "targetId": 101,
  "targetName": "DeepSeek-chat (温度 0.7)",
  "fromMs": 1730000000000,
  "toMs": 1730086400000,
  "granularity": "HOUR",
  "metrics": {
    "requestCount": 12840,
    "successRate": 0.987,
    "latency": {
      "p50Ms": 820,
      "p95Ms": 3400,
      "p99Ms": 12100,
      "histogram": {"0-1s": 8200, "1-3s": 3100, "3-10s": 1400, "10s+": 140}
    },
    "tokens": {
      "inputTotal": 24500000,
      "outputTotal": 8900000,
      "avgPerRequest": 2596
    },
    "costEstimate": {
      "totalUsd": 14.32,
      "perRequestUsd": 0.0011
    },
    "autoScore": {
      "evaluatorId": "policy-5001-weighted",
      "mean": 72.4,
      "median": 76,
      "distribution": {"0-20": 120, "20-40": 480, "40-60": 1200, "60-80": 6800, "80-100": 4240},
      "lowScoreRate": 0.098
    }
  },
  "timeSeries": [
    {"timestampMs": 1730003600000, "requestCount": 520, "p95Ms": 3200, "avgScore": 73.1},
    {"timestampMs": 1730007200000, "requestCount": 501, "p95Ms": 3500, "avgScore": 71.8}
  ]
}
```

### 4.6 A/B 实验（v1.4 补充：Prompt A/B 是核心场景）

A/B 实验有两种常见形态：
- **跨 Target 对比**：同一个 Prompt，比不同 Agent（DeepSeek vs GPT-4o）——**Prompt 版本可选**（不填各自拉 ACTIVE）
- **Prompt 版本对比**：同一个 Target，比不同 Prompt（v1.0 vs v2.1）——**Prompt 版本必填**（否则两组都拉 ACTIVE，等于没对比）

**输入**

| 输入 | 说明 |
|------|------|
| 实验名称 + 假设 | 例："新 Prompt v2.1 是否比 v1.0 提升代码生成准确率" |
| **Control 组** | EvalTarget + **Prompt 版本（跨 Prompt 对比时必填）** |
| **Treatment 组** | EvalTarget + **Prompt 版本（跨 Prompt 对比时必填）**；可多 Treatment（最多 3 组） |
| 流量分配 | 线上百分比 或 离线评测集 |
| 核心指标 | 主指标（必须有）+ 护栏指标 |

**A/B 实验执行时的 Prompt 注入逻辑**

```
Control.invoke(input, config, promptOverride = {promptKey, version = "v1.0"})
Treatment.invoke(input, config, promptOverride = {promptKey, version = "v2.1"})
  │
  └── Adapter 层看到 promptOverride != null
        → 拉指定版本模板 → 注入 → Agent 跳过自己的拉取
        → Langfuse Trace 写 prompt_version_id = 指定版本
```

**输出**

| 输出 | 说明 |
|------|------|
| 实验状态 | DRAFT → RUNNING → PAUSED → COMPLETED |
| 并行执行 | 同一 input 在两组下都跑，共享 context |
| 对比面板 | 并排输出、分数差、token 差、延迟差；Prompt 版本对比时并排显示"用的哪个 Prompt + 版本号" |
| 统计结论 | t 检验 / Mann-Whitney U，输出 p-value + 置信区间 |
| 胜出建议 | "Prompt v2.1 显著优于 v1.0（p=0.02），建议全量发布" |

### 4.7 评分器（Evaluator）体系

| 评分器类型 | 输入 | 输出 | 适用场景 |
|-----------|------|------|----------|
| **规则匹配** | Target 输出 + 正则/关键词列表 | 0/1 或 0-100 分 | 结构化输出校验、安全红线 |
| **精确匹配** | Target 输出 + Golden Answer | 0/1 分 | 封闭域问题 |
| **LLM-as-Judge** | Target 输出 + Golden Answer + Judging Prompt | 0-100 分 + 理由 | 开放式问答、总结 |
| **语义相似度** | Target 输出 + Golden Answer | cosine 0-1 | 可等价表述 |
| **代码执行** | 代码输出 + 测试用例 | pass@1 / pass@k | HumanEval、MBPP |
| **JSON 结构校验** | 输出 + JSON Schema | 0/1 + 错误列表 | Agent 工具调用输出 |
| **自定义脚本**（v1.1 新增） | Python 脚本路径 + 输入 JSON | 0-100 分 + 自定义元数据 | 业务特有评分逻辑，无法开箱即用 |

---

## 五、约束条件

### 5.1 技术约束

| 编号 | 约束 | 理由 |
|------|------|------|
| C1 | 评测引擎**不得直接**调用任何具体 LLM/Agent 实现，必须通过 `TargetAdapter` 接口 | 这是通用性的根基——新增被评类型 = 新增一个 Adapter 实现 |
| C2 | 本平台 Agent 评测通过 `BUILTIN_AGENT Adapter` 复用现有 `AgentCallManager`，不绕开也不特殊对待 | 保证 Trace、Quota、超时/重试策略全部复用 |
| C3 | 评测模块所有表必须带 `space_id`，AgentService 调用前必须校验当前用户属于该 space | 多租户隔离 |
| C4 | 离线评测并发数不得超过 `platform.evaluation.offline.max-concurrent`（默认 4） | 防止打爆 LLM API 和机器内存 |
| C5 | Langfuse 上报失败不得阻塞主流程，必须异步+降级（本地落盘 + 定期重试） | 可观测性是旁路，评测结果不能丢 |
| C6 | 评分器执行总超时 ≤ 单样本超时的 30% | 防止评分器成为瓶颈 |
| C7 | HTTP_AGENT Adapter 的 endpoint 必须经过 SSRF 校验（禁止内网 IP 段、禁止环回地址） | 防止恶意 EvalTarget 配置探测内网 |
| C8 | PYTHON_SCRIPT Adapter 执行必须设独立工作目录 + CPU/内存限制 + 执行超时上限，且禁止脚本访问 `~/.ssh`、`/etc/shadow` 等敏感路径 | 防止恶意脚本越权 |
| C9 | adapterConfig 里的密钥类字段一律存"环境变量引用名"，后端在运行时从 `System.getenv()` 读取，DB 中不得出现明文 Key | 防止泄露 |
| C10 | 外部评测集导入必须经过 HuggingFace URL 白名单 + 文件大小 ≤ 50MB | 防止 SSRF 和 DoS |
| C11 | **v1.2 新增**：所有在线 Trace 必须带 `target_id` 标签写入 Langfuse，否则在线评测策略引擎无法正确归属 | TargetAdapter 内部在调用 `wrapWithLangfuseTracing` 时强制注入 |
| C12 | **v1.2 新增**：Langfuse 查询（给监控聚合和策略引擎消费）必须走 **只读 API**，禁止用写 Key 查询 | 防止线上误删数据；Langfuse Cloud 支持 Read Key / Write Key 分离 |
| C13 | **v1.2 新增**：在线策略引擎周期扫描必须有**断点续跑**机制——每条 Langfuse Trace 只跑一次评分器，靠 `eval_online_scoring` 表记录已评状态（unique on policy_id + langfuse_trace_id） | 防止重复消耗 LLM-as-Judge Token |
| C14 | **v1.2 新增**：单个 EvalTarget 的 Langfuse tracing 关闭 (`langfuseTracing.enabled=false`) 时，该 Target 从在线评测策略引擎和监控大盘的候选列表中自动隐藏 | 避免空策略和空数据 |
| C15 | **v1.3 并发约束**：离线评测并发**双层限制**——全局 `max-concurrent`（默认 4）+ 每个 `EvalRun` 自配的 `maxConcurrency`（默认 2，上限取二者小值）；**每个 space 同时 RUNNING 的 EvalRun 数 ≤ 3** | 防止多 space 同时开跑把机器和 LLM API 打爆；space 级上限是硬性护栏 |
| C16 | **v1.3 并发约束**：同一 `EvalTarget` 同时被多少个离线 EvalRun 使用有上限（默认 2）——超过时新 Run 排队等待，而非直接并发调用同一个 Target | 多个 space 共用同一个 Target（比如同一个 DeepSeek API key）时，各自的离线评测互相限速 |
| C17 | **v1.3 并发约束**：在线策略引擎**扫描调度带随机抖动**——每条策略的扫描时间在其 interval 基础上加 `±20%` 随机偏移（默认 interval=5min → 实际 4-6min）。禁止所有 ACTIVE 策略在同一个时间点集中扫描 | 10 个 space 各开 3 条策略 = 30 条循环同时扫 → 抖动打散，Langfuse 查询压力 + LLM-as-Judge 压力平滑 |
| C18 | **v1.3 并发约束**：LLM-as-Judge 评分器**三层预算墙**——① 全局日预算 `platform.evaluation.judge.global-daily-tokens`（默认 5M）；② 每 space 日预算 `space.maxJudgeTokensPerDay`（默认全局 ÷ 活跃 space 数，可覆盖）；③ 每条策略分钟级 `rateLimit.maxEvaluationsPerMinute`。三层任何一层耗尽 → 该策略自动降级为规则评分器并记日志 | 一个 space 的 10 条激进策略不能吃掉整个平台的 Judge 预算 |
| C19 | **v1.3 并发约束**：EvalRun 启动必须做**幂等校验**——同一用户对同一评测集 + Prompt + Target 的相同配置，60s 内重复点击"启动"只产生一条 EvalRun（用 Redis 做分布式锁：`eval:run:dedupe:{spaceId}:{datasetId}:{promptVersionId}:{targetId}`） | 防止用户连点两次或前端重试导致重复 Run |
| C20 | **v1.3 并发约束**：EvalRun 状态机**只能单向前进**——PENDING → RUNNING → COMPLETED/FAILED，不可逆。状态转移用**乐观锁**（`eval_run.version` 字段 + UPDATE WHERE version = ?），拒绝并发状态冲突 | 防止多线程同时更新同一条 Run 的状态导致错乱 |
| C21 | **v1.3 并发约束**：Langfuse 查询**全局并发闸门**——最多同时 8 个线程在查 Langfuse（`Semaphore` 控制），多出来的排队等待（最多 30s）。监控聚合 API 结果带 60s 本地缓存（按 targetId + 时间窗口 hash 做 key） | Langfuse Cloud 有 API rate limit，10 个 space 各查一次就可能撞线；缓存大幅降低查询量 |
| C22 | **v1.3 并发约束**：A/B 实验的线上流量分配必须**原子化**——用 `(userId + experimentId).hash % 100 < trafficPercentage` 的纯计算方式，**不查 DB 不写 DB**，完全无锁无竞争；实验流量变更最多影响未来请求，历史已分配的 group 不可变 | 线上 A/B 每一个生产请求都要做分配，必须是 O(1) 纯计算；查 DB 或加锁会成为生产调用瓶颈 |
| C23 | **v1.4 新增**：Prompt 拉取接口 `GET /api/evaluation/prompts/{promptKey}` 必须满足：① P95 ≤ 10ms（Caffeine 本地缓存）；② 拉取时必须校验 `spaceId` + token（防止跨 space 拉 Prompt）；③ 同一 promptKey 在同一 space 同时 ACTIVE 的版本数 ≤ 1（数据库级约束 `prompt_key + space_id + status=ACTIVE UNIQUE`） | Prompt 托管是被测 Agent 的生产依赖，必须快、必须安全、不能有歧义 |
| C24 | **v1.4 新增**：首次启动时 `classpath:AGENT.md` 自动 seed 为 Prompt 托管中心的第一个 DRAFT 版本（promptKey = `agent-system-prompt`），用户在前端发布 ACTIVE 后生效 | 避免现有 BUILTIN_AGENT 切换到新 Prompt 系统时"裸奔"——先有可用的默认版本 |
| C25 | **v1.4 新增**：Langfuse Trace **必须**写入 `prompt_version_id` 和 `prompt_version_name` 标签，值为**Agent 实际使用的 Prompt 版本**——无论来源是 Agent 自己拉的 ACTIVE，还是评测引擎 A/B 实验强制注入的指定版本。由 TargetAdapter 在 `wrapWithLangfuseTracing` 里统一写入。监控大盘和 Langfuse UI 可以按 Prompt 版本分组看质量趋势。 | 这是 Prompt 托管中心存在的核心理由——能在 Langfuse 里关联 Trace 和 Prompt 版本，知道"当前用的哪个 Prompt 版本在跑出什么质量" |

### 5.2 业务约束

| 编号 | 约束 | 理由 |
|------|------|------|
| B1 | 只有 space 的 admin 能创建/编辑/删除 EvalTarget、创建 A/B 实验、发布 Prompt 版本 | 防止误操作 |
| B2 | 已发布的 Prompt 版本不可删除，只能标记 deprecated | 审计留痕 |
| B3 | EvalTarget 被某个正在运行的 EvalRun 引用时，禁止删除；必须先取消运行 | 数据完整性 |
| B4 | 离线评测结果保留 90 天，超过自动归档到 MinIO | 控制 MySQL 存储 |
| B5 | **v1.2 重构**：在线评测没有自己的流量，全部消费 Langfuse 已有 Trace——因此 B4 不适用 | 概念变化 |
| B6 | **v1.2 新增**：EvalTarget 注册时 `langfuseTracing.enabled` 默认为 true；只有 targetType = PYTHON_SCRIPT 且宿主不支持时才自动降级为 false | 保证新 Target 自动纳入监控 |

### 5.3 外部依赖约束

| 编号 | 约束 |
|------|------|
| E1 | Langfuse 实例可达；自部署需同时启动 Postgres、ClickHouse、MinIO |
| E2 | LLM-as-Judge 评分器额外消耗 Token（1:3 ~ 1:5 倍率） |
| E3 | 代码执行评分器沙箱初期只做"语法检查 + 单元测试"，不做真实 Docker 隔离（后续迭代） |

### 5.4 多租户并发架构总览（v1.3 新增，把 C15-C22 串起来看）

```
┌──────────────────────────────────────────────────────────────────────┐
│                     全局并发闸门 — Semaphore(8)                       │
│            Langfuse 查询（监控聚合 + 策略引擎共用同一个闸门）             │
│            60s 本地缓存兜底，90%+ 查询走缓存不进闸门                     │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│                  离线评测（双层并发 + 排队）                            │
│                                                                      │
│  全局 max-concurrent = 4 ──► 所有 EvalRun 的 Executor 共用          │
│  ┌─────────────────────────────────────────────────────────┐        │
│  │ per-space 护栏：同时 RUNNING 的 EvalRun ≤ 3              │        │
│  │   → Space A 跑 3 个，Space B 第 1 个也能进来              │        │
│  │   → 不是按 space 平均分，是硬性总上限                      │        │
│  └─────────────────────────────────────────────────────────┘        │
│  ┌─────────────────────────────────────────────────────────┐        │
│  │ per-Target 护栏：同一 Target 被同时调用的 Run ≤ 2        │        │
│  │   → Run 想超过？排队等前面跑完                           │        │
│  │   → 防止多 space 共用同一个 API key 时互相干扰            │        │
│  └─────────────────────────────────────────────────────────┘        │
│  启动幂等：Redis 锁 eval:run:dedupe:{spaceId}:... → 60s 内同配只出 1 条 │
│  状态机乐观锁：version 字段，禁止并发写状态                            │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│               在线策略引擎（抖动调度 + 三层预算墙）                     │
│                                                                      │
│  每条 ACTIVE 策略的扫描 interval = 配置值 ± random(20%)               │
│  → 30 条策略不会同时在 top-of-5min 集中扫                              │
│                                                                      │
│  LLM-as-Judge 三层预算墙（任何一层耗尽 → 降级规则评分）：              │
│  ┌─────────────────────────────────────────┐                        │
│  │ ① 全局日预算（default 5M tokens/day）    │                        │
│  │ ② per-space 日预算（default 全局/N 活跃space）│                     │
│  │ ③ 每条策略分钟级 rateLimit（default 60/min）│                      │
│  └─────────────────────────────────────────┘                        │
│  预算消耗是实时累加的（Redis INCR + 过期），不是事后统计                │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│              A/B 线上流量分配（纯计算无锁）                             │
│                                                                      │
│  bucket = (userId.hashCode() + experimentId) % 100                   │
│  group  = bucket < trafficPercentage ? TREATMENT : CONTROL            │
│                                                                      │
│  → O(1) 纯计算，0 DB 查询，0 锁                                      │
│  → 同一 userId 永远落同一 bucket（确定性 hash）                        │
│  → 修改 trafficPercentage 后，bucket 边界以内的用户 group 会改变        │
│    （可以接受；要完全稳定就用 100 个 bucket 预分配，但没必要）           │
└──────────────────────────────────────────────────────────────────────┘
```

#### 并发调度算法（离线评测）伪代码

```java
public EvalRun startRun(CreateRunRequest req) {
    // ① 启动幂等（C19）
    String dedupeKey = "eval:run:dedupe:" + req.spaceId + ":" + req.datasetId 
                       + ":" + req.promptVersionId + ":" + req.targetId;
    if (!redis.trySetNx(dedupeKey, "1", Duration.ofSeconds(60))) {
        throw new BusinessException("相同配置的评测已在 60s 内启动，请稍候");
    }

    // ② per-space 护栏（C15）
    long runningInSpace = evalRunMapper.countRunningBySpace(req.spaceId);
    if (runningInSpace >= 3) {
        throw new BusinessException("当前 space 已有 3 条评测在运行，请等待完成后再启动");
    }

    // ③ per-Target 护栏（C16）
    long concurrentOnTarget = evalRunMapper.countRunningByTarget(req.targetId);
    if (concurrentOnTarget >= 2) {
        // 不直接拒绝，排队（可选；也可以直接抛异常让用户知道）
        throw new BusinessException("该 Target 当前被 2 条评测占用，请稍后重试");
    }

    // ④ 最终并发 = min(Run 自配, 全局 max-concurrent)
    int effectiveConcurrency = Math.min(
        req.getMaxConcurrency(), 
        platformEvaluationOfflineMaxConcurrent   // 默认 4
    );

    // ⑤ 创建 Run，状态 PENDING + version=1
    EvalRun run = evalRunMapper.insertAndReturnEntity(
        EvalRun.builder()
            .spaceId(req.spaceId).targetId(req.targetId)...
            .status(PENDING).version(1)...
            .build()
    );

    // ⑥ 提交到全局 Executor（线程池大小 = 全局 max-concurrent）
    //    实际同时跑多少条 Run = min(全局线程池空闲槽, 各 Run 自配的 effectiveConcurrency 之和)
    offlineExecutor.submit(() -> executeRun(run.id, effectiveConcurrency));
    return run;
}
```

---

## 六、运行方式

### 6.1 本地开发启动

```bash
# 1. Langfuse（自部署或 Cloud 二选一）
docker compose -f langfuse/docker-compose.yml up -d   # 自部署
# 或直接配 Cloud Key

# 2. 环境变量
LANGFUSE_PUBLIC_KEY=pk-lf-xxxx
LANGFUSE_SECRET_KEY=sk-lf-xxxx
LANGFUSE_HOST=http://localhost:3000     # 自部署

# 3. 外部依赖（如果计划评 LLM API / HTTP Agent）
#    在 .env.local 里加：
MY_CUSTOM_LLM_API_KEY=sk-xxxx
EXTERNAL_AGENT_TOKEN=eyJhbGci...

# 4. 后端 + 前端照常
cd playwright-platform-server && mvn spring-boot:run
cd playwright-platform-web && npm run dev
```

### 6.2 生产部署

在现有 `docker-compose.prod.yml` 基础上增加 Langfuse service。Python 宿主如需要，可通过 `platform.evaluation.python.enabled=true` 开关关闭未安装时的 Adapter（适配器启动时自检、能力不满足时自动从注册列表移除）。

### 6.3 CI 独立运行

```bash
curl -X POST http://platform/api/evaluation/runs \
  -H "Content-Type: application/json" \
  -H "Cookie: platform_session=xxx" \
  -d '{
    "targetId": 101,
    "promptVersionId": 456,
    "datasetId": 123,
    "evaluators": ["rule_match", "llm_judge"],
    "maxConcurrency": 4,
    "spaceId": 1
  }'
```

---

## 七、验收方式

### 7.1 功能验收清单

| 编号 | 验收项 | 验收标准 | 优先级 |
|------|--------|----------|--------|
| AC1 | EvalTarget CRUD | 能创建 4 种类型的 Target，健康检查能返回 reachable + latency | P0 |
| AC2 | EvalTarget 通用性 | 创建一个 `HTTP_AGENT` Target 指向 `https://httpbin.org/post` 形式的 mock，离线评测能跑通并拿到输出 | P0 |
| AC3 | EvalTarget 密钥安全 | adapterConfig 里填的是环境变量引用名 `MY_KEY`，DB 中看不到明文；把环境变量 unset 后调用失败且提示 Key 缺失 | P0 |
| AC4 | 评测集 CRUD | 能创建、编辑、导入 JSONL、按 spaceId 隔离 | P0 |
| AC5 | 外部评测集导入 | 内置 MMLU/GSM8K/HumanEval 子集可直接选；HuggingFace URL 可导入 | P0 |
| AC6 | Prompt 版本管理 | 能创建、diff、发布、回滚，支持"适用目标类型"标签 | P0 |
| AC7 | 离线评测运行 | 先选 Target → 再选评测集 → 启动，实时看进度，完成后看总分和 case 明细 | P0 |
| AC8 | 离线评测多目标对比 | 同一评测集跑 2 个 Target，报告页显示并排对比矩阵 | P0 |
| AC9 | 规则评分器 | 配置关键词/正则后自动 0/1 打分 | P0 |
| AC10 | LLM-as-Judge（离线可用） | 输出 0-100 分 + 理由 | P0 |
| AC11 | **v1.2 核心**：EvalTarget 生产调用自动 Trace 采集 | 创建 LLM_API Target → 发一次生产请求 → Langfuse UI 看到带 `target_id` 标签的 Trace（C11 约束生效） | P0 |
| AC12 | **v1.2 核心**：在线评测策略 CRUD | 能创建、启停、测试策略（一条策略 = target + filter + evaluators + alerting） | P0 |
| AC13 | **v1.2 核心**：策略引擎自动评分 | ACTIVE 策略每 5min 扫描 Langfuse 新 Trace → 跑评分器 → 回写 Score 到 Langfuse + 本地留痕 | P0 |
| AC14 | **v1.2 核心**：断点续跑 | 重启后端后已评 Trace 不重复跑（唯一索引生效，C13） | P0 |
| AC15 | **v1.2 核心**：监控大盘聚合指标 | 选一个 Target → 实时查 Langfuse → 显示 P50/P95 延迟、Token、成本、自动评分分布 | P0 |
| AC16 | 手动评分 + Langfuse 回写 | 监控大盘浏览 Trace → 手动打 1-5 星 → Langfuse Score 里出现 | P1 |
| AC17 | 告警触发 | 策略 lowScoreThreshold 调到 5 → 等一轮扫描 → 日志出现告警 | P1 |
| AC18 | HTTP_AGENT + BUILTIN_AGENT 两种 Target 都能被策略引擎消费 | 创建两种 Target → 各发一条生产请求 → 都在监控大盘可见 | P1 |
| AC19 | A/B 实验（离线） | 支持跨 Target 对比（DeepSeek vs GPT-4o），并排展示 | P1 |
| AC20 | A/B 统计结论 | 输出 p-value、置信区间、显著性结论 | P1 |
| AC21 | Langfuse 集成（离线） | 离线评测 Trace + Score 在 Langfuse UI 按 targetId 分组可见 | P0 |
| AC22 | 权限隔离 | 非本 space 成员不可看 Target/评测集/运行/策略；非 admin 不可创建 Target / 发布 Prompt / 创建策略 | P0 |
| AC23 | 失败降级 | 关闭 Langfuse → 离线评测仍能跑完；在线策略引擎自动暂停并记录错误 | P0 |
| AC24 | API 鉴权 | 所有评测接口要求登录 + spaceId 校验 | P0 |
| AC25 | 自定义 Python 评分器 | 能上传 .py 脚本作为评分器后端 | P2 |
| **v1.3 并发专项验收** | | | |
| AC26 | **C15 per-space 护栏** | 同一 space 连续启动 4 条离线评测 → 前 3 条 RUNNING，第 4 条收到"请等待"提示 | P0 |
| AC27 | **C16 per-Target 护栏** | 两个不同 space 同时启动同一个 Target 的离线评测，并发都设 2 → 第 3 个进来时被拒 | P0 |
| AC28 | **C17 策略扫描抖动** | 创建 5 条 ACTIVE 策略 → 观察下一轮扫描时间差，应分布在 interval±20% 区间内 | P0 |
| AC29 | **C18 三层预算墙** | 调小 per-space 日预算到 100 tokens → 该 space 的 LLM-as-Judge 策略跑完后自动降级为规则评分器 | P0 |
| AC30 | **C19 启动幂等** | 同一用户 60s 内对同配置点两次"启动" → 只产生一条 EvalRun | P0 |
| AC31 | **C20 状态机乐观锁** | 模拟两个线程同时 UPDATE eval_run SET status=RUNNING → 只有一个成功，另一个 version mismatch | P0 |
| AC32 | **C21 Langfuse 闸门 + 缓存** | 10 个 space 同时点监控聚合 API → 最多 8 个线程查 Langfuse，其余走缓存或排队 | P0 |
| AC33 | **C22 A/B 纯计算分配** | 压测 1000 并发请求 → 流量分配接口 RPS ≥ 5000（纯计算不走 DB） | P1 |
| **v1.4 Prompt 托管验收** | | | |
| AC34 | Prompt CRUD | 能创建 Draft 版本、发布 ACTIVE、Diff 对比、版本历史查询；同一 key 同一 space 只能有 1 个 ACTIVE（C23 唯一约束生效） | P0 |
| AC35 | Prompt 拉取接口 | `GET /api/evaluation/prompts/{promptKey}?spaceId=1` 返回 ACTIVE 版本；带 `If-None-Match` 时 304 Not Modified；跨 space 拉 403 | P0 |
| AC36 | Prompt 拉取性能 | 压测拉取接口 1000 QPS → P95 ≤ 10ms，Caffeine 缓存命中率 ≥ 99% | P0 |
| AC37 | Prompt 版本切换生效 | 发布新版本后，Agent 下一次拉取拿到新版本 → Langfuse Trace 自动带上新 promptVersionId 标签（C25） | P0 |
| AC38 | Seed 数据 | 首次启动时 `AGENT.md` 自动 seed 为 `agent-system-prompt` 的 DRAFT 版本（C24） | P0 |
| AC39 | Langfuse 按 Prompt 版本分组 | Langfuse UI 里能按 `prompt_version_name` 分组看 Trace，切换版本前后的质量趋势有明显分野 | P0 |

### 7.2 性能验收

| 指标 | 标准 |
|------|------|
| 离线评测启动延迟 | ≤ 3s（从点击到第一样本开始执行） |
| 单样本吞吐（规则评分器） | 并发 4 时 ≥ 2 samples/s |
| Langfuse 写入开销 | 单次 Trace 写入 P95 ≤ 200ms（流水线 A，不阻塞生产调用主链路） |
| Langfuse 查询开销（监控聚合） | 按 Target + 时间窗口拉 1000 条 Trace 做聚合的 P95 ≤ 1s |
| HTTP_AGENT 适配器开销 | 本地 mock 场景下 ≤ 纯 HTTP 调用时间 + 10ms |
| **v1.2 新增**：在线策略扫描效率 | 每轮扫描 5min 内最近新 Trace 的处理 P95 ≤ 30s（含 LLM-as-Judge） |
| **v1.2 新增**：生产调用 Langfuse 埋点开销 | TargetAdapter.wrapWithLangfuseTracing 包装后，生产调用 P95 延迟增加 ≤ 5%（异步上报不阻塞主链路） |

---

## 八、测试方式

### 8.1 单元测试

| 测试目标 | 覆盖范围 |
|----------|----------|
| 四种 TargetAdapter | LLM_API 参数映射、HTTP_AGENT 模板渲染、BUILTIN_AGENT 调用委托、PYTHON_SCRIPT 子进程管理 |
| 适配器通用能力 | 密钥引用解析、SSRF 拦截（C7）、敏感路径拦截（C8） |
| Evaluator 体系 | 输入解析、边界 case、超时 |
| Langfuse 客户端 | 正常上报、网络失败重试、限流降级 |
| 离线评测引擎 | 并发调度、部分样本失败、中断恢复 |
| A/B 统计 | t 检验 / Mann-Whitney U 正确性 |
| 权限隔离 | 跨 space 访问被拒、非 admin 操作被拒 |

### 8.2 集成测试

| 测试目标 | 方法 |
|----------|------|
| **通用适配器端到端**（v1.1 关键项） | 启动 H2 + Mock LLM Server（WireMock 模拟 DeepSeek-compatible API）+ 一个 mock HTTP Agent + Testcontainers Langfuse → `curl` 触发评测 → 查 DB + 查 Langfuse |
| BUILTIN_AGENT Adapter | Mock AgentCallManager，验证 BUILTIN_AGENT Adapter 确实委托给它 |
| HTTP_AGENT Adapter 模板渲染 | 构造 template 里含 `{{input}}` + `{{system_prompt}}` 的配置，验证 HTTP Body 正确渲染 |
| 离线多 Target 对比 | 同一数据集分别跑 LLM_API mock 和 HTTP_AGENT mock → 验证报告有并排对比数据 |

### 8.3 前端测试

| 测试目标 | 方法 |
|----------|------|
| Target 创建向导 | 按 4 种类型切换时验证表单动态字段 |
| 离线评测运行页 | 先选 Target → Prompt 下拉根据 Target 类型联动过滤 |
| 多目标对比视图 | 模拟 2 个 Run 的返回，验证并排渲染 |

### 8.4 手动测试 Checklist

- [ ] 创建一个 LLM_API Target 指向 DeepSeek → 离线评测 MMLU 子集 100 条 → 检查 Langfuse 有 100 条 Trace
- [ ] 创建一个 HTTP_AGENT Target 指向 mock server → 同一 MMLU 子集再跑一遍 → 报告页出现并排对比
- [ ] 创建一个 BUILTIN_AGENT Target → 跑在线评测采样 → 验证 Trace 同步到 Langfuse 且带 targetId 标签
- [ ] adapterConfig 里填环境变量引用名，unset 变量后 → 健康检查返回 Key 缺失错误，DB 中看不到明文
- [ ] 关闭 Langfuse → 离线评测仍能跑完，日志有 warning
- [ ] HTTP_AGENT endpoint 填 `http://127.0.0.1:22` → SSRF 拦截，健康检查返回安全错误
- [ ] 跨 space 访问 Target → 403
- [ ] A/B 实验跨 Target 跑 → 确认并排输出和 p-value
- [ ] **并发专项**：3 个 space 各同时启动 2 条离线评测 → 全局 max-concurrent 生效；per-space 护栏未被绕过（C15）
- [ ] **并发专项**：同 space 60s 内两次启动相同配置的评测 → 只产生 1 条 Run（C19 幂等）
- [ ] **并发专项**：创建 5 条 ACTIVE 在线策略，观察扫描时间差 → 应散布在 interval±20% 内（C17 抖动）
- [ ] **并发专项**：把 per-space Judge 预算设成 10 tokens → 该 space 的 LLM-as-Judge 策略很快自动降级（C18 预算墙）
- [ ] **Prompt 托管专项**：首次启动 → `classpath:AGENT.md` 自动 seed 为 `agent-system-prompt` DRAFT 版本 → 前端能看到
- [ ] **Prompt 托管专项**：发布 `agent-system-prompt` v2.0 为 ACTIVE → 旧 ACTIVE(v1.0) 自动 DEPRECATED，带 `supersededBy` 指向 v2.0 → 再发布 v3.0 → v2.0 自动 DEPRECATED
- [ ] **Prompt 托管专项**：`curl -H 'Authorization: Bearer xxx' http://platform/api/evaluation/prompts/agent-system-prompt?spaceId=1` → 返回 ACTIVE 版本的 template；`If-None-Match` 命中 → 304
- [ ] **Prompt 托管专项**：同一个 promptKey 在同一 space 手动尝试 INSERT 第二条 ACTIVE → 唯一约束拒绝（C23）
- [ ] **Prompt 托管专项**：发布新版本后立刻发一次生产请求 → Langfuse 新 Trace 带上新 promptVersionId → 监控大盘按 Prompt 版本分组能看到前后质量差异

---

## 九、数据模型概要

### 9.1 新增表（`db/migration/V11__evaluation_schema.sql`）

```
eval_target            -- 被评目标（name, space_id, target_type, adapter_config JSON, runtime_options JSON, langfuse_tracing JSON, tags JSON, status）
eval_prompt_version    -- Prompt 托管中心（prompt_key UNIQUE+space_id, name, description, prompt_type, template JSON(多轮 Message), variables JSON, version, change_log, status, superseded_by FK, created_by, UNIQUE(space_id, prompt_key, status='ACTIVE')）
eval_dataset           -- 评测集（name, space_id, version, description, scenario_tags, recommended_target_types JSON）
eval_dataset_sample    -- 评测集样本（dataset_id, input, golden_answer, reference_context, metadata JSON）
eval_run               -- 评测运行（target_id, prompt_version_id NULLABLE-null=Agent自拉ACTIVE, dataset_id, status, total, passed, score, started_at, finished_at, version 乐观锁）
eval_run_case_result   -- 运行样本结果（run_id, sample_id, output, evaluator_scores JSON, tokens_in, tokens_out, duration_ms, external_trace_id, actual_prompt_version_id NULLABLE——Agent实际用的版本）
eval_ab_experiment     -- A/B 实验（name, space_id, control_target_id, control_prompt_version_id NULLABLE, treatment_target_ids JSON, treatment_prompt_version_ids JSON, status, metrics_json）
eval_ab_assignment     -- 实验流量分配（exp_id, user_hash_bucket, assigned_group）
eval_ab_run_result     -- 实验对比结果（exp_id, input, control_output, treatment_outputs JSON, scores JSON, trace_ids JSON, prompt_versions_used JSON——实际用了哪个版本）
eval_custom_evaluator  -- 自定义 Python 评分器（name, space_id, script_path, supported_target_types）
eval_online_policy     -- 在线评测策略（name, space_id, target_id, status, filter JSON, evaluators JSON, alerting JSON, rate_limit JSON）
eval_online_scoring    -- 在线评分留痕（policy_id, langfuse_trace_id UNIQUE, evaluator_scores JSON, weighted_score, created_at）
```

> **v1.2 关键说明**：`eval_online_scoring(policy_id, langfuse_trace_id)` 有唯一索引——每条 Trace 对每条策略**只跑一次评分器**，即使策略重启或扫描器多次运行也不会重复消耗 Token（断点续跑机制，对应约束 C13）。

所有表带 `created_at` / `updated_at`。`eval_run.target_id`、`eval_ab_experiment.control_target_id`、`eval_online_policy.target_id` 是核心关联——**任何评测运行/实验/策略必须绑定一个 EvalTarget**。

### 9.2 Langfuse 数据流向与本地数据的关系（v1.2 新增）

```
Langfuse（真相源，生产 Trace 写这里）
   │
   │  写：流水线 A 生产调用自动注入
   │  写：离线评测 Langfuse 同步
   │  写：在线策略自动评分回写 Score
   │  写：手动评分回写 Score
   │
   │  读：策略引擎周期拉新 Trace（C12 只读 Key）
   │  读：监控聚合 API 实时查指标（C12 只读 Key）
   │
   ▼
本地 MySQL（评测平台自己的业务状态）
   ├── eval_target           存"谁"被评 + 怎么连 Langfuse
   ├── eval_online_policy    存"对谁做什么评测"的策略配置
   ├── eval_online_scoring   存已评过的 Trace 记录（断点续跑）
   ├── eval_run / eval_ab_*  离线评测和 A/B 实验的运行状态
   └── eval_dataset / eval_prompt_version  评测集和 Prompt

★ 本地 MySQL 不冗余存生产 Trace 内容——那些全在 Langfuse。
   本地只存"策略配置"和"评分留痕"，保证评测平台的业务逻辑可查询、可审计。
```

### 9.2 与现有模块的关联

| 现有模块 | 关联方式 |
|----------|----------|
| `AgentCallManager` | **仅通过 `BUILTIN_AGENT Adapter`** 被评测引擎间接调用，评测引擎本身不知道它存在 |
| `AgentTraceLogService` | 在线评测对 BUILTIN_AGENT Target 采样时复用 |
| `SystemPromptHook` | 当一个 BUILTIN_AGENT Target 绑定了 `ACTIVE` 的 Prompt 版本时，自动注入 |
| `SpaceAuthorizationService` | 所有评测接口复用做 space 成员校验 |

> **关键变化**：v1.0 里评测引擎直接依赖 `AgentCallManager`；v1.1 里评测引擎唯一依赖的是 `TargetAdapter` 接口，`BUILTIN_AGENT Adapter` 只是这个接口的一个实现——它恰好委托给 `AgentCallManager`。这就是通用性的根源。

---

## 十、分期建议

| 阶段 | 内容 | 预估 |
|------|------|------|
| **P0 · MVP** | EvalTarget CRUD + Langfuse 自动埋点（wrapWithLangfuseTracing）+ Langfuse SDK 封装 + 评测集 CRUD + 外部评测集导入 + Prompt 版本化 + 离线评测（规则 + LLM-as-Judge）+ 多 Target 对比 + 在线评测策略 CRUD + 策略引擎自动评分 + 断点续跑 + 监控大盘聚合 API + Langfuse 双向同步 | 3 周 |
| **P1 · 增强** | HTTP_AGENT Adapter + BUILTIN_AGENT Adapter + 监控大盘前端完整 UI + 手动评分 + 告警（日志）+ A/B 实验（跨 Target）+ 显著性分析 | 2 周 |
| **P2 · 完善** | PYTHON_SCRIPT Adapter + 代码执行评分器 + 线上 A/B 灰度 + 告警渠道扩展（邮件/飞书/Webhook）+ 报告 PDF 导出 + 自定义 Python 评分器 | 2 周 |
| **P3 · 高级** | EvalTarget 自动探活 + 失败样本聚类 + Prompt 自动生成与调优建议 + 基准评测日程化（每月自动跑一遍所有 Target 的 MMLU）+ 场景标签 LLM 自动分类 | 待定 |

---

## 十一、风险与预案

| 风险 | 影响 | 预案 |
|------|------|------|
| HTTP_AGENT Adapter 被配置了内网地址做 SSRF | 探测内部服务端口 | C7 技术约束 + 健康检查环节做地址解析黑名单（禁止 `127.0.0.0/8`、`10.0.0.0/8`、`172.16.0.0/12`、`192.168.0.0/16`、IPv6 环回） |
| PYTHON_SCRIPT Adapter 跑恶意脚本删宿主文件 | 安全事故 | 默认关闭（`platform.evaluation.python.enabled=false`）；开启后强制工作目录隔离 + `no-new-privileges` + CPU 1 / 内存 512M / 执行超时 120s；禁止访问 `.ssh`、`.aws`、`/etc/passwd`、`/etc/shadow` |
| **v1.2 新增**：在线策略引擎扫太多 Trace 把 LLM-as-Judge Token 打爆 | 评测成本爆炸 + Judge 超时阻塞 | 每条策略配 `rateLimit.maxEvaluationsPerMinute`（默认 60），超了直接跳过；全局还配一层总预算开关（`platform.evaluation.online.max-judge-tokens-per-day`），用完自动降级为规则评分 |
| **v1.3 新增**：多 space 共用同一个 EvalTarget（如同一个 DeepSeek API key），离线评测并发互相干扰 | API 限速导致所有 space 的评测大面积 FAILED | C16 per-Target 护栏（默认 ≤ 2 Run 同时用）+ Adapter 内部统一做速率限制（按 API key 维度），超过排队而非直接发请求 |
| **v1.3 新增**：EvalRun 长时间 RUNNING 占着线程池（比如某个 Run 的样本特别慢） | 其他 space 的 Run 被饿死 | Run 级有总超时上限（默认 2h）；超时后自动标记 FAILED；手动"取消 Run"接口；线程池用有界队列，拒绝策略为"抛 RejectedExecutionException + 提示用户稍后重试" |
| **v1.4 新增**：Prompt 版本发布后 Agent 还在跑旧版本（Agent 本地缓存没及时更新） | 线上行为与评测预期脱节 | Caffeine 缓存放在**评测平台服务端**（P95 ≤ 10ms），不是 Agent 客户端；Agent 每次生产调用都拉一次（或用 ETag 只在变更时全量拉）；这样"发布 → 生效"的延迟就是 Agent 下一次请求的间隔 |
| **v1.2 新增**：Langfuse 查询慢（自部署 ClickHouse 高延迟） | 监控大盘打不开、策略引擎扫描超时 | 生产推荐 Langfuse Cloud；自部署时 ClickHouse 限制内存；监控聚合 API 加 30s 超时兜底 + 返回上一次缓存 |
| LLM-as-Judge Token 开销爆炸 | 成本不可控 | Judge 并发上限 + 总 Token 预算开关，用完自动降级为规则评分 |
| 离线评测长时间运行被 Docker 重启中断 | 结果丢失 | EvalRun 状态机持久化，每条样本先写状态再执行 → 重启后从断点继续 |
| 外部 LLM API 限流导致评测大面积失败 | Run 失败率高 | Target 层支持速率限制配置 + 指数退避重试；同时 Run 级有"跳过失败样本继续"开关 |
| 新增被评类型（未来）需要改评测引擎核心 | 违反开闭原则 | 严格遵循 Adapter 接口；新增类型只需加一个 Spring Bean 实现 `TargetAdapter` + 一个 TargetType enum 值，评测引擎零改动 |
