---
name: error-analysis
description: 易测平台错误排查技能，覆盖 UI 自动化用例失败、分布式调度冲突、容器资源异常、数据库与缓存异常、平台配置与权限异常、LLM-Agent 与模型评测故障六类 SOP。当用户说任务失败、用例失败、报错、异常、超时、一直排队、评测失败、AI 回答异常时使用。核心原则：先 getTask 再 analyzeLogs 后查配置，证据优先，不臆测根因。
---

# 错误排查技能（Error Analysis）

## 适用范围

- UI 自动化：任务失败、阶段卡住、用例失败、浏览器异常、产物缺失
- 平台基建：任务长期 `QUEUED`、定时不触发、重复执行、容器 OOM、数据库/缓存/对象存储异常
- 模型评测：Target 调用失败、评分异常、离线运行卡住、Langfuse/在线策略异常
- AI 助手：知知回答超时、格式异常、工具调用异常、会话上下文异常

**不适用**：纯业务规则咨询（用 `business-knowledge`）；代码缺陷修复与数据库手工改数（只给建议，不执行）。

## 核心原则

1. **证据优先**：没有工具返回的事实，不下根因结论；只能写「可能原因」并标 `confidence=LOW/MEDIUM`。
2. **先分阶段**：`PREPARING` / `INSTALLING` / `TESTING` / `ARCHIVING` 决定根因域，避免一上来就猜测试代码。
3. **区分任务级与用例级**：
   - 任务 `FAILED` 且 `failedCount=0` → 优先看环境、安装、归档。
   - 任务 `SUCCESS` 但有用例 `FAILED` → 进入用例级根因推断。
4. **区分事实与推测**：日志明确写出根因给 `HIGH`；由多个现象交叉推断给 `MEDIUM`；信息缺失给 `LOW`。
5. **方案要可执行**：临时恢复 + 长期优化 + 测试风险，三者齐全。
6. **只读边界**：不修改配置、不取消/重跑任务、不查其他空间。

## 取证顺序

```
第 1 步  getTask(taskId)              → 状态、阶段、统计、结果码、结果消息
第 2 步  analyzeLogs(taskId)          → 错误行、失败用例、阶段日志摘要
第 3 步  getSceneDetail(sceneId)      → 分支、选择器、浏览器、Cron、环境变量
第 4 步  getRepository(repoId)        → Git 地址、安装/执行命令、测试根目录、启用状态
辅助    listTasks(sceneId)            → 同场景历史任务，判断是否偶发/批量
辅助    loadSkillDocument(...)        → 加载对应分类的详细 SOP
```

**不要跳过第 1、2 步直接猜配置。** 如果用户没有给 taskId，先追问任务 ID；如果是评测问题，追问运行记录或 traceId。

## 故障分类与路由

| fault_type | 典型现象 | 加载 SOP |
|------------|----------|----------|
| `UI自动化异常` | 用例失败、选择器/断言/超时、浏览器启动失败、产物缺失 | `playwright-error.md` |
| `分布式调度冲突` | 长期 `QUEUED`、定时不触发、重复执行、取消无效、队列满 | `scheduler-error.md` |
| `容器资源异常` | OOM、容器启动失败、磁盘满、网络/权限问题 | `docker-error.md` |
| `数据库缓存问题` | MySQL/Redis/MinIO/Flyway 连接、超时、迁移、缓存不一致 | `cache-db-error.md` |
| `平台配置与权限异常` | 空间/角色/审批、仓库凭据、场景保存校验、Prompt/Target/Dataset 配置 | `config-permission-error.md` |
| `LLM-Agent评测故障` | 知知对话异常、Target 调用失败、评分异常、Langfuse/在线策略故障 | `llm-agent-error.md` |

### 决策树

```
用户描述故障
    │
    ├── 有任务 ID / 明确是 UI 回归？ → 先 getTask + analyzeLogs
    │       ├── PREPARING 失败        → 先查仓库与凭据（UI自动化异常 / 配置权限异常）
    │       ├── INSTALLING 失败       → 依赖、网络、命令（UI自动化异常）
    │       ├── TESTING 阶段用例失败  → 用例级根因推断（playwright-error）
    │       ├── ARCHIVING 失败        → 产物/结果路径、存储（UI自动化异常）
    │       └── 阶段未开始、长期排队  → 调度/容器（scheduler-error / docker-error）
    │
    ├── 提到定时、重复、取消、队列  → scheduler-error
    ├── 提到 Docker、OOM、磁盘      → docker-error
    ├── 提到 MySQL、Redis、MinIO、迁移 → cache-db-error
    ├── 提到 403、无权限、保存报错、凭据、状态机 → config-permission-error
    └── 提到 AI、知知、评测、评分、Langfuse、traceId → llm-agent-error
```

## 输出要求

按 `AGENT.md` 的 JSON 规范输出，故障分析时必须提供 `faultDetail`：

| 字段 | 要求 |
|------|------|
| `fault_type` | 必须是上表六类之一 |
| `root_cause` | 一句话根因，≤150 字；推测需注明「可能」 |
| `immediate_solution` | 分号分隔的可执行止血动作 |
| `long_term_optimize` | 分号分隔的防复发措施 |
| `test_risk` | 对回归、覆盖率、发布的影响 |
| `reproduce_steps` | 可复现步骤；无法复现填「无」 |

`responseType` 固定为 `FAULT_ANALYSIS`；`sections` 至少包含「现象 → 证据 → 结论 → 建议」四段。

## 排查质量检查清单

- [ ] 是否真的调用了 `getTask` 和 `analyzeLogs`？
- [ ] 是否写清了失败阶段与结果码？
- [ ] 是否引用了日志中的具体错误关键字，而不是泛泛而谈？
- [ ] 是否区分了任务级失败与用例级失败？
- [ ] 根因是否可能被反证？有没有遗漏更早阶段的错误？
- [ ] 置信度是否与证据量匹配？
- [ ] 是否给出了临时恢复与长期优化？
- [ ] 是否泄露了密钥、Token 或完整 Git 地址？
