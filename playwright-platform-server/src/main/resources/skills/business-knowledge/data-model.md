# 数据结构知识

## 核心实体关系
```
Space (空间/租户)
  ├── Scene (测试场景) 1:N
  │     └── Task (执行任务) 1:N
  │           ├── TaskStageLog (阶段日志)
  │           ├── CaseResult (用例结果)
  │           └── Artifact (产物)
  ├── TestRepository (测试仓库)
  ├── SpaceMember (空间成员)
  └── SpaceAccessRequest (访问申请)
```

## 实体字段详解

### TaskEntity（执行任务）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 任务主键 |
| spaceId | Long | 所属空间（数据隔离） |
| sceneId | Long | 关联场景 |
| repoId | Long | 关联测试仓库 |
| status | String | QUEUED / RUNNING / COMPLETED / FAILED / CANCELLED |
| triggerType | String | MANUAL / SCHEDULED / API |
| triggerReason | String | 触发原因 |
| triggerUser | String | 触发人 |
| queuedAt | LocalDateTime | 入队时间 |
| branch | String | 使用的 Git 分支 |
| commitSha | String | 实际 commit |
| startedAt | LocalDateTime | 开始执行时间 |
| finishedAt | LocalDateTime | 完成时间 |
| durationMs | Long | 执行耗时 |
| runnerName | String | 执行 Runner |
| currentStage | String | 当前阶段 |
| resultCode | String | 结果码 |
| resultMessage | String | 结果消息 |
| passedCount | int | 通过用例数 |
| failedCount | int | 失败用例数 |
| skippedCount | int | 跳过用例数 |
| logUrl | String | 日志存储地址（MinIO） |
| resolvedBrowser | String | 实际使用浏览器 |
| resolvedEnvJson | String | 实际使用环境变量 JSON |

### SceneEntity（测试场景）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 场景主键 |
| spaceId | Long | 所属空间 |
| repoId | Long | 关联仓库 |
| name | String | 场景名称 |
| description | String | 描述 |
| branch | String | 默认分支 |
| testSelectorType | String | 选择器类型（CSS/XPath/text/regex） |
| testSelectorValue | String | 选择器值 |
| matchValue | String | 实际匹配值 |
| browser | String | 目标浏览器 |
| envJson | String | 环境变量 JSON |
| runCommand | String | 运行命令 |
| scheduleEnabled | Boolean | 是否启用定时调度 |
| cronExpression | String | Cron 表达式 |
| lastTaskStatus | String | 最近一次任务状态 |

### TestRepositoryEntity（测试仓库）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 仓库主键 |
| spaceId | Long | 所属空间 |
| name | String | 仓库名称 |
| gitUrl | String | Git 仓库地址 |
| defaultBranch | String | 默认分支 |
| testRoot | String | 测试代码根目录 |
| runCommandTemplate | String | 运行命令模板 |
| enabled | Boolean | 是否启用 |

### TaskStageLogEntity（阶段日志）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| taskId | Long | 关联任务 |
| stage | String | 阶段名（INSTALL / RUN / REPORT 等） |
| streamType | String | stdout / stderr |
| lineCount | int | 日志行数 |
| previewText | String | 日志预览文本 |
| logUrl | String | 完整日志 MinIO 地址 |

## 数据隔离规则
所有实体均包含 `spaceId` 字段，实现多租户数据隔离。AI Agent 的所有工具调用必须携带 spaceId 进行查询范围限制。

### UserEntity（用户）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 用户主键 |
| username | String | 登录用户名 |
| nickname | String | 显示昵称 |
| avatarUrl | String | 头像 URL（可为空） |

### SpaceAccessRequestEntity（空间访问申请）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| spaceId | Long | 目标空间 |
| userId | Long | 申请人 |
| status | String | PENDING / APPROVED / REJECTED |
| createdAt | LocalDateTime | 申请时间 |
| processedAt | LocalDateTime | 审批时间 |
| processedBy | Long | 审批人 |

## AI Trace 数据结构（Langfuse）

内置 Agent 的 trace 不再写入 Redis，统一通过 Langfuse ingestion API 落库：

- **Trace**：`id=traceId`、`name=agent.chat`，tags `source=builtin_agent` + `target_id=builtin_agent`，metadata 含 `sessionId` / `userId` / `spaceId`，input=用户消息、output=最终回复
- **Observation**：每个阶段一条观测（`request_received`、`session_ready`、`context_*`、`prompt_*`、`agent_call_*`、`output_*`、`request_completed`、`unexpected_error`），通过 `parentObservationId` 组成调用树
- **LLM / 工具观测**：`llm.call` / `tool.<toolName>` 记录起止时间、入参、结果与真实 token 用量
- **查询入口**：「模型评测 → 在线监控」，按 Agent（内置 Agent 选「智能助手（内置 Agent）」）或 Trace ID 查询
- **上游 `traceparent`**：请求头携带时直接复用其 trace-id，与外部 OTel 链路和 Langfuse 双向绑定
- **保留策略**：由 Langfuse 项目配置决定

## 缓存策略
- 任务详情：5min TTL + 随机抖动（±60s）
- 空值缓存：1min TTL（防穿透）
- 锁 TTL：5s（租约模式）