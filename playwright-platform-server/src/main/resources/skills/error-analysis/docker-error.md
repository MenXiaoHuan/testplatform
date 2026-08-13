# 容器资源异常排查 SOP

## 适用场景
Docker 容器启动失败、Playwright 浏览器 OOM、容器网络超时、磁盘空间不足等基础设施相关故障。

## 排查步骤（按优先级）

### 第1步：确认任务的容器配置
```
调用：TaskTool.getTask(taskId, spaceId)
关注：resolvedBrowser / resolvedEnvJson / runnerName
```
- 确认使用的浏览器版本和 Runner 名称
- 对比场景配置中的 browser 字段

### 第2步：查看场景的容器参数
```
调用：SceneTool.getSceneDetail(sceneId, spaceId)
关注：browser / envJson（环境变量中的资源限制）
```
常见环境变量：
- `NODE_ENV`: 运行环境
- `CI`: 是否 CI 模式
- `HEADLESS`: 是否无头模式（Playwright）
- `TIMEOUT`: 测试超时时间
- `CONCURRENCY`: 并发数

### 第3步：分析容器日志
```
调用：LogPreprocessingTool.analyzeLogs(taskId, spaceId)
关注：所有 stage 的 error 信息
```
常见错误模式：
- `FATAL ERROR: CALL_AND_RETRY_LAST Allocation failed` → Node.js 堆内存不足
- `Browser was not found` → Playwright 浏览器二进制缺失
- `EPIPE: broken pipe` → 容器与 Runner 通信中断
- `EACCES: permission denied` → 文件系统权限问题
- `ENOSPC: no space left` → 磁盘空间不足

## 常见故障 → 根因映射表
| 错误关键字 | 可能根因 | 解决方案 |
|-----------|---------|---------|
| `insufficient memory` | 容器内存超限 | 增大 `platform.runner.docker.memory`（默认 2g → 4g） |
| `Browser launch failed` | 浏览器版本不兼容 | 确认镜像版本 `mcr.microsoft.com/playwright:v1.61.1-noble` |
| `Timeout 30000ms` | 容器网络慢/DNS 解析慢 | 检查容器 DNS 配置；增加超时 |
| `No space left` | 容器磁盘满 | 清理工作空间；增大磁盘配额 |
| `permission denied` | 文件系统权限 | 检查 runner workspace 目录权限 |
| `ERR_CONNECTION_RESET` | 容器间网络不通 | 检查 Docker 网络配置 `platform.runner.docker.network` |

## 关键配置项
| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `platform.runner.docker.memory` | 2g | 容器内存上限 |
| `platform.runner.docker.cpus` | 2 | 容器 CPU 核数 |
| `platform.runner.docker.network` | bridge | Docker 网络模式 |
| `platform.runner.docker.image` | playwright:v1.61.1-noble | Playwright 镜像 |
| `platform.runner.docker.remove-container` | true | 完成后是否删除容器 |

## 快速恢复方案
1. 重新触发任务（可能临时资源抖动）
2. 切换到本地 Runner（`PLATFORM_RUNNER_MODE=local`）绕过 Docker
3. 调整容器资源配置（增大内存/CPU）
4. 清理历史工作空间释放磁盘