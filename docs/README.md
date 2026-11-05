# 平台文档中心

本目录是「易测」的业务与工程文档入口。按「先读导航，再读模块 playbook，最后看代码规范」的顺序阅读。

## 文档地图

| 文档 | 面向 | 内容 |
|------|------|------|
| [playbooks/01-account-and-space.md](playbooks/01-account-and-space.md) | 所有用户 | 登录注册、昵称、会话、空间广场、空间成员与审批 |
| [playbooks/02-repository.md](playbooks/02-repository.md) | 测试工程师 / 空间管理员 | 测试仓库接入、私有凭据、分支与执行命令 |
| [playbooks/03-scene.md](playbooks/03-scene.md) | 测试工程师 | 场景配置、可视化定时、调度事件与重试 |
| [playbooks/04-task.md](playbooks/04-task.md) | 测试工程师 | 任务触发、执行阶段、日志、产物、取消与重跑 |
| [playbooks/12-patrol.md](playbooks/12-patrol.md) | 测试工程师 / 空间管理员 | 巡页配置、补充入口、四类模型、LLM 规划与判定、审批、预算降级、证据与保留 |
| [playbooks/05-ai-assistant.md](playbooks/05-ai-assistant.md) | 所有用户 | AI 助手入口、上下文、工具、额度与故障排查 |
| [playbooks/06-prompt.md](playbooks/06-prompt.md) | 评测负责人 | Prompt 版本托管、ACTIVE 切换、Pull API 接入 |
| [playbooks/07-target.md](playbooks/07-target.md) | 评测负责人 | 被评目标（Agent）类型、连接配置、健康检查 |
| [playbooks/08-dataset.md](playbooks/08-dataset.md) | 评测负责人 | 评测集与用例、JSONL 导入、内置评测集 |
| [playbooks/09-offline-evaluation.md](playbooks/09-offline-evaluation.md) | 评测负责人 | 离线批跑、并发、评分器、结果解读 |
| [playbooks/10-online-monitoring.md](playbooks/10-online-monitoring.md) | 评测负责人 / 值班 | Trace 浏览器、在线策略、评分与监控指标 |
| [playbooks/11-platform-admin.md](playbooks/11-platform-admin.md) | 超级管理员 | 用户管理、注册开关、删除保护、审计 |
| [code-style.md](code-style.md) | 开发者 / AI | 前后端代码编写规范与提交、验收要求 |

## 部署与运维

部署模式、环境变量、监控、备份、安全加固统一维护在仓库根目录的 [DEPLOYMENT.md](../DEPLOYMENT.md)；
项目概览与快速启动见 [README.md](../README.md)。

## 文档维护约定

1. **一个模块一份 playbook**：模块新增或交互改版时，同步更新对应 playbook 的「功能说明」和「规则与限制」。
2. **以代码为准**：文档中的路径、字段、限制值必须能在代码中找到对应实现；修改后请顺手核对相关接口。
3. **不写密钥**：所有示例统一使用 `<placeholder>`，禁止在文档中出现真实密码、Token、API Key。
4. **术语统一**：空间（Space）、场景（Scene）、任务（Task）、巡页（Patrol）、被评目标（Target/Agent）、评测集（Dataset）、离线运行（Offline Run）、在线策略（Online Policy）。
5. **中文优先**：面向用户的中文文档与界面文案保持一致，避免中英文两套叫法。
