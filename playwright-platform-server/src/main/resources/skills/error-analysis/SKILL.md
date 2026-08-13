---
name: error-analysis
description: 平台故障排查技能。覆盖 Playwright UI 自动化异常、分布式调度冲突、Docker 容器资源、Redis/MySQL/MinIO 基础设施、LLM-Agent 评测故障五大类排查 SOP。当用户请求"排查故障""分析报错""任务失败""排查异常""看看日志""为什么失败"时，使用此技能。核心原则：先获取任务详情(getTask)→再分析日志(analyzeLogs)→最后检查配置(getSceneDetail/getRepository)，按此优先级顺序定位根因。
---

# 故障排查技能（Error Analysis Skill）

## 触发条件
当用户请求中包含以下关键词时，自动激活本技能：
- 排查/分析/诊断 故障、错误、异常、报错
- 任务失败、执行超时、自动化失败
- 日志分析、看看日志、为什么报错
- 排查 SOP、故障定位

## 排查核心原则（必须遵循的顺序）

### 通用排查三步曲
```
第1步：获取任务详情 → TaskTool.getTask(taskId, spaceId)
         ↓ 确定故障阶段和状态
第2步：分析错误日志 → LogPreprocessingTool.analyzeLogs(taskId, spaceId)
         ↓ 提取错误摘要和失败用例
第3步：检查配置信息 → SceneTool.getSceneDetail() / RepositoryTool.getRepository()
         ↓ 定位配置层面根因
```

### 工具调用优先级
1. **TaskTool.getTask** — 最先调用，获取任务状态和阶段信息
2. **LogPreprocessingTool.analyzeLogs** — 第二调用，分析日志提取错误
3. **SceneTool.getSceneDetail** — 第三调用，检查场景配置
4. **RepositoryTool.getRepository** — 第四调用，检查仓库配置
5. **TaskTool.listTasks** — 辅助调用，查看任务列表

## 故障分类与路由

根据故障现象，将用户引导到对应的 SOP 子文档：

### 故障决策树
```
用户描述故障
    │
    ├── 提到"Playwright/浏览器/选择器/断言/超时"？
    │   → 路由到 [playwright-error.md] Playwright UI自动化异常排查
    │
    ├── 提到"排队/不执行/重复执行/调度/Redis锁"？
    │   → 路由到 [scheduler-error.md] 分布式调度冲突排查
    │
    ├── 提到"Docker/容器/OOM/内存/浏览器启动"？
    │   → 路由到 [docker-error.md] 容器资源异常排查
    │
    ├── 提到"Redis/MySQL/数据库/Flyway/MinIO/缓存"？
    │   → 路由到 [cache-db-error.md] 数据库与缓存异常排查
    │
    ├── 提到"AI/Agent/对话/评测/模型/超时"？
    │   → 路由到 [llm-agent-error.md] LLM-Agent评测故障排查
    │
    └── 不确定类型？
        → 先执行通用排查三步曲，根据结果再路由
```

## 各分类 SOP 概要

### 1. Playwright UI 自动化异常
- **典型现象**：浏览器启动失败、选择器找不到、页面超时、断言失败
- **排查路径**：getTask → analyzeLogs → getSceneDetail → getRepository
- **详见**：[playwright-error.md](./playwright-error.md)

### 2. 分布式调度冲突
- **典型现象**：任务一直 QUEUED、重复执行、Runner 失联
- **排查路径**：getTask → listTasks → analyzeLogs → 检查 Redis 锁配置
- **详见**：[scheduler-error.md](./scheduler-error.md)

### 3. 容器资源异常
- **典型现象**：OOM、浏览器启动失败、容器网络超时
- **排查路径**：getTask → getSceneDetail(检查容器参数) → analyzeLogs
- **详见**：[docker-error.md](./docker-error.md)

### 4. 数据库与缓存异常
- **典型现象**：Redis 连接失败、MySQL 池耗尽、Flyway 迁移失败、MinIO 异常
- **排查路径**：健康检查 → getTask → analyzeLogs → 检查配置
- **详见**：[cache-db-error.md](./cache-db-error.md)

### 5. LLM-Agent 评测故障
- **典型现象**：对话超时、输出格式错误、工具调用失败、上下文过长
- **排查路径**：会话状态 → Agent 调用日志 → 检查 AI 配置
- **详见**：[llm-agent-error.md](./llm-agent-error.md)

## 输出要求
按 AGENT.md 定义的 JSON 格式输出，fault_type 对应故障分类：
- `UI自动化异常` → playwright-error
- `分布式调度冲突` → scheduler-error
- `容器资源异常` → docker-error
- `数据库缓存问题` → cache-db-error
- `LLM-Agent评测故障` → llm-agent-error
