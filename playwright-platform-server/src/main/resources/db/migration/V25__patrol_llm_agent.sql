-- =====================================================================
-- V25 巡页 LLM 化：预算、用例、用例步骤与问题清单
-- 1) patrol_task 由爬虫式硬约束改为预算制，并新增报告统计字段。
-- 2) patrol_page 保存页面快照（可见文本 + 结构化元素），供 LLM 规划与判定。
-- 3) 新增 patrol_case / patrol_case_step / patrol_finding 三张表。
-- 注：max_depth / max_pages / max_steps / max_elements_per_page 列保留读兼容，
--    新建任务不再写入，执行阶段不再生效。
-- =====================================================================

ALTER TABLE patrol_task
    ADD COLUMN task_brief varchar(1024) NULL AFTER dangerous_keywords,
    ADD COLUMN budget_duration_minutes int NOT NULL DEFAULT 15 AFTER task_brief,
    ADD COLUMN budget_actions int NOT NULL DEFAULT 400 AFTER budget_duration_minutes,
    ADD COLUMN budget_llm_tokens int NOT NULL DEFAULT 300000 AFTER budget_actions,
    ADD COLUMN budget_pages int NOT NULL DEFAULT 40 AFTER budget_llm_tokens,
    ADD COLUMN budget_cases_per_page int NOT NULL DEFAULT 5 AFTER budget_pages,
    ADD COLUMN case_count int NOT NULL DEFAULT 0 AFTER budget_cases_per_page,
    ADD COLUMN case_pass_count int NOT NULL DEFAULT 0 AFTER case_count,
    ADD COLUMN case_fail_count int NOT NULL DEFAULT 0 AFTER case_pass_count,
    ADD COLUMN case_skip_count int NOT NULL DEFAULT 0 AFTER case_fail_count,
    ADD COLUMN finding_p0_count int NOT NULL DEFAULT 0 AFTER case_skip_count,
    ADD COLUMN finding_p1_count int NOT NULL DEFAULT 0 AFTER finding_p0_count,
    ADD COLUMN finding_p2_count int NOT NULL DEFAULT 0 AFTER finding_p1_count,
    ADD COLUMN llm_token_used int NOT NULL DEFAULT 0 AFTER finding_p2_count,
    ADD COLUMN degraded tinyint(1) NOT NULL DEFAULT 0 AFTER llm_token_used;

ALTER TABLE patrol_page
    ADD COLUMN visible_text text NULL AFTER console_error_count,
    ADD COLUMN snapshot_json mediumtext NULL AFTER visible_text;

CREATE TABLE IF NOT EXISTS patrol_case (
    id              bigint PRIMARY KEY AUTO_INCREMENT,
    patrol_id       bigint NOT NULL,
    page_key        varchar(512) NOT NULL,
    name            varchar(256) NOT NULL,
    intent          varchar(1024) NULL,
    expected        varchar(2048) NULL,
    priority        varchar(8) NOT NULL DEFAULT 'P1',
    status          varchar(16) NOT NULL DEFAULT 'PENDING',
    exec_status     varchar(16) NOT NULL DEFAULT 'PENDING',
    source          varchar(16) NOT NULL DEFAULT 'LLM',
    degraded        tinyint(1) NOT NULL DEFAULT 0,
    llm_reason      varchar(1024) NULL,
    screenshot_key  varchar(512) NULL,
    duration_ms     bigint NULL,
    created_at      datetime NOT NULL DEFAULT current_timestamp,
    CONSTRAINT fk_patrol_case_task FOREIGN KEY (patrol_id) REFERENCES patrol_task(id) ON DELETE CASCADE,
    INDEX idx_patrol_case_page (patrol_id, page_key),
    INDEX idx_patrol_case_status (patrol_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS patrol_case_step (
    id              bigint PRIMARY KEY AUTO_INCREMENT,
    case_id         bigint NOT NULL,
    seq             int NOT NULL,
    action          varchar(32) NOT NULL,
    target_selector varchar(1024) NULL,
    target_label    varchar(512) NULL,
    input_value     varchar(1024) NULL,
    status          varchar(16) NOT NULL DEFAULT 'PENDING',
    message         varchar(1024) NULL,
    screenshot_key  varchar(512) NULL,
    duration_ms     bigint NULL,
    created_at      datetime NOT NULL DEFAULT current_timestamp,
    CONSTRAINT fk_patrol_case_step_case FOREIGN KEY (case_id) REFERENCES patrol_case(id) ON DELETE CASCADE,
    CONSTRAINT uk_patrol_case_step UNIQUE (case_id, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS patrol_finding (
    id                  bigint PRIMARY KEY AUTO_INCREMENT,
    patrol_id           bigint NOT NULL,
    case_id             bigint NULL,
    step_seq            int NULL,
    type                varchar(16) NOT NULL,
    severity            varchar(8) NOT NULL,
    title               varchar(256) NOT NULL,
    description         text NULL,
    screenshot_key      varchar(512) NULL,
    element_selector    varchar(1024) NULL,
    repro_steps         text NULL,
    status              varchar(16) NOT NULL DEFAULT 'OPEN',
    handled_by          varchar(128) NULL,
    handled_at          datetime NULL,
    degraded            tinyint(1) NOT NULL DEFAULT 0,
    created_at          datetime NOT NULL DEFAULT current_timestamp,
    CONSTRAINT fk_patrol_finding_task FOREIGN KEY (patrol_id) REFERENCES patrol_task(id) ON DELETE CASCADE,
    INDEX idx_patrol_finding_severity (patrol_id, severity),
    INDEX idx_patrol_finding_status (patrol_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
