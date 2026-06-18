CREATE TABLE test_repository (
    id bigint PRIMARY KEY AUTO_INCREMENT,
    name varchar(128) NOT NULL,
    git_url varchar(512) NOT NULL,
    default_branch varchar(128) NOT NULL,
    working_directory varchar(256) NULL,
    install_command varchar(256) NOT NULL,
    run_command_template varchar(512) NOT NULL,
    test_root varchar(256) NOT NULL,
    results_index_relative_path varchar(256) NOT NULL DEFAULT 'test-results/.playwright-results.json',
    artifact_root_relative_path varchar(256) NOT NULL DEFAULT '.playwright-artifacts',
    enabled tinyint NOT NULL DEFAULT 1,
    created_at datetime NOT NULL DEFAULT current_timestamp,
    updated_at datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    CONSTRAINT uk_test_repository_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE scene (
    id bigint PRIMARY KEY AUTO_INCREMENT,
    repo_id bigint NOT NULL,
    name varchar(128) NOT NULL,
    description varchar(512) NULL,
    branch varchar(128) NOT NULL,
    test_selector_type varchar(32) NOT NULL,
    test_selector_value varchar(512) NOT NULL,
    project_name varchar(64) NULL,
    browser varchar(64) NULL,
    env_json json NULL,
    run_command varchar(512) NOT NULL,
    schedule_enabled tinyint NOT NULL DEFAULT 0,
    cron_expression varchar(64) NULL,
    next_run_at datetime NULL,
    last_run_at datetime NULL,
    last_task_status varchar(32) NULL,
    created_at datetime NOT NULL DEFAULT current_timestamp,
    updated_at datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    CONSTRAINT fk_scene_repo FOREIGN KEY (repo_id) REFERENCES test_repository(id),
    CONSTRAINT uk_scene_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_scene_schedule_next_run_at
    ON scene (schedule_enabled, next_run_at);

CREATE TABLE task (
    id bigint PRIMARY KEY AUTO_INCREMENT,
    scene_id bigint NOT NULL,
    repo_id bigint NOT NULL,
    status varchar(32) NOT NULL,
    current_stage varchar(32) NULL,
    result_code varchar(32) NULL,
    result_message varchar(1024) NULL,
    cancel_requested tinyint(1) NOT NULL DEFAULT 0,
    cancel_requested_at datetime NULL,
    cancel_requested_by varchar(64) NULL,
    trigger_type varchar(32) NOT NULL,
    trigger_reason varchar(128) NULL,
    trigger_user varchar(64) NULL,
    queued_at datetime NULL,
    branch varchar(128) NOT NULL,
    commit_sha varchar(128) NULL,
    started_at datetime NULL,
    finished_at datetime NULL,
    duration_ms bigint NULL,
    runner_name varchar(128) NULL,
    log_url varchar(1024) NULL,
    resolved_branch varchar(128) NULL,
    resolved_browser varchar(32) NULL,
    resolved_env_json text NULL,
    resolved_match_value varchar(256) NULL,
    resolved_test_root varchar(256) NULL,
    resolved_run_command varchar(1024) NULL,
    created_at datetime NOT NULL DEFAULT current_timestamp,
    updated_at datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    CONSTRAINT fk_task_scene FOREIGN KEY (scene_id) REFERENCES scene(id),
    CONSTRAINT fk_task_repo FOREIGN KEY (repo_id) REFERENCES test_repository(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE case_result (
    id bigint PRIMARY KEY AUTO_INCREMENT,
    task_id bigint NOT NULL,
    history_id varchar(256) NULL,
    full_name varchar(512) NOT NULL,
    suite_name varchar(256) NULL,
    story_name varchar(256) NULL,
    status varchar(32) NOT NULL,
    duration_ms bigint NULL,
    owner_name varchar(128) NULL,
    severity varchar(64) NULL,
    project_name varchar(64) NULL,
    CONSTRAINT fk_case_result_task FOREIGN KEY (task_id) REFERENCES task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE artifact (
    id bigint PRIMARY KEY AUTO_INCREMENT,
    task_id bigint NOT NULL,
    case_result_id bigint NULL,
    artifact_type varchar(32) NOT NULL,
    bucket varchar(128) NOT NULL,
    object_key varchar(512) NOT NULL,
    content_type varchar(128) NULL,
    size bigint NULL,
    url varchar(1024) NULL,
    CONSTRAINT fk_artifact_task FOREIGN KEY (task_id) REFERENCES task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE platform_audit_log (
    id bigint PRIMARY KEY AUTO_INCREMENT,
    entity_type varchar(32) NOT NULL,
    entity_id bigint NOT NULL,
    action varchar(32) NOT NULL,
    operator_name varchar(64) NULL,
    detail_json json NULL,
    created_at datetime NOT NULL DEFAULT current_timestamp
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
