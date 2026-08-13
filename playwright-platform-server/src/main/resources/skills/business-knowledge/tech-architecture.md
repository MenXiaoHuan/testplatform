# 技术架构知识

## 系统分层架构
```
┌─────────────────────────────────────────────┐
│              Vue3 + Element Plus            │  前端层
│         Pinia 状态管理 + marked Markdown     │
├─────────────────────────────────────────────┤
│              Spring Boot 3.5 (Java 21)       │  后端层
│  ┌─────────┬──────────┬──────────┬────────┐ │
│  │ Controller│  Service  │   Tools   │  Config │ │
│  └─────────┴──────────┴──────────┴────────┘ │
│  ┌─────────┬──────────┬──────────┬────────┐ │
│  │AgentSvc  │ SessionMgr│  CallMgr │  Sani.  │ │  AI Agent 层
│  └─────────┴──────────┴──────────┴────────┘ │
├─────────────────────────────────────────────┤
│  MySQL │ Redis │ MinIO │ Docker │ Flyway    │  基础设施
└─────────────────────────────────────────────┘
```

## 核心技术栈
| 层次 | 技术 | 用途 |
|------|------|------|
| 前端 | Vue 3 + TypeScript + Element Plus | UI 框架 |
| 前端状态 | Pinia | 全局状态管理（AI 对话会话等） |
| 前端渲染 | marked + DOMPurify | Markdown 渲染 |
| 后端框架 | Spring Boot 3.5.0 | Web 框架 |
| AI 框架 | Spring AI Alibaba Agent 1.1.2.2 | ReActAgent + Skills |
| 模型 | deepseek-chat | LLM 推理 |
| 数据库 | MySQL 8 + MyBatis | 持久化存储 |
| 缓存 | Redis + Caffeine (两级缓存) | 数据缓存、分布式锁 |
| 对象存储 | MinIO | 日志/报告存储 |
| 数据库迁移 | Flyway | 版本化 Schema 管理 |
| 容器化 | Docker（Playwright 镜像） | 测试执行环境 |
| 调度 | 自定义任务队列 + Redis 租约锁 | 分布式任务调度 |
| 实时通信 | SSE (SseEmitter) | AI 对话流式响应 |

## 关键设计模式
### Redis 分布式租约锁
- 用途：防止任务被重复调度
- 实现：`SETNX` + TTL（默认 5s）+ 续约机制
- 回退：锁获取失败重试 3 次，间隔 50ms

### 两级缓存
- L1：Caffeine 本地缓存（5min TTL，JVM 内存）
- L2：Redis 分布式缓存（5min TTL，跨实例共享）
- 策略：Cache-Aside + 空值缓存（1min TTL）+ 随机抖动防雪崩

### Agent 调用可靠性
- 超时：CompletableFuture.get(timeout)，默认 60s
- 重试：指数退避，默认 2 次，间隔 1s → 2s
- 上下文压缩：token 数超 80% 阈值时自动压缩历史对话

### SSE 流式响应
- 前端：原生 fetch API + ReadableStream 解析 SSE 协议
- 后端：SseEmitter（300s 超时）+ 虚拟线程
- 取消：AbortController 中断 + emitter.completeWithError

## AI Agent 组件
| 组件 | 职责 |
|------|------|
| ReactAgent | ReAct 循环：思考→调用工具→观察→输出 |
| SystemPromptHook | 注入 AGENT.md 系统提示词 |
| SkillsAgentHook | 加载 error-analysis、business-knowledge 技能 |
| ModelCallLimitHook | 限制最大模型调用次数（20次）防死循环 |
| ChatSessionManager | 会话管理 + Caffeine 缓存 |
| ContextCompressionService | 上下文压缩，控制 token 总量 |
| OutputFormatFallbackService | 四层输出解析兜底 |
| InputSanitizer | 输入清洗 + Prompt 注入检测 |
| AgentObservability | 调用量/错误率/token 使用量监控 |