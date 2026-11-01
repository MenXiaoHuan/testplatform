# Playbook · UI 自动化 - 代码仓库管理

## 1. 业务定位

代码仓库是 UI 自动化的执行源：平台按仓库配置把 Playwright 测试项目拉进 Runner 容器，安装依赖并执行测试，再把结果与产物回传。一个空间可以有多个仓库，一个仓库可以被多个场景复用。

适用场景：

- 接入新的 Playwright 测试项目
- 为私有仓库配置 HTTPS Token / SSH Deploy Key
- 调整安装命令、执行命令、结果与产物路径

## 2. 前置条件与权限

- 空间角色为 `OPERATOR` 或 `ADMIN` 才能新增 / 编辑 / 删除；`VIEWER` 只读。
- 私有仓库需要平台配置 `PLATFORM_CREDENTIAL_KEY`（AES-256-GCM 密钥），否则无法保存凭据。

## 3. 功能与操作说明

| 功能 | 操作 | 说明 |
|------|------|------|
| 仓库列表 | `/spaces/{id}/repos` | 展示仓库名称、地址、默认分支、启用状态等 |
| 新增仓库 | 右上角「新增仓库」 | 填写名称、Git 地址、认证方式、命令模板等 |
| 编辑仓库 | 行内「编辑」 | 修改配置；凭据留空表示保留原凭据 |
| 删除仓库 | 行内「删除」 | 被场景引用时需先解除引用，避免场景不可用 |
| 启用 / 停用 | 编辑弹窗中的状态项 | 停用后场景无法选择该仓库 |

### 关键字段说明

| 字段 | 用途 | 注意事项 |
|------|------|----------|
| 仓库名称 | 空间内唯一标识 | 重名会返回冲突提示 |
| Git 地址 | 测试项目地址 | `PUBLIC` / `HTTPS_TOKEN` 使用 `https://`；`SSH_KEY` 使用 `ssh://` 或主机别名格式 |
| 默认分支 | 场景未指定分支时使用 | 建议填写主干分支，如 `main` |
| 工作目录 | 仓库内执行相对目录 | 必须位于仓库根目录内 |
| 安装命令 | 依赖安装，如 `npm ci` | 超时默认 600 秒 |
| 执行命令模板 | 实际执行命令，如 `npx playwright test` | 支持场景级覆盖 |
| 测试根目录 | 测试用例目录 | 用于结果定位 |
| 结果索引路径 | Playwright JSON 报告路径 | 默认 `test-results/.playwright-results.json` |
| 产物目录 | 截图 / 视频 / Trace 目录 | 默认 `.playwright-artifacts` |
| 认证方式 | `PUBLIC` / `HTTPS_TOKEN` / `SSH_KEY` | 与凭据配套使用 |
| 凭据 | 用户名 + Token / 私钥 | 仅提交时加密落库，读取接口永不返回明文 |

## 4. 关键规则与限制

| 规则 | 说明 |
|------|------|
| 名称唯一 | 同一空间内仓库名称唯一，冲突返回「仓库名称已存在」 |
| URL 校验 | 统一由 `GitUrlPolicy` 校验，不接受不安全的协议 |
| 凭据加密 | 凭据使用 AES-256-GCM 加密存储；编辑时留空不覆盖旧值 |
| 私有仓库 | 未配置匹配凭据时，任务在拉取阶段失败并给出明确原因 |
| 停用仓库 | 停用后不能创建新场景引用，但历史任务记录仍可查看 |
| 删除 | 会清理仓库记录；引用中的场景需要先迁移 |

## 5. 典型流程：接入一个私有 Playwright 仓库

1. 确认服务器已配置 `PLATFORM_CREDENTIAL_KEY`。
2. 在「仓库管理」新增仓库，填写名称与 `https://` Git 地址。
3. 认证方式选择 `HTTPS_TOKEN`，填写用户名与 Token。
4. 安装命令填 `npm ci`，执行命令填 `npx playwright test`。
5. 保存后到「场景管理」创建场景，选择该仓库与分支。
6. 手动触发一次任务，确认拉取、安装、执行三个阶段均成功。

## 6. 常见问题

| 现象 | 排查 |
|------|------|
| 保存时报「凭据加密未配置」 | 服务器缺少 `PLATFORM_CREDENTIAL_KEY` |
| 任务在 PREPARING 阶段失败 | Git 地址不可达、分支不存在、Token 无权限 |
| 任务在 INSTALLING 阶段超时 | 依赖源网络慢或安装命令错误（默认 600 秒超时） |
| 找不到测试结果 | 确认结果索引路径与实际 reporter 输出一致 |
| 截图 / 视频缺失 | 确认 Playwright 配置了产物目录，且与「产物目录」字段一致 |

## 7. 相关接口与代码位置

- 后端：`repository/controller/RepositoryController`、`repository/service/RepositoryServiceImpl`、`common/CredentialCipher`、`repository/GitUrlPolicy`
- 前端：`views/repository/RepositoryListView.vue`、`api/repository.ts`
- 数据表：`test_repository`
