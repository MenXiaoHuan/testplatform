-- H2 test profile: H2 does not support multiple ADD COLUMN clauses in one ALTER.
ALTER TABLE task_stage_log ADD COLUMN duration_ms BIGINT NULL DEFAULT 0;
ALTER TABLE task_stage_log ADD COLUMN exit_code INT NULL;
ALTER TABLE task_stage_log ADD COLUMN stage_status VARCHAR(32) NULL DEFAULT 'SUCCESS';
ALTER TABLE task_stage_log ADD COLUMN command VARCHAR(1024) NULL;
ALTER TABLE task_stage_log ADD COLUMN started_at DATETIME NULL;
ALTER TABLE task_stage_log ADD COLUMN ended_at DATETIME NULL;
ALTER TABLE task_stage_log ADD COLUMN error_message TEXT NULL;
