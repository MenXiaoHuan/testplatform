# 业务功能知识

## 核心功能模块

### 1. 空间管理（Space）
- 创建/加入空间，实现多租户数据隔离
- 空间成员角色管理（管理员/开发者/观察者）
- 访问申请审批流程

### 2. 测试仓库管理（Repository）
- 关联 Git 仓库（HTTPS/SSH）
- 配置默认分支、测试目录、运行命令模板
- 启用/禁用仓库

### 3. 测试场景管理（Scene）
- 创建场景：关联仓库 + 选择器（CSS/XPath/Text/Regex）+ 浏览器
- 配置环境变量（envJson）：API 地址、Token、超时等
- 定时调度：Cron 表达式配置
- 手动触发执行

### 4. 任务执行（Task）
```
用户触发 → 入队(QUEUED) → Runner 领取 → 执行(RUNNING) → 完成(COMPLETED/FAILED)
                                        │
                                        ├── INSTALL 阶段：安装依赖
                                        ├── RUN 阶段：执行 Playwright
                                        └── REPORT 阶段：生成报告
```
- 触发方式：手动触发 / 定时调度 / API 调用
- 执行模式：本地 / Docker 容器
- 结果统计：通过/失败/跳过用例数
- 日志存储：MinIO 对象存储

### 5. AI 智能助手
- 入口：右下角浮动按钮 → 打开对话框
- 功能：故障排查、业务咨询、任务分析
- 流式响应：SSE 打字机效果
- 对话历史：会话持久化 + 上下文压缩
- 工具调用：自动调用 TaskTool / SceneTool / LogPreprocessingTool 等

## API 端点速查

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/ai/chat` | 同步 AI 对话 |
| POST | `/api/ai/chat/stream` | 流式 AI 对话（SSE） |
| DELETE | `/api/ai/session/{sessionId}` | 清理会话 |
| GET | `/api/ai/sessions/count` | 活跃会话数 |

## 常见操作指南
### 如何排查一个失败的任务？
1. 获取任务 ID（从场景页面或任务列表）
2. 告诉 AI 助手：「帮我分析任务 {taskId} 的失败原因」
3. AI 会自动调用 getTask + analyzeLogs
4. 根据 AI 给出的故障类型查阅对应的 SOP

### 如何配置定时任务？
1. 创建/编辑场景
2. 开启「定时调度」开关
3. 填写 Cron 表达式（如 `0 0 2 * * ?` 表示每天凌晨 2 点）
4. 保存后场景会按计划自动执行

### 如何查看执行日志？
1. 在任务详情页查看各阶段日志预览
2. 完整日志存储在 MinIO，可通过 logUrl 下载
3. AI 助手可通过 analyzeLogs 直接分析日志摘要