# schedule_event EXPLAIN Validation

这份验证用于确认 `schedule_event` 的索引设计是否覆盖当前三类核心查询：

1. 自动补偿重试查询
2. 问题事件数量统计查询
3. 问题事件分页查询

验证脚本：

- [schedule_event_explain.sql](file:///Users/bytedance/test_platform/docs/validation/schedule_event_explain.sql)

## 运行方式

在项目根目录执行：

```bash
set -a
source .env
/usr/local/bin/docker compose exec -T mysql \
  mysql -u"$PLATFORM_DB_USERNAME" -p"$PLATFORM_DB_PASSWORD" \
  "$PLATFORM_DB_NAME" < docs/validation/schedule_event_explain.sql
```

## 当前关注的索引

- `uk_schedule_event_scene_fire (scene_id, planned_fire_at)`
- `idx_schedule_event_retry (status, task_id, next_retry_at, retry_count, created_at, id)`
- `idx_schedule_event_issue_status_updated (status, updated_at, id)`
- `idx_schedule_event_issue_scene_status_updated (scene_id, status, updated_at, id)`

## 判定标准

### 自动补偿重试查询

目标：

- 优先命中 `idx_schedule_event_retry`
- 避免全表扫描

关键过滤条件：

- `status = 'FAILED'`
- `task_id is null`
- `retry_count < ?`
- `next_retry_at <= ?`

### 问题事件按场景统计 / 分页

目标：

- 按 `scene_id + status` 查询时优先命中 `idx_schedule_event_issue_scene_status_updated`
- 分页排序时尽量减少额外排序代价

关键过滤条件：

- `status in (...)`
- `scene_id = ?`
- `order by updated_at desc, id desc`

## 备注

这份脚本会在事务中插入一批模拟数据并在结尾 `ROLLBACK`，不会污染本地数据。
