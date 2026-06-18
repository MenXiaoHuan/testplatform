# 第七阶段设计：事务边界、Redis 详情缓存与线程池治理

## 一、背景

后端已完成注解式 MyBatis 全量迁移，数据访问层不再依赖 JPA。当前还存在三类治理缺口：

- 写操作事务边界不够统一，部分短数据库写操作没有显式 `@Transactional`。
- 读接口没有缓存，详情类接口每次都直接访问数据库。
- 任务执行线程池已有自定义实现，但文档、拒绝补偿和 Web 请求线程配置说明还不完整。

本阶段目标是在不改变 API 和前端调用方式的前提下，补齐事务、Redis 缓存防护和线程池治理能力。

## 二、目标

- 给短写操作补齐事务边界。
- 长任务执行不使用大事务，保持每个状态落库独立提交。
- 优先缓存详情类接口：仓库详情、场景详情、任务详情。
- 使用 Redis 实现缓存，并具备防穿透、防击穿、防雪崩策略。
- 写操作后主动失效关联详情缓存。
- 明确自定义任务线程池配置、拒绝策略告警和补偿策略。
- 补充 Web 请求线程池配置说明。
- 保持 `mvn test`、`npm test`、`npm run build`、`docker compose config` 通过。

## 三、不包含

- 不缓存分页列表接口。
- 不引入分布式任务队列。
- 不改变 Runner 执行模式。
- 不重写前端页面。
- 不引入复杂多级缓存。

## 四、事务设计

### 4.1 短写操作

以下操作增加或保持 `@Transactional`：

- 仓库 `create/update/delete`
- 场景 `create/update/delete`
- 任务取消
- 任务创建
- 仓库级联删除
- 场景级联删除
- 调度 lease 获取
- 调度 nextRunAt 更新
- 启动恢复

### 4.2 长任务执行

任务执行涉及外部命令、文件系统、MinIO 和数据库，不使用一个大事务包住全流程。保留当前多次状态落库模式，并将关键小写入封装为明确事务方法：

- 标记任务运行中
- 更新当前阶段
- 最终完成任务
- 标记队列拒绝失败
- 更新场景摘要
- 持久化用例、产物、阶段日志

这样可以避免长事务占用连接和锁，同时让每个状态更新独立提交，服务异常时也能保留中间状态用于恢复。

## 五、Redis 缓存设计

### 5.1 缓存范围

缓存详情类接口：

- `GET /api/repos/{id}`
- `GET /api/scenes/{id}`
- `GET /api/tasks/{taskId}`

暂不缓存列表接口，避免分页、排序和频繁变更导致失效复杂度过高。

### 5.2 缓存 key

- `platform:repo:detail:{id}`
- `platform:scene:detail:{id}`
- `platform:task:detail:{id}`
- 空值缓存使用相同 key，value 中标记 `empty=true`。
- 互斥锁 key 为 `platform:lock:{detailKey}`。

### 5.3 TTL 策略

- 正常详情缓存：基础 TTL 300 秒，随机抖动 0 到 60 秒。
- 空值缓存：基础 TTL 30 秒，随机抖动 0 到 10 秒。
- 互斥锁 TTL：5 秒，避免服务异常导致锁长期不释放。

TTL 支持配置：

```yaml
platform:
  cache:
    detail:
      ttl-seconds: 300
      ttl-jitter-seconds: 60
      null-ttl-seconds: 30
      null-ttl-jitter-seconds: 10
      lock-ttl-seconds: 5
      lock-wait-millis: 50
      lock-retry-times: 3
```

### 5.4 防护策略

- 防穿透：id 为空或小于等于 0 直接拒绝；数据库查不到时写入短 TTL 空值缓存。
- 防击穿：缓存未命中时尝试 Redis `SET NX PX` 获取单 key 互斥锁；拿不到锁的请求短暂等待并重读缓存。
- 防雪崩：正常缓存和空值缓存都使用随机 TTL 抖动。
- 失效策略：写操作后删除关联详情缓存。仓库更新/删除删仓库缓存；场景更新/删除删场景缓存；任务状态更新、取消、完成删任务缓存；场景摘要更新删场景缓存。

## 六、线程池治理设计

### 6.1 任务执行线程池

继续使用项目自定义 `taskExecutionExecutor`，不使用 Spring Web 默认线程处理长任务。参数由 `platform.task.execution` 管理：

- `core-pool-size`
- `max-pool-size`
- `queue-capacity`
- `keep-alive-seconds`
- `monitor-log-interval-seconds`

拒绝策略保持 fail-fast：

- 记录 active、pool、max、queue 指标。
- 抛出 `RejectedExecutionException`。
- 业务侧将任务标记为 `FAILED/SYSTEM_BUSY`。
- 场景摘要同步更新。

### 6.2 Web 请求线程池

HTTP 请求线程由内嵌 Tomcat 管理。本阶段补充可配置项和文档：

```yaml
server:
  tomcat:
    threads:
      max: ${SERVER_TOMCAT_THREADS_MAX:200}
      min-spare: ${SERVER_TOMCAT_THREADS_MIN_SPARE:10}
    accept-count: ${SERVER_TOMCAT_ACCEPT_COUNT:100}
```

### 6.3 任务补偿

已有启动恢复会扫描 `QUEUED/RUNNING` 任务。本阶段保留并明确：

- `RUNNING` 在重启后标记失败或取消。
- `QUEUED` 且未取消的任务重新派发。
- 队列拒绝的任务标记 `FAILED/SYSTEM_BUSY`，不进入重试，避免高压下反复冲击线程池。

## 七、测试策略

- 事务测试：验证关键 service 方法存在 `@Transactional`。
- 缓存测试：使用 mock RedisTemplate 或轻量 fake 组件验证命中、空值缓存、互斥加载、TTL 抖动和失效。
- 控制器/服务测试：验证详情接口第二次读取命中缓存，更新后缓存失效。
- 线程池测试：验证拒绝策略抛错，业务侧标记 `FAILED/SYSTEM_BUSY`。
- 回归测试：运行后端全量 `mvn test`。

## 八、验收标准

- 关键短写 service 方法具备事务边界。
- 仓库、场景、任务详情读取使用 Redis 缓存。
- 缓存具备空值缓存、随机 TTL、互斥加载。
- 写操作后删除对应详情缓存。
- 任务执行仍使用自定义 `taskExecutionExecutor`。
- README 说明任务线程池、Web 请求线程池、拒绝策略和恢复补偿。
- `mvn test` 通过。
- `npm test` 通过。
- `npm run build` 通过。
- `docker compose config` 通过。
