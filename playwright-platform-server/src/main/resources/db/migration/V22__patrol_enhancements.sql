-- =====================================================================
-- V22 巡页能力增强
-- 1) 认证扩展：支持 localStorage 注入；Cookie/Token 沿用密文存储。
-- 2) 浏览器与无头模式开关；危险操作运行前一次性审批。
-- 3) 断言结果与截图比对基线（同空间同入口最近一次成功巡页）。
-- =====================================================================

ALTER TABLE patrol_task
    ADD COLUMN auth_local_storage_encrypted text NULL AFTER auth_token_header,
    ADD COLUMN headless tinyint(1) NOT NULL DEFAULT 1 AFTER auth_token_header,
    ADD COLUMN dangerous_approved tinyint(1) NOT NULL DEFAULT 0 AFTER timeout_seconds,
    ADD COLUMN dangerous_approved_by varchar(128) NULL AFTER dangerous_approved,
    ADD COLUMN baseline_patrol_id bigint NULL AFTER dangerous_approved_by;

ALTER TABLE patrol_page
    ADD COLUMN console_error_count int NOT NULL DEFAULT 0 AFTER status;

CREATE TABLE IF NOT EXISTS patrol_assertion (
    id                  bigint PRIMARY KEY AUTO_INCREMENT,
    patrol_id           bigint NOT NULL,
    scope               varchar(16) NOT NULL,
    page_key            varchar(512) NULL,
    page_url            varchar(2048) NULL,
    assertion_type      varchar(32) NOT NULL,
    name                varchar(256) NOT NULL,
    status              varchar(16) NOT NULL,
    expected_value      varchar(1024) NULL,
    actual_value        varchar(1024) NULL,
    diff_ratio          decimal(6, 4) NULL,
    baseline_patrol_id  bigint NULL,
    created_at          datetime NOT NULL DEFAULT current_timestamp,
    CONSTRAINT fk_patrol_assertion_task FOREIGN KEY (patrol_id) REFERENCES patrol_task(id) ON DELETE CASCADE,
    INDEX idx_patrol_assertion_status (patrol_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
