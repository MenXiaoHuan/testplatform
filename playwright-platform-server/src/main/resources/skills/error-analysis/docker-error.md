# 容器资源异常排查 SOP

## 1. 适用场景

Docker 容器启动失败、Playwright 浏览器 OOM、容器网络超时、磁盘空间不足、挂载目录权限异常、镜像拉取超时。

## 2. 排查步骤

### 第 1 步：确认任务使用的执行环境

```
调用：getTask(taskId)
```

关注：

- `currentStage`：`PREPARING` / `INSTALLING` / `TESTING` 哪一步出问题。
- `resolvedBrowser`：实际使用的浏览器。
- `runnerName`：执行 Runner 标识。
- `resultCode`：`PREPARE_FAILED` / `INSTALL_FAILED` / `TEST_FAILED` / `TIMEOUT`。

### 第 2 步：分析容器日志

```
调用：analyzeLogs(taskId)
```

优先找这些信号：

| 日志信号 | 含义 |
|----------|------|
| `Killed`、`exit code 137`、`OOMKilled` | 容器内存超限被系统杀死 |
| `FATAL ERROR: ... Allocation failed` | Node.js 堆内存不足 |
| `Browser was not found` / `Executable doesn't exist` | Playwright 浏览器二进制缺失 |
| `Failed to launch ... browser` | 浏览器启动失败（依赖库、内存、权限） |
| `No space left on device` / `ENOSPC` | 磁盘或 inode 满 |
| `Permission denied` / `EACCES` | 挂载目录或文件权限不足 |
| `Cannot connect to the Docker daemon` | Docker 服务不可用或 socket 权限问题 |
| `docker: Error response from daemon` | 容器创建失败，常见于名称冲突、资源不足 |
| `image pull ... timeout` | 镜像拉取超时 |
| `ERR_NAME_NOT_RESOLVED` | 容器 DNS 解析失败 |
| `EPIPE` / `broken pipe` | 容器进程与执行端通信中断 |
| `Connection reset` / `Connection timed out` | 容器网络不通或目标服务不可达 |

### 第 3 步：核对场景配置

```
调用：getSceneDetail(sceneId)
```

- 浏览器是否选对；环境变量是否合法。
- 并发相关环境变量是否被错误放大。
- 是否注入了错误的接口地址、代理或 Token。

### 第 4 步：核对仓库命令与工作目录

```
调用：getRepository(repoId)
```

- 工作目录、测试根目录是否在仓库内。
- 安装命令是否会在容器内写入大量文件。
- 产物目录是否会生成超大文件。

## 3. Runner 资源默认值

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| 镜像 | `mcr.microsoft.com/playwright:v1.61.1-noble` | 浏览器与依赖随镜像提供 |
| 内存 | `2g` | 容器内存上限 |
| CPU | `2` | 容器 CPU 核数 |
| 网络 | `bridge` | Docker 网络模式 |
| 容器工作目录 | `/workspace/task` | 挂载后的执行目录 |
| 容器清理 | 自动删除 | 任务结束即删除 |
| 镜像拉取超时 | 1800 秒 | 首次拉取大镜像可能较慢 |
| Git clone 超时 | 120 秒 | 拉代码阶段上限 |
| 单产物 | 100MB | 单文件 |
| 产物总量 | 1GB | 单任务 |
| 测试日志 | 200MB | 单文件 |

> 2GB 内存同时跑多个浏览器 worker 容易 OOM；优先降低单任务 worker 数，而不是无限加内存。

## 4. 故障现象 → 根因映射

| 现象 | 可能根因 | 处理 |
|------|----------|------|
| `exit 137` / `OOMKilled` | 容器内存不足，多 worker 或多个浏览器同时启动 | 降低 worker/并发；评估提高内存 |
| Node 堆分配失败 | `NODE_OPTIONS` 堆上限过低或内存不足 | 调整堆参数或降低并发 |
| 浏览器找不到 | 镜像与 Playwright 版本不匹配、浏览器未安装 | 使用与项目版本匹配的官方镜像 |
| 浏览器启动失败 | 缺少系统依赖库、共享内存不足、权限限制 | 使用官方镜像；评估 `--shm-size`/`--ipc=host`（需运维确认） |
| `No space left` | workspace 或 Docker 磁盘满 | 清理历史 workspace 与无用镜像 |
| `Permission denied` | 挂载目录属主/权限不正确 | 修正 workspace 权限或运行用户 |
| Docker daemon 不可达 | Docker 未启动、socket 权限、宿主资源不足 | 启动 Docker；检查执行用户权限 |
| 镜像拉取超时 | 网络慢或镜像仓库不可达 | 预拉镜像、配置镜像加速 |
| DNS 解析失败 | 容器 DNS 配置缺失 | 检查 Docker DNS 与网络模式 |
| 网络连接超时 | 目标环境不可达、代理配置错误 | 确认目标地址、端口、代理 |
| 容器名冲突 | 上次容器未清理 | 清理残留容器后重试 |

## 5. 快速恢复

1. 清理历史 workspace 与退出码异常的残留容器。
2. 降低单任务 worker 数或场景并发，避免内存峰值叠加。
3. 预拉取 Playwright 镜像，避免每次任务卡在拉取阶段。
4. 修复挂载目录权限后重新执行。
5. 确认目标环境可达、DNS 正常后再跑。
6. 如确属资源不足，由运维评估提高内存/CPU 或扩容 Runner。

## 6. 不要做的事

- 不要把所有容器失败都归因为「内存不足」，先看退出码与 daemon 日志。
- 不要在没有宿主监控数据时断言是宿主机资源问题。
- 不要建议关闭容器资源限制来「解决」OOM。
- 不要直接删除生产 workspace 或镜像，先确认影响范围。
