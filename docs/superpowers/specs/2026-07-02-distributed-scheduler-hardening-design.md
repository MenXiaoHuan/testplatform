# Distributed Scheduler Hardening Design

## Goal

在不引入 MQ、不过度改造任务执行链路的前提下，把当前基于 `@Scheduled` 的场景定时调度提升为更严格的多实例防重实现，确保同一个 `scene` 在同一个 `plannedFireAt` 上只能成功触发一次，同时保留不同场景同一时刻可并发创建任务的能力。

## Context

当前仓库的调度主链路如下：

```text
SceneServiceImpl.@Scheduled
-> SceneSchedulerServiceImpl.triggerDueScenes(now)
-> SceneScheduleLeaseService.tryAcquire(sceneId, plannedFireAt)
-> taskService.createScheduledTask(sceneId, triggerReason)
-> taskExecutionExecutor 异步执行任务
```

当前实现的优点是简单直接，且已经把定时调度与任务执行分开到了“扫描创建任务”和“线程池执行任务”两个阶段。  
当前实现的不足在于：

1. `SceneScheduleLeaseServiceImpl` 仍然是“先查后改”的 Java 侧判断，不是严格的数据库原子竞争。
2. `lease_owner` 固定为 `"local-scheduler"`，无法区分实例。
3. 没有独立的调度事件幂等表，无法在数据库层明确表示“这个场景在这个触发点已经被处理过”。
4. 多实例场景下虽有基础防重能力，但还不能视为严格成熟的分布式调度。

## Requirements

### Functional Requirements

1. 保留现有 `scene.next_run_at` 作为“到期场景”的判断依据。
2. 同一个 `scene` 在同一个 `plannedFireAt` 上只能成功调度一次。
3. 不同 `scene` 在同一时刻到期时，仍然要能够分别创建自己的 `task`。
4. 保留现有 `taskService.createScheduledTask(...)` 和 `taskExecutionExecutor` 执行链路，不引入 MQ。
5. 调度失败要有结构化落点，便于后续排查和恢复。

### Non-Functional Requirements

1. 改造应尽量贴合现有 MyBatis + Spring Boot + Flyway 架构。
2. 不引入 RabbitMQ、RocketMQ、Kafka、Redis List 队列等新基础设施。
3. 改动应控制在调度与调度事件边界内，不扩展到任务执行编排主体。
4. 所有并发防重逻辑必须具备单元测试或集成测试覆盖。

## Options Considered

### Option A: 继续沿用当前方案，仅做少量 Java 逻辑增强

做法：

- 继续使用 `scene_schedule_state`
- 在 Java 层强化 `lastPlannedFireAt` 判断
- 尽量少改 mapper / schema

优点：

- 改动最小

缺点：

- 仍然不是原子竞争
- 多实例下仍有并发空隙
- 没有显式的调度事件幂等记录

结论：

- 不推荐。它只能改善现状，不能从根上把调度幂等拉到数据库层。

### Option B: 数据库原子租约 + 调度事件幂等表

做法：

- 强化 `scene_schedule_state` 为数据库原子认领状态
- 新增 `schedule_event` 表
- 以 `(scene_id, planned_fire_at)` 唯一约束表示一次调度点
- 仍沿用 `createScheduledTask(...)` 进入现有任务链路

优点：

- 和现有架构最贴合
- 改动可控
- 能明确解决当前最关键的问题：多实例重复触发
- 为后续 MQ 化保留清晰演进路径

缺点：

- 调度器和任务创建仍然耦合在同一服务里
- 不包含消息重试、续租心跳、worker 解耦等更完整能力

结论：

- 推荐，作为本轮实施方案。

### Option C: 直接引入 MQ 做单消费者调度

做法：

- 引入 RabbitMQ / RocketMQ / Kafka / Redis 队列
- 调度器只发消息
- 通过排他消费者或单分区消费组保证单实例消费

优点：

- 更接近完整分布式调度架构

缺点：

- 需要重构现有调度链路
- 超出本轮目标
- 会引入新的运维和测试复杂度

结论：

- 暂不采用，留作下一阶段演进方案。

## Selected Approach

采用 **Option B: 数据库原子租约 + 调度事件幂等表**。

这轮实现目标不是把系统直接做成完整事件驱动调度平台，而是先在现有代码框架下，把“定时扫描”和“调度去重”做对。  
设计原则如下：

1. 扫描入口保留：仍由 `@Scheduled(fixedDelay = 60000)` 每分钟扫描。
2. 到期判断保留：仍以 `scene.next_run_at <= now` 判定到期场景。
3. 认领必须原子：由数据库条件更新来决定谁成功认领这个 `scene + plannedFireAt`。
4. 幂等必须落库：由 `schedule_event(scene_id, planned_fire_at)` 唯一约束兜底。
5. 执行链路不扩张：成功创建调度事件后，仍然调用 `taskService.createScheduledTask(...)`。

## Design Details

### 1. `scene_schedule_state` 的职责

`scene_schedule_state` 继续表示“某个场景当前调度状态”，但职责从“辅助记录状态”增强为“原子认领控制”。

现有关键字段继续保留：

- `scene_id`
- `last_planned_fire_at`
- `last_triggered_at`
- `last_task_id`
- `lease_owner`
- `lease_until`
- `version`

本轮对字段的使用约束：

- `lease_owner` 改为真实实例 ID，而不是固定值
- `last_planned_fire_at` 用于标识上一次成功认领的计划触发点
- `version` 将用于条件更新或后续乐观锁演进
- `lease_until` 本轮保留并写入，但不实现续租流程

### 2. 新增 `schedule_event` 表

新增一张调度事件表，表示“某个场景在某个计划触发点的一次调度事件”。

建议字段：

- `id`
- `scene_id`
- `planned_fire_at`
- `status`
- `task_id`
- `trigger_reason`
- `error_message`
- `created_at`
- `updated_at`

关键约束：

- 唯一键：`(scene_id, planned_fire_at)`

状态建议：

- `ACQUIRED`：已认领，尚未创建任务
- `TASK_CREATED`：已成功创建任务
- `FAILED`：尝试创建任务失败

作用：

1. 在数据库层表示“这个触发点已经处理过”
2. 为失败排查和后续补偿保留结构化记录
3. 为后续 MQ 化演进提供自然落点

### 3. 实例标识

新增一个实例标识提供者，用于写入 `lease_owner`。  
本轮不做复杂服务发现，采用轻量且可测试的方式：

- 优先读取环境变量或配置项
- 若未配置，则在启动时生成稳定的本实例 UUID

要求：

- 同一实例生命周期内值稳定
- 测试中可注入伪值

### 4. 原子认领逻辑

不再使用单纯的“查 state -> Java 判断 -> update”流程。  
推荐流程：

1. 尝试读取 `scene_schedule_state`
2. 若不存在，插入初始记录
3. 通过条件更新方式认领：
   - 仅当 `last_planned_fire_at` 为空或小于当前 `plannedFireAt` 时允许推进
   - 更新 `lease_owner`、`lease_until`、`last_planned_fire_at`
4. 依据受影响行数判断是否成功认领

认领成功后，当前实例才允许进入创建 `schedule_event` 的步骤。

### 5. 调度主流程

新的调度流程如下：

```text
@Scheduled 扫描
-> 找出 next_run_at <= now 的 scene
-> tryAcquire(sceneId, plannedFireAt)
-> 成功后插入 schedule_event
-> 推进 scene.next_run_at 到下一次
-> createScheduledTask(sceneId, triggerReason)
-> 更新 schedule_event.status/task_id
```

其中：

- 如果认领失败：直接跳过当前场景
- 如果插入 `schedule_event` 发生唯一键冲突：说明该调度点已被处理，直接跳过
- 如果 `createScheduledTask(...)` 失败：将 `schedule_event` 标记为 `FAILED`

### 6. 与现有任务创建的关系

保留现有 [TaskCreationService](file:///Users/bytedance/test_platform/playwright-platform-server/src/main/java/com/example/platform/task/service/TaskCreationService.java) 对“同一场景不能同时有多个活动任务”的约束。

因此本轮会形成两层保护：

1. **调度点幂等保护**：同一个 `scene + plannedFireAt` 只能被成功处理一次
2. **场景活动任务保护**：同一个 `scene` 同时不能存在多个 `QUEUED/RUNNING` 任务

这两层保护分别覆盖：

- 分布式扫描下的重复触发
- 单场景执行中的重复创建

### 7. 错误处理

本轮不引入自动补偿，只保证错误落库和日志清晰。

处理方式：

- 认领失败：视为正常竞争失败，不记为错误
- 幂等唯一键冲突：视为已处理，记录 debug/info 日志
- 任务创建失败：`schedule_event.status = FAILED`，记录错误信息

不做：

- 自动重试失败调度事件
- 自动扫描 `FAILED` 事件补偿
- 自动续租或租约恢复

## Files to Modify or Add

### Database / Schema

- Add: `playwright-platform-server/src/main/resources/db/migration/V2__add_schedule_event.sql`
- Modify: `playwright-platform-server/src/main/resources/db/schema/SCHEMA_OVERVIEW.sql`

### Scene Scheduling

- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneScheduleLeaseServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/mapper/SceneScheduleStateMapper.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneSchedulerServiceImpl.java`

### Schedule Event

- Add: `playwright-platform-server/src/main/java/com/example/platform/scene/model/ScheduleEventEntity.java`
- Add: `playwright-platform-server/src/main/java/com/example/platform/scene/mapper/ScheduleEventMapper.java`
- Add: `playwright-platform-server/src/main/java/com/example/platform/scene/service/ScheduleEventService.java`
- Add: `playwright-platform-server/src/main/java/com/example/platform/scene/service/ScheduleEventServiceImpl.java`

### Instance Identity

- Add: `playwright-platform-server/src/main/java/com/example/platform/scene/service/SchedulerInstanceIdProvider.java`

### Tests

- Add or modify schedule-related tests under `playwright-platform-server/src/test/java/com/example/platform/scene/`

## Testing Strategy

本轮测试重点放在调度一致性，而不是任务执行主体。

建议覆盖：

1. **认领成功测试**
   - 首次认领某个 `scene + plannedFireAt` 成功

2. **重复认领失败测试**
   - 同一个 `scene + plannedFireAt` 第二次认领失败

3. **调度事件幂等测试**
   - 同一个 `scene + plannedFireAt` 重复插入事件时，唯一键生效

4. **不同场景同一时刻测试**
   - 不同 `scene` 同一 `plannedFireAt` 可分别创建自己的事件与任务

5. **任务创建失败测试**
   - `createScheduledTask(...)` 抛错时，事件状态标记为 `FAILED`

6. **现有任务保护测试**
   - 当同一场景已有 `QUEUED/RUNNING` 任务时，仍不允许重复创建活动任务

## Out of Scope

本轮明确不做以下内容：

1. MQ 引入与消费者架构调整
2. RabbitMQ 排他消费者 / Kafka 单分区消费组 / Redis List 队列
3. 调度器与执行器彻底拆分
4. 分布式续租心跳
5. 调度失败自动补偿
6. 调度后台管理页面或可视化运维页面

## Rollout and Migration Notes

1. 数据库迁移新增 `schedule_event` 表，不修改现有 `scene`、`task` 主表结构。
2. 新版本上线后，旧场景会继续使用已有 `next_run_at` 机制。
3. `scene_schedule_state` 历史数据若为空，可在首次扫描或首次认领时惰性初始化。
4. 若本地或测试环境存在旧数据库，需确保 Flyway 能顺利执行新迁移。

## Future Evolution

当本轮方案稳定后，下一阶段推荐演进方向为：

1. Scheduler 只负责写 `schedule_event`
2. 将 `schedule_event` 投递到 Kafka / RocketMQ
3. 由单分区消费组独占消费调度事件
4. Worker 创建 task，并回填事件状态
5. 最终实现“调度层”和“执行层”彻底解耦

该演进方向比直接接入 RabbitMQ 排他消费者或 Redis List 更适合本项目后续的事件化调度架构。
