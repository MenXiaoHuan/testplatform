# Playbook · 模型评测 - Prompt 托管

## 1. 业务定位

Prompt 托管把提示词从代码里抽出来，做成「按 key + 版本」的集中管理：线上 Agent 通过 Pull API 拉取当前 `ACTIVE` 版本，评测与 A/B 实验引用同一份版本，改 Prompt 不需要改代码、不需要重新发版。

适用场景：

- 线上 Prompt 灰度 / 回滚
- Prompt 版本与评测结果、Trace 对齐
- 多环境（测试 / 生产）共用一份版本源

## 2. 前置条件与权限

- 空间成员即可访问；写操作建议由评测负责人统一管理。
- 需要配置 Langfuse（用于 Trace 关联与在线监控）；未配置时托管本身可用，但看不到链路。

## 3. 功能与操作说明

| 功能 | 操作路径 | 说明 |
|------|----------|------|
| Prompt 列表 | `/spaces/{id}/evaluation/prompts` | 左侧按 key 分组，右侧展示版本 |
| 新建提示词 | 左栏「+ 新建」/「+ 新建提示词」 | 填写名称、类型、版本号、消息体 |
| 新建版本 | 「+ 新建版本」 | 在已有 key 下追加版本，默认 `DRAFT` |
| 编辑草稿 | 版本行「编辑」 | 仅 `DRAFT` 可编辑 |
| 查看内容 | 版本行「查看」 | 只读查看消息体与变量 |
| 状态切换 | 版本行「状态 ▾」 | 激活 / 废弃，见下方状态机 |
| 复制 Pull 地址 | 「复制地址」 | 给业务 Agent 使用的拉取 URL |
| 删除版本 / 删除 key | 版本行「删除」/ 左栏「×」 | 删除不可恢复 |

### 表单字段

| 字段 | 说明 |
|------|------|
| 名称 | 最多 256 字符 |
| 类型 | 业务分类，如 system / 业务场景提示词 |
| 版本号 | 如 `v1`、`v2`；同一 key 内唯一 |
| 变更说明 | 最多 1024 字符，说明本次改了什么 |
| 消息体 | `system` / `user` / `assistant` 多段组合，支持变量占位符 |
| 变量 | 可「从内容自动提取」，也可手工维护，供外部调用时填充 |

## 4. 关键规则与限制

| 规则 | 说明 |
|------|------|
| 状态机 | `DRAFT` → `ACTIVE` → `DEPRECATED`；同一 key 同一时间只允许一个 `ACTIVE` |
| 激活 | 激活新版本时旧 `ACTIVE` 自动转为 `DEPRECATED` |
| 编辑限制 | 只有 `DRAFT` 可编辑；`ACTIVE` / `DEPRECATED` 只读 |
| 拉取接口 | `GET /api/evaluation/prompts/{promptKey}`（等同 `/active/{promptKey}`），返回当前 ACTIVE 版本 |
| 缓存 | 支持 ETag，命中时返回 304；P95 目标 ≤ 10ms |
| 版本冲突 | 并发激活同一版本会返回 `PROMPT_VERSION_CONFLICT`（409） |
| Trace 关联 | 拉取时记录版本信息，Langfuse Trace 可回溯到具体 Prompt 版本 |
| 删除 | 删除 key 会移除其全部版本；线上仍在拉取的业务需要先切走 |

## 5. 典型流程：发布一版新 Prompt

1. 在已有 key 下「+ 新建版本」，填写变更说明与消息内容。
2. 保存为 `DRAFT`，用评测集跑一次离线评测或 A/B 对照。
3. 确认效果后点「状态 ▾ → 激活」，该版本变为 `ACTIVE`，旧版本自动废弃。
4. 业务 Agent 下一次 Pull 即拿到新版本（有 ETag 缓存时按缓存策略刷新）。
5. 出问题可重新激活上一版本，实现快速回滚。

## 6. 常见问题

| 现象 | 排查 |
|------|------|
| 无法编辑版本 | 版本不是 `DRAFT`；先复制为新版本再改 |
| 激活失败 409 | 并发激活冲突，刷新后重试 |
| 拉取不到内容 | key 不存在或没有 `ACTIVE` 版本 |
| 业务侧仍拿到旧内容 | 客户端缓存了 ETag，按 304 逻辑刷新或清缓存 |
| Trace 里看不到 Prompt 版本 | 业务调用未携带 traceparent / 未配置 Langfuse |

## 7. 相关接口与代码位置

- 后端：`evaluation/prompt/PromptController`、`PromptVersionService`、`PromptPullService`、`PromptCache`
- 前端：`views/evaluation/PromptListView.vue`
- 数据表：`eval_prompt_version`；迁移 `V11__evaluation_schema.sql`、`V14__fix_prompt_status_uniqueness.sql`
