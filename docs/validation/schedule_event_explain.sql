USE playwright_platform;

SET @scene_id := (SELECT id FROM scene ORDER BY id LIMIT 1);

START TRANSACTION;

INSERT INTO schedule_event (
    scene_id,
    planned_fire_at,
    status,
    task_id,
    trigger_reason,
    error_message,
    retry_count,
    next_retry_at,
    last_error_at
)
SELECT
    @scene_id,
    TIMESTAMP('2026-07-02 12:00:00') + INTERVAL n MINUTE,
    CASE
        WHEN MOD(n, 10) IN (0, 1, 2, 3) THEN 'FAILED'
        WHEN MOD(n, 10) IN (4, 5) THEN 'ABANDONED'
        ELSE 'TASK_CREATED'
    END,
    CASE
        WHEN MOD(n, 10) IN (6, 7, 8, 9) THEN 100000 + n
        ELSE NULL
    END,
    CONCAT('cron:slot:', n),
    CASE
        WHEN MOD(n, 10) IN (0, 1, 2, 3) THEN 'system busy'
        WHEN MOD(n, 10) IN (4, 5) THEN 'abandoned after retries'
        ELSE NULL
    END,
    MOD(n, 4),
    CASE
        WHEN MOD(n, 10) IN (0, 1, 2, 3) THEN TIMESTAMP('2026-07-02 13:00:00') + INTERVAL MOD(n, 5) MINUTE
        ELSE NULL
    END,
    CASE
        WHEN MOD(n, 10) IN (0, 1, 2, 3, 4, 5) THEN TIMESTAMP('2026-07-02 12:30:00') + INTERVAL MOD(n, 15) MINUTE
        ELSE NULL
    END
FROM (
    SELECT ones.n
           + tens.n * 10
           + hundreds.n * 100
           + thousands.n * 1000
           + 1 AS n
    FROM (
        SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ) ones
    CROSS JOIN (
        SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ) tens
    CROSS JOIN (
        SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ) hundreds
    CROSS JOIN (
        SELECT 0 AS n UNION ALL SELECT 1
    ) thousands
) seq
WHERE seq.n <= 2000;

SELECT 'EXPLAIN_retry_failed_events' AS marker;
EXPLAIN FORMAT=TREE
SELECT id, scene_id, planned_fire_at, status, task_id, trigger_reason, error_message,
       retry_count, next_retry_at, last_error_at, created_at, updated_at
FROM schedule_event FORCE INDEX (idx_schedule_event_retry)
WHERE status = 'FAILED'
  AND task_id IS NULL
  AND retry_count < 3
  AND next_retry_at <= TIMESTAMP('2026-07-02 13:02:00')
ORDER BY created_at ASC, id ASC
LIMIT 20;

SELECT 'EXPLAIN_count_issue_events_by_scene' AS marker;
EXPLAIN FORMAT=TREE
SELECT COUNT(1)
FROM schedule_event FORCE INDEX (idx_schedule_event_issue_scene_status_updated)
WHERE status IN ('FAILED', 'ABANDONED')
  AND scene_id = @scene_id;

SELECT 'EXPLAIN_issue_page_by_scene' AS marker;
EXPLAIN FORMAT=TREE
SELECT id, scene_id, planned_fire_at, status, task_id, trigger_reason, error_message,
       retry_count, next_retry_at, last_error_at, created_at, updated_at
FROM schedule_event FORCE INDEX (idx_schedule_event_issue_scene_status_updated)
WHERE status IN ('FAILED', 'ABANDONED')
  AND scene_id = @scene_id
ORDER BY updated_at DESC, id DESC
LIMIT 20 OFFSET 0;

ROLLBACK;
