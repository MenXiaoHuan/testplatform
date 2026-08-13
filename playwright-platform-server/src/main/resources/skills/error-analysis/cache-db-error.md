# 数据库与缓存异常排查 SOP

## 适用场景
Redis 连接失败/缓存不一致、Flyway 迁移失败、MySQL 连接池耗尽、MinIO 对象存储异常等。

## 排查步骤（按优先级）

### 第1步：确认服务状态
检查后端服务健康检查接口是否可达。
```
GET /actuator/health
```
关注组件状态：db / redis / diskSpace

### 第2步：分析任务相关日志
```
调用：TaskTool.getTask(taskId, spaceId)
关注：resultMessage 中的数据库/缓存错误信息
调用：LogPreprocessingTool.analyzeLogs(taskId, spaceId)
关注：stage 日志中的 SQL 异常、Redis 连接异常
```

### 第3步：常见故障模式
#### Redis 相关
- `RedisConnectionFailureException` → Redis 服务不可达，检查 `platform.redis.host/port`
- `RedisTimeoutException` → Redis 响应超时，可能网络阻塞或 Redis 慢查询
- `Lock acquisition failed` → 分布式锁竞争激烈，增大 `mutex-ttl` 或减少并发
- `Cache null result` → 缓存穿透，检查 `null-ttl` 配置

#### MySQL 相关
- `Communications link failure` → MySQL 连接中断，检查 `platform.db.url/host/username/password`
- `Too many connections` → 连接池耗尽，增大连接池大小
- `Lock wait timeout exceeded` → 行锁竞争，优化 SQL 或缩短事务
- `Duplicate entry for key` → 唯一键冲突，检查数据一致性

#### Flyway 相关
- `Migration checksum mismatch` → 迁移脚本被修改，校验失败
- `Migration failed` → 迁移 SQL 执行失败，查看具体 SQL 错误
- `Schema history table update failed` → `flyway_schema_history` 表损坏

#### MinIO 相关
- `MinioException: Access Denied` → 凭据错误，检查 `platform.minio.access-key/secret-key`
- `Bucket not found` → Bucket `qa-report` 不存在，需创建
- `Connection refused` → MinIO 服务不可达，检查 `platform.minio.endpoint`

## 关键配置项
| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `platform.redis.host` | redis | Redis 主机 |
| `platform.redis.port` | 6379 | Redis 端口 |
| `platform.redis.timeout` | 2s | Redis 超时 |
| `platform.cache.mutex-ttl` | 5s | 分布式锁 TTL |
| `platform.cache.lock-retry-times` | 3 | 锁重试次数 |
| `platform.minio.endpoint` | - | MinIO 服务地址 |
| `platform.storage.bucket` | qa-report | 存储桶名称 |

## 快速恢复方案
1. Redis 异常：重启 Redis 服务；等待连接自动恢复
2. MySQL 异常：检查连接池配置；Kill 慢查询
3. Flyway 异常：修复迁移脚本后重新启动（禁止手动修改已执行的迁移）
4. MinIO 异常：检查网络连通性；确认 Bucket 存在