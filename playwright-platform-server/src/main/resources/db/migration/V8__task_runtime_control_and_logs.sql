ALTER TABLE task
    ADD COLUMN queued_at datetime NULL AFTER trigger_user,
    ADD COLUMN current_stage varchar(32) NULL AFTER status,
    ADD COLUMN result_code varchar(32) NULL AFTER current_stage,
    ADD COLUMN result_message varchar(1024) NULL AFTER result_code,
    ADD COLUMN cancel_requested tinyint(1) NOT NULL DEFAULT 0 AFTER result_message,
    ADD COLUMN cancel_requested_at datetime NULL AFTER cancel_requested,
    ADD COLUMN trigger_reason varchar(128) NULL AFTER trigger_type;

CREATE TABLE task_stage_log (
    id bigint PRIMARY KEY AUTO_INCREMENT,
    task_id bigint NOT NULL,
    stage varchar(32) NOT NULL,
    stream_type varchar(16) NOT NULL,
    object_key varchar(512) NOT NULL,
    content_type varchar(128) NOT NULL DEFAULT 'text/plain',
    size bigint NOT NULL DEFAULT 0,
    line_count int NOT NULL DEFAULT 0,
    preview_text varchar(512) NULL,
    created_at datetime NOT NULL DEFAULT current_timestamp,
    CONSTRAINT fk_task_stage_log_task FOREIGN KEY (task_id) REFERENCES task(id)
);

CREATE TABLE scene_schedule_state (
    scene_id bigint PRIMARY KEY,
    last_planned_fire_at datetime NULL,
    last_triggered_at datetime NULL,
    last_task_id bigint NULL,
    lease_owner varchar(128) NULL,
    lease_until datetime NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    CONSTRAINT fk_scene_schedule_state_scene FOREIGN KEY (scene_id) REFERENCES scene(id)
);

CREATE TABLE platform_audit_log (
    id bigint PRIMARY KEY AUTO_INCREMENT,
    entity_type varchar(32) NOT NULL,
    entity_id bigint NOT NULL,
    action varchar(32) NOT NULL,
    operator_name varchar(64) NULL,
    detail_json json NULL,
    created_at datetime NOT NULL DEFAULT current_timestamp
);
