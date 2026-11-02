-- =====================================================================
-- V20 巡页（Page Patrol）模块 Schema
-- 巡页任务基于 Playwright 自主探索站点，BFS 为主、局部 DFS 为辅。
-- 所有表均按 space_id 多租户隔离。
-- =====================================================================

CREATE TABLE IF NOT EXISTS patrol_task (
    id                      bigint PRIMARY KEY AUTO_INCREMENT,
    space_id                bigint NOT NULL,
    name                    varchar(128) NOT NULL,
    start_url               varchar(2048) NOT NULL,
    browser                 varchar(32) NOT NULL DEFAULT 'chromium',
    auth_type               varchar(16) NOT NULL DEFAULT 'NONE',
    auth_cookie_encrypted   text NULL,
    auth_token_encrypted    text NULL,
    auth_token_header       varchar(128) NULL,
    max_depth               int NOT NULL DEFAULT 3,
    max_pages               int NOT NULL DEFAULT 50,
    max_steps               int NOT NULL DEFAULT 500,
    timeout_seconds         int NOT NULL DEFAULT 900,
    status                  varchar(32) NOT NULL,
    current_stage           varchar(32) NULL,
    result_message          varchar(1024) NULL,
    page_count              int NOT NULL DEFAULT 0,
    step_count              int NOT NULL DEFAULT 0,
    success_count           int NOT NULL DEFAULT 0,
    failure_count           int NOT NULL DEFAULT 0,
    skipped_count           int NOT NULL DEFAULT 0,
    screenshot_count        int NOT NULL DEFAULT 0,
    cancel_requested        tinyint(1) NOT NULL DEFAULT 0,
    started_at              datetime NULL,
    finished_at             datetime NULL,
    duration_ms             bigint NULL,
    created_by              varchar(128) NULL,
    created_at              datetime NOT NULL DEFAULT current_timestamp,
    updated_at              datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    CONSTRAINT fk_patrol_task_space FOREIGN KEY (space_id) REFERENCES space(id),
    INDEX idx_patrol_task_space_created (space_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS patrol_page (
    id              bigint PRIMARY KEY AUTO_INCREMENT,
    patrol_id       bigint NOT NULL,
    page_key        varchar(512) NOT NULL,
    page_url        varchar(2048) NOT NULL,
    title           varchar(512) NULL,
    depth           int NOT NULL DEFAULT 0,
    status          varchar(32) NOT NULL DEFAULT 'DISCOVERED',
    parent_page_key varchar(512) NULL,
    screenshot_key  varchar(512) NULL,
    first_seen_at   datetime NULL,
    created_at      datetime NOT NULL DEFAULT current_timestamp,
    CONSTRAINT fk_patrol_page_task FOREIGN KEY (patrol_id) REFERENCES patrol_task(id) ON DELETE CASCADE,
    CONSTRAINT uk_patrol_page_key UNIQUE (patrol_id, page_key),
    INDEX idx_patrol_page_depth (patrol_id, depth)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS patrol_step (
    id              bigint PRIMARY KEY AUTO_INCREMENT,
    patrol_id       bigint NOT NULL,
    seq             int NOT NULL,
    depth           int NOT NULL DEFAULT 0,
    phase           varchar(16) NOT NULL,
    action          varchar(32) NOT NULL,
    page_key        varchar(1024) NULL,
    page_url        varchar(2048) NULL,
    page_title      varchar(512) NULL,
    target_type     varchar(32) NULL,
    target_label    varchar(512) NULL,
    target_selector varchar(1024) NULL,
    status          varchar(32) NOT NULL,
    message         varchar(1024) NULL,
    result_url      varchar(2048) NULL,
    screenshot_key  varchar(512) NULL,
    duration_ms     bigint NULL,
    created_at      datetime NOT NULL DEFAULT current_timestamp,
    CONSTRAINT fk_patrol_step_task FOREIGN KEY (patrol_id) REFERENCES patrol_task(id) ON DELETE CASCADE,
    CONSTRAINT uk_patrol_step_seq UNIQUE (patrol_id, seq),
    INDEX idx_patrol_step_status (patrol_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS patrol_edge (
    id          bigint PRIMARY KEY AUTO_INCREMENT,
    patrol_id   bigint NOT NULL,
    from_key    varchar(512) NOT NULL,
    to_key      varchar(512) NOT NULL,
    label       varchar(512) NULL,
    step_seq    int NULL,
    created_at  datetime NOT NULL DEFAULT current_timestamp,
    CONSTRAINT fk_patrol_edge_task FOREIGN KEY (patrol_id) REFERENCES patrol_task(id) ON DELETE CASCADE,
    CONSTRAINT uk_patrol_edge UNIQUE (patrol_id, from_key(255), to_key(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
