ALTER TABLE task_stage_log
    ADD COLUMN duration_ms BIGINT NULL DEFAULT 0 AFTER line_count,
    ADD COLUMN exit_code INT NULL AFTER duration_ms,
    ADD COLUMN stage_status VARCHAR(32) NULL DEFAULT 'SUCCESS' AFTER exit_code,
    ADD COLUMN command VARCHAR(1024) NULL AFTER stage_status,
    ADD COLUMN started_at DATETIME NULL AFTER command,
    ADD COLUMN ended_at DATETIME NULL AFTER started_at,
    ADD COLUMN error_message TEXT NULL AFTER ended_at;
