CREATE TABLE schedule_event (
    id bigint PRIMARY KEY AUTO_INCREMENT,
    scene_id bigint NOT NULL,
    planned_fire_at datetime NOT NULL,
    status varchar(32) NOT NULL,
    task_id bigint NULL,
    trigger_reason varchar(128) NULL,
    error_message varchar(1024) NULL,
    retry_count int NOT NULL DEFAULT 0,
    next_retry_at datetime NULL,
    last_error_at datetime NULL,
    created_at datetime NOT NULL DEFAULT current_timestamp,
    updated_at datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    CONSTRAINT fk_schedule_event_scene FOREIGN KEY (scene_id) REFERENCES scene(id),
    CONSTRAINT uk_schedule_event_scene_fire UNIQUE (scene_id, planned_fire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_schedule_event_retry
    ON schedule_event (status, task_id, next_retry_at, retry_count, created_at, id);

CREATE INDEX idx_schedule_event_issue_status_updated
    ON schedule_event (status, updated_at, id);

CREATE INDEX idx_schedule_event_issue_scene_status_updated
    ON schedule_event (scene_id, status, updated_at, id);
