-- V8: 新增 Agent 调度事件支持
-- 1. 删除 scene_id 外键约束，允许 Agent 调度事件不关联具体场景
ALTER TABLE schedule_event DROP FOREIGN KEY fk_schedule_event_scene;
ALTER TABLE schedule_event MODIFY COLUMN scene_id bigint NULL;

-- 2. 新增调度类型/追踪ID/会话ID/用户消息字段
ALTER TABLE schedule_event
    ADD COLUMN schedule_type varchar(32) NOT NULL DEFAULT 'CRON' AFTER status,
    ADD COLUMN trace_id varchar(64) NULL AFTER schedule_type,
    ADD COLUMN session_id varchar(64) NULL AFTER trace_id,
    ADD COLUMN user_message varchar(1024) NULL AFTER trigger_reason;

-- 3. 调度类型 + 状态 + 更新时间 索引（按类型筛选调度事件列表）
CREATE INDEX idx_schedule_event_type_status_updated
    ON schedule_event (schedule_type, status, updated_at, id);

-- 4. trace_id 索引（从 trace 反查调度事件）
CREATE INDEX idx_schedule_event_trace
    ON schedule_event (trace_id);
