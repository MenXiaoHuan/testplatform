-- H2 test profile: H2 syntax for dropping FK / altering column / multi-add.
ALTER TABLE schedule_event DROP CONSTRAINT fk_schedule_event_scene;
ALTER TABLE schedule_event ALTER COLUMN scene_id bigint NULL;

ALTER TABLE schedule_event ADD COLUMN schedule_type varchar(32) NOT NULL DEFAULT 'CRON';
ALTER TABLE schedule_event ADD COLUMN trace_id varchar(64) NULL;
ALTER TABLE schedule_event ADD COLUMN session_id varchar(64) NULL;
ALTER TABLE schedule_event ADD COLUMN user_message varchar(1024) NULL;

CREATE INDEX idx_schedule_event_type_status_updated
    ON schedule_event (schedule_type, status, updated_at, id);

CREATE INDEX idx_schedule_event_trace
    ON schedule_event (trace_id);
