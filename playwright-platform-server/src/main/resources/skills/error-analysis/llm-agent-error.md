# LLM-Agent 故障排查 SOP

## 核心思路：通过 traceId 全链路追踪

每一次 AI 对话请求都会生成唯一的 **traceId**（UUID 格式），贯穿从用户提问到最终回答的完整生命周期。  
**排查任何 LLM-Agent 故障的第一步，永远是获取 traceId。**

---

## Step 0：获取 traceId

traceId 有 4 个获取途径，按便捷程度排序：

### 方式 1：前端对话框直接复制
AI 回答消息底部有 `traceId: xxxxxxxx-...` 标签，点击即可复制完整 ID。

### 方式 2：从日志中 grep
```bash
# 已知大概时间，按时间范围过滤
grep "TRACE:" app.log | grep "14:3[0-5]" | head -20

# 已知用户名或 sessionId
grep "TRACE:" app.log | grep "sessionId=xxx" | head -5
```

### 方式 3：SSE 流响应
前端 EventSource 接收的 `meta` 事件中包含 `traceId` 字段：
```json
{"traceId":"5f847f51-f5d2-4b9e-9a3c-2b9e5b5d2a8c","usedTools":["getTask"],"confidence":"HIGH","responseType":"FAULT_ANALYSIS"}
```

### 方式 4：同步 API 响应
`POST /api/ai/chat` 返回的 `ChatResponse.traceId` 字段。

---

## Step 1：通过 traceId 检索全链路日志

**拿到 traceId 后，用它 grep 出这次请求的完整生命周期：**

```bash
# 基础用法 - 查看完整链路
grep "[TRACE:5f847f51-f5d2-4b9e-9a3c-2b9e5b5d2a8c]" app.log

# 只看错误/警告
grep "[TRACE:5f847f51-f5d2-4b9e-9a3c-2b9e5b5d2a8c]" app.log | grep -E "(ERROR|WARN)"

# 看关键节点摘要
grep "[TRACE:5f847f51-f5d2-4b9e-9a3c-2b9e5b5d2a8c]" app.log | grep -E "(Processing|Chat processed|Conversation ended|Agent call|Tool usage)"
```

### 日志关键节点说明

| 日志关键字 | 含义 | 排查价值 |
|-----------|------|---------|
| `Processing chat request` | 请求接收 | 确认请求是否正常到达 |
| `Agent call recorded` | Agent 调用发出 | 确认模型调用是否成功发起 |
| `Chat processed` | 核心处理完成 | **最重要**：parsingStrategy、responseType、responseLength |
| `Conversation ended` | 会话结束 | 最终耗时、消息数、是否有错误 |
| `Tool usage issues` | 工具使用异常 | 工具调用问题定位 |
| `Input sanitization failed` | 输入被拒 | 注入检测或超长 |
| `Session context compressed` | 上下文压缩 | token 超限警告 |

---

## Step 2：按日志定位故障阶段

### 阶段 A：请求未到达 Agent

**日志特征**：只有 `Processing chat request`，后续无日志  
**可能原因**：
- 输入被 `InputSanitizer` 拒绝（含注入特征或超长）
- 检查 `platform.ai.sanitizer.max-length`（默认 10000）

```bash
grep "[TRACE:xxx]" app.log | grep "sanitization"
```

### 阶段 B：Agent 调用失败

**日志特征**：出现 `Agent error` 或 `Agent call failed`  
**排查清单**：
1. 检查 `platform.ai.call.timeout-seconds`（默认 60s），是否超时
2. 检查 `PLATFORM_DEEPSEEK_BASE_URL` 网络可达性
3. 检查 `PLATFORM_DEEPSEEK_API_KEY` 配额是否充足
4. 检查 `ModelCallLimitHook.runLimit`（默认 20）是否触发（无限循环保护）

```bash
grep "[TRACE:xxx]" app.log | grep -i "timeout\|refused\|401\|429\|quota"
```

### 阶段 C：输出解析异常

**日志特征**：`Output parsing used fallback`  
**这是最常见的故障类型**，按 parsingStrategy 判断：

| parsingStrategy | 含义 | 排查方向 |
|----------------|------|---------|
| `bean_converter` | ✅ 成功 | 无需排查 |
| `json_extraction` | ⚠️ BeanConverter 失败 | LLM 输出含额外文本，检查 system prompt |
| `field_extraction` | ⚠️ JSON 提取也失败 | 格式偏离严重，检查 AGENT.md 模板 |
| `fallback` | ❌ 全部兜底失败 | 返回纯文本，功能降级 |

**排查动作**：
```bash
# 查看实际 LLM 输出（如果日志有记录）
grep "[TRACE:xxx]" app.log | grep -i "raw\|response\|output\|parsing"
```

- 检查 AGENT.md 中 `{{` / `}}` 转义是否正确（Spring AI 模板引擎要求）
- 检查 system prompt 是否正确加载
- OutputFormatFallbackService 有四层兜底：BeanConverter → JSON 提取 → 正则提取 → 纯文本

### 阶段 D：工具调用异常

**日志特征**：`Tool usage issues`  
**排查清单**：
1. 未知工具：LLM 调用了未注册的工具名（检查 AGENT.md 工具列表是否被遵循）
2. 过度调用：同一工具调用次数 ≥ 5，可能陷入循环
3. 工具参数错误：检查 spaceId 数据隔离

```bash
grep "[TRACE:xxx]" app.log | grep -i "tool\|guidance\|unknown"
```

### 阶段 E：上下文压缩

**日志特征**：`Session context compressed`  
**排查清单**：
- 检查 `platform.ai.context.max-tokens`（默认 8000）
- 检查 `platform.ai.context.compression-threshold`（默认 0.8）
- 压缩后上下文是否导致关键信息丢失

---

## Step 3：常见故障速查

### 场景 1：AI 回答超时无响应
```bash
# 1. grep traceId 看日志停在哪一步
grep "[TRACE:xxx]" app.log

# 2. 如果停在 Agent call 之后 → 模型调用超时
# 3. 如果停在 Processing 之后 → 检查输入
```

### 场景 2：AI 回答格式混乱（非结构化 JSON）
```bash
# 1. 找 parsingStrategy
grep "[TRACE:xxx]" app.log | grep "parsingStrategy"

# 2. 如果是 fallback → 检查 AGENT.md 是否被正确加载
# 3. 检查 {{ }} 转义
```

### 场景 3：工具返回空数据
```bash
# 1. 看工具调用日志
grep "[TRACE:xxx]" app.log | grep "tool"

# 2. 检查 spaceId 是否正确传递
# 3. 检查后端对应服务模块是否正常
```

### 场景 4：会话历史丢失
```bash
# 1. 看是否触发上下文压缩
grep "[TRACE:xxx]" app.log | grep "compressed"

# 2. 检查 max-tokens 配置
# 3. 考虑增大上下文窗口
```

### 场景 5：AI 回答"答非所问"
```bash
# 1. 看日志中 responseType 和 faultDetail
grep "[TRACE:xxx]" app.log | grep -E "responseType|faultDetail"

# 2. 检查 usedTools 是否调用了正确的工具
# 3. 如果工具正确但回答错误 → LLM 推理问题，调整 temperature
```

---

## 关键配置项速查

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `platform.ai.call.timeout-seconds` | 60 | Agent 调用超时 |
| `platform.ai.call.max-retries` | 2 | 最大重试次数 |
| `platform.ai.call.retry-delay-ms` | 1000 | 重试间隔 |
| `platform.ai.context.max-tokens` | 8000 | 上下文 token 上限 |
| `platform.ai.context.max-messages` | 50 | 历史消息上限 |
| `platform.ai.sanitizer.max-length` | 10000 | 输入最大长度 |
| `PLATFORM_DEEPSEEK_MODEL` | deepseek-chat | 模型名称 |
| `PLATFORM_DEEPSEEK_TEMPERATURE` | 0.6 | 温度参数（越低输出越稳定） |

---

## 快速恢复 Checklist

1. ✅ 通过 traceId 定位到故障阶段（A/B/C/D/E）
2. ✅ 清理当前会话（DELETE `/api/ai/session/{sessionId}`）重新开始
3. ✅ 降低 temperature 提高输出稳定性
4. ✅ 增大超时时间
5. ✅ 检查 API Key 配额是否充足
6. ✅ 简化用户输入，减少上下文长度
7. ✅ 检查 AGENT.md 修改后是否重启应用

---

## 完整链路日志示例

以下是一次成功请求的完整 traceId 日志链路供参考：

```
[TRACE:5f847f51-f5d2-4b9e-9a3c-2b9e5b5d2a8c] Processing chat request: sessionId=session-001, spaceId=1, taskId=123
[TRACE:5f847f51-f5d2-4b9e-9a3c-2b9e5b5d2a8c] Input sanitization passed: length=128, max=10000
[TRACE:5f847f51-f5d2-4b9e-9a3c-2b9e5b5d2a8c] Agent call recorded: sessionId=session-001, tools=[getTask, analyzeLogs]
[TRACE:5f847f51-f5d2-4b9e-9a3c-2b9e5b5d2a8c] Tool executed: getTask(taskId=123) → success (45ms)
[TRACE:5f847f51-f5d2-4b9e-9a3c-2b9e5b5d2a8c] Tool executed: analyzeLogs(taskId=123) → success (120ms)
[TRACE:5f847f51-f5d2-4b9e-9a3c-2b9e5b5d2a8c] Chat processed: sessionId=session-001, responseLength=856, time=3200ms, parsingStrategy=bean_converter, responseType=FAULT_ANALYSIS
[TRACE:5f847f51-f5d2-4b9e-9a3c-2b9e5b5d2a8c] Conversation ended: sessionId=session-001, totalTime=3200ms, messages=4, hadErrors=false
```