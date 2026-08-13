# AI Agent 评测知识

## Agent 架构
```
用户消息 → SystemPromptHook(AGENT.md) → SkillsAgentHook(加载技能)
    → ReAct 循环（思考→调用工具→观察→再思考）
        → 最多 20 次模型调用（ModelCallLimitHook）
    → OutputFormatFallbackService（四层输出解析兜底）
    → ChatAssistantResult（response + usedTools + confidence）
    → AgentTraceLogService（写入 Redis 全链路日志）
```

## 核心组件
| 组件 | 职责 |
|------|------|
| ReactAgent | ReAct 推理循环，思考→行动→观察 |
| SystemPromptHook | 注入 AGENT.md 系统提示词 |
| SkillsAgentHook | 加载技能文档（error-analysis、business-knowledge） |
| ModelCallLimitHook | 限制最大模型调用 20 次，防死循环 |
| ChatSessionManager | 会话管理，支持历史对话 |
| ContextCompressionService | 上下文压缩，控制 token 总量 |
| OutputFormatFallbackService | 输出解析四层兜底 |
| InputSanitizer | 输入清洗 + Prompt 注入检测 |
| AgentObservability | 调用量/错误率/token 监控 |
| AgentCallManager | 超时 + 重试 + 线程池 |

## 评测指标
### 1. 输出格式合规性
- JSON 可解析率
- ChatAssistantResult 三字段完整性
- 内层故障 JSON 字段规范性

### 2. 故障定位准确性
- fault_type 分类正确率
- root_cause 精准度（是否定位到根因而非表象）
- 解决方案可行性

### 3. 工具调用效率
- 工具选择匹配度（是否选对工具）
- 工具调用次数（越少越好）
- usedTools 标注准确性

### 4. 响应质量
- confidence 评级准确度（与实际信息完整度匹配）
- 中文表达流畅度
- 无幻觉率（不编造信息）

### 5. 安全合规
- Prompt 注入防护（InputSanitizer 拦截率）
- 数据隔离（spaceId 传递覆盖率）
- 越权访问防护

## 兜底机制
| 层级 | 机制 | 说明 |
|------|------|------|
| L1 | BeanConverter | 优先使用 Jackson 反序列化为 ChatAssistantResult |
| L2 | JSON 提取 | 正则提取 `{...}` JSON 块再解析 |
| L3 | 字段正则提取 | 逐字段正则匹配 response/usedTools/confidence |
| L4 | 纯文本兜底 | 直接将原始文本作为 response 返回，confidence=LOW |

## 对话流程
```
前端发送 → POST /api/ai/chat/stream (SSE)
  → 后端创建 SseEmitter (300s 超时)
  → 虚拟线程异步处理
  → AgentService.chatStream()
      → InputSanitizer.sanitize()
      → ChatSessionManager.getOrCreateSession()
      → ContextCompressionService.compressIfNeeded()
      → AgentCallManager.executeWithRetry()
          → ReactAgent.call(prompt).getText()  // 阻塞式调用
      → OutputFormatFallbackService.parseAgentOutput()
      → 模拟流式分片（每 15ms 发送一个 chunk）
  → SSE 事件序列:
      meta → {usedTools, confidence}
      chunk × N → 文本分片
      complete → {processingTime, sessionId}
      error → {error}（异常时）
```

## 关键配置
| 配置 | 默认值 | 说明 |
|------|--------|------|
| timeout-seconds | 60 | Agent 调用超时 |
| max-retries | 2 | 最大重试次数 |
| context.max-tokens | 8000 | 上下文 token 上限 |
| context.compression-threshold | 0.8 | 压缩触发阈值 |
| sanitizer.max-length | 10000 | 输入最大长度 |
| system-prompt-path | classpath:AGENT.md | 系统提示词路径 |