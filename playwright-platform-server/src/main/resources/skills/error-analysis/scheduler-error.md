# 分布式调度冲突排查 SOP

## 适用场景
任务长期排队不执行、同一任务被重复调度、Redis 租约锁抢占失败、Runner 节点异常失联等。

## 排查步骤（按优先级）

### 第1步：确认任务状态和队列位置
```
调用：TaskTool.getTask(taskId, spaceId)
关注：status / queuedAt / startedAt / runnerName / currentStage
```
- status=QUEUED + startedAt=null：任务在队列中未被消费
- status=RUNNING + runnerName=null：已分配但 Runner 未确认
- status=RUNNING + 长时间无更新：Runner 可能失联，Redis 租约到期未释放

### 第2步：检查是否存在重复任务
```
调用：TaskTool.listTasks(sceneId, spaceId)
关注：同一 sceneId 下是否有多个 QUEUED/RUNNING 状态任务
```
- 同一场景同时有多个任务排队：可能是并发调度冲突
- 检查 triggerType：是 MANUAL / SCHEDULED / API 触发？

### 第3步：分析任务日志
```
调用：LogPreprocessingTool.analyzeLogs(taskId, spaceId)
关注：stage 执行日志中是否有锁冲突/超时日志
```
常见错误模式：
- `Redis lock acquisition failed` → 租约锁被其他 Runner 持有
- `Lease expired but task not completed` → Runner 崩溃，锁未正常释放
- `Duplicate task detection` → 幂等校验触发

## 常见故障 → 根因映射表
| 现象 | 可能根因 | 排查动作 |
|------|---------|---------|
| 任务一直 QUEUED | Runner 池满/无可用 Runner | 检查 Runner 健康状态；等待空闲 Runner |
| 任务被重复执行 | 调度器重试 + 租约未及时更新 | 检查 Redis 锁TTL；确保任务幂等 |
| 任务 RUNNING 但无进度 | Runner 崩溃，状态未回写 | 标记任务失败后重新触发 |
| 同一任务日志出现两次 | API 重试 + 服务端未幂等 | 检查 triggerType 和幂等键 |

## 关键配置项
- `platform.runner.docker.memory`：容器内存，过小会导致 Runner 频繁 OOM
- `platform.cache.mutex-ttl`：分布式锁 TTL（秒），默认 5s
- `platform.cache.lock-retry-times`：锁获取重试次数，默认 3
- `platform.cache.lock-wait-millis`：锁获取等待间隔，默认 50ms

## 快速恢复方案
1. 手动取消卡死任务（cancelRequested=true）后重新触发
2. 等待 Redis 租约自然过期（默认 TTL 5s）
3. 重启服务清理残留状态（谨慎使用）
4. 调整并发配置降低调度冲突概率