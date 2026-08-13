---
name: business-knowledge
description: 平台业务知识技能。覆盖技术架构、数据结构、业务功能、AI Agent评测四大领域。当用户请求"平台介绍""架构说明""数据模型""有哪些功能""怎么用""API""评测指标""技术栈"时，使用此技能。帮助AI Agent理解平台全貌，提供准确的业务咨询和技术支持。
---

# 业务知识技能（Business Knowledge Skill）

## 触发条件
当用户请求中包含以下关键词时，自动激活本技能：
- 介绍/说明/了解 平台、架构、功能
- 数据模型、数据结构、表结构、实体关系
- 怎么用、如何操作、使用指南
- API、接口、端点
- 评测、指标、技术栈
- Agent 原理、AI 工作流

## 知识分类与路由

根据用户的咨询方向，引导到对应的知识子文档：

### 知识路由图
```
用户咨询
    │
    ├── 想了解平台整体技术架构？
    │   → 路由到 [tech-architecture.md] 技术架构知识
    │   涵盖：系统分层、技术栈、设计模式、AI Agent组件
    │
    ├── 想了解数据模型/表结构？
    │   → 路由到 [data-model.md] 数据结构知识
    │   涵盖：实体关系、字段定义、数据隔离、缓存策略
    │
    ├── 想了解业务功能/操作流程？
    │   → 路由到 [business-functions.md] 业务功能知识
    │   涵盖：空间管理、仓库管理、场景管理、任务执行、AI助手
    │
    └── 想了解AI Agent评测/原理？
        → 路由到 [llm-agent.md] AI Agent评测知识
        涵盖：Agent架构、评测指标、兜底机制、对话流程
```

## 各知识领域概要

### 1. 技术架构知识
**文档**：[tech-architecture.md](./tech-architecture.md)

**核心内容**：
- 系统四层架构：Vue3 前端 → Spring Boot 后端 → AI Agent 层 → 基础设施
- 技术栈明细：Spring Boot 3.5 / Spring AI Alibaba 1.1.2.2 / Redis 两级缓存 / Docker 调度
- 关键设计模式：
  - Redis 分布式租约锁（防任务重复调度）
  - 两级缓存（Caffeine L1 + Redis L2）
  - Agent 调用可靠性（超时 + 重试 + 上下文压缩）
  - SSE 流式响应（虚拟线程 + 打字机效果）
- AI Agent 组件矩阵：SystemPromptHook / SkillsAgentHook / ModelCallLimitHook 等

### 2. 数据结构知识
**文档**：[data-model.md](./data-model.md)

**核心内容**：
- 实体关系链：Space → Scene → Task → TaskStageLog/CaseResult/Artifact
- 核心实体字段定义：
  - TaskEntity（30+ 字段：status、triggerType、stage、统计数等）
  - SceneEntity（repoId、branch、browser、cronExpression 等）
  - TestRepositoryEntity（gitUrl、testRoot、runCommandTemplate 等）
  - TaskStageLogEntity（stage、streamType、logUrl 等）
- 数据隔离规则：所有实体通过 spaceId 实现多租户隔离
- 缓存策略：5min TTL + 随机抖动 + 空值缓存防穿透

### 3. 业务功能知识
**文档**：[business-functions.md](./business-functions.md)

**核心内容**：
- 五大功能模块：空间管理 / 仓库管理 / 场景管理 / 任务执行 / AI 智能助手
- 任务执行流程：QUEUED → RUNNING(INSTALL→RUN→REPORT) → COMPLETED/FAILED
- 触发方式：手动触发 / 定时调度(Cron) / API 调用
- AI 助手使用指南：右下角浮动按钮 → SSE 流式对话 → 自动工具调用
- API 端点速查表：/api/ai/chat、/api/ai/chat/stream 等

### 4. AI Agent 知识
**文档**：[llm-agent.md](./llm-agent.md)

**核心内容**：
- Agent 架构：SystemPromptHook → SkillsAgentHook → ReAct 循环 → OutputFormatFallback
- 评测五大指标：格式合规性 / 故障定位准确性 / 工具调用效率 / 响应质量 / 安全合规
- 四层兜底机制：BeanConverter → JSON 提取 → 正则提取 → 纯文本
- SSE 对话流程：meta → chunk×N → complete → error
- 关键配置参数：超时60s / 重试2次 / token上限8000 / 温度0.6

## 使用建议
1. 用户咨询平台使用方法时，优先路由到 business-functions.md
2. 用户询问技术实现细节时，优先路由到 tech-architecture.md
3. 用户需要了解数据关系时，优先路由到 data-model.md
4. 用户关注 AI Agent 能力和评测时，优先路由到 llm-agent.md
5. 当需要多领域知识交叉时，可组合路由多个子文档
