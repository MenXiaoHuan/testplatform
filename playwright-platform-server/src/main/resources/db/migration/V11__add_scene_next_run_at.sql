ALTER TABLE scene
    ADD COLUMN next_run_at datetime NULL AFTER cron_expression;

CREATE INDEX idx_scene_schedule_next_run_at
    ON scene (schedule_enabled, next_run_at);
