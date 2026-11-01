-- =====================================================================
-- V11 大模型评测模块 Schema
-- 所有表均带 space_id 多租户隔离
-- =====================================================================

-- ========== 被评目标 ==========
CREATE TABLE IF NOT EXISTS eval_target (
    id              bigint PRIMARY KEY AUTO_INCREMENT,
    space_id        bigint NOT NULL,
    name            varchar(256) NOT NULL,
    target_type     varchar(32) NOT NULL,
    adapter_config  json NOT NULL,
    runtime_options json NULL,
    langfuse_tracing json NOT NULL,
    tags            json NULL,
    status          varchar(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      datetime NOT NULL DEFAULT current_timestamp,
    updated_at      datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    CONSTRAINT fk_eval_target_space FOREIGN KEY (space_id) REFERENCES space(id),
    CONSTRAINT uk_eval_target_space_name UNIQUE (space_id, name),
    INDEX idx_eval_target_space_status (space_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== Prompt 托管中心 ==========
CREATE TABLE IF NOT EXISTS eval_prompt_version (
    id              bigint PRIMARY KEY AUTO_INCREMENT,
    space_id        bigint NOT NULL,
    prompt_key      varchar(128) NOT NULL,
    name            varchar(256) NOT NULL,
    description     varchar(1024) NULL,
    prompt_type     varchar(32) NOT NULL,
    template        json NOT NULL,
    variables       json NULL,
    version_label   varchar(32) NOT NULL,
    change_log      varchar(1024) NULL,
    status          varchar(32) NOT NULL,
    superseded_by   bigint NULL,
    created_by      varchar(128) NOT NULL,
    created_at      datetime NOT NULL DEFAULT current_timestamp,
    updated_at      datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    CONSTRAINT fk_eval_prompt_space FOREIGN KEY (space_id) REFERENCES space(id),
    CONSTRAINT fk_eval_prompt_superseded FOREIGN KEY (superseded_by) REFERENCES eval_prompt_version(id),
    CONSTRAINT uk_eval_prompt_active UNIQUE (space_id, prompt_key, status),
    CONSTRAINT uk_eval_prompt_version UNIQUE (space_id, prompt_key, version_label),
    INDEX idx_eval_prompt_key (space_id, prompt_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 评测集 ==========
CREATE TABLE IF NOT EXISTS eval_dataset (
    id                      bigint PRIMARY KEY AUTO_INCREMENT,
    space_id                bigint NOT NULL,
    name                    varchar(256) NOT NULL,
    version                 varchar(32) NOT NULL DEFAULT 'v1',
    description             varchar(1024) NULL,
    scenario_tags           json NULL,
    recommended_target_types json NULL,
    external_source         varchar(512) NULL,
    total_samples           int NOT NULL DEFAULT 0,
    annotated_samples       int NOT NULL DEFAULT 0,
    created_at              datetime NOT NULL DEFAULT current_timestamp,
    updated_at              datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    CONSTRAINT fk_eval_dataset_space FOREIGN KEY (space_id) REFERENCES space(id),
    CONSTRAINT uk_eval_dataset_space_name UNIQUE (space_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 评测集样本 ==========
CREATE TABLE IF NOT EXISTS eval_dataset_sample (
    id                      bigint PRIMARY KEY AUTO_INCREMENT,
    dataset_id              bigint NOT NULL,
    sample_index            int NOT NULL,
    input_text              text NOT NULL,
    golden_answer           text NULL,
    golden_answer_versions  json NULL,
    reference_context       text NULL,
    metadata                json NULL,
    scenario_tags           json NULL,
    created_at              datetime NOT NULL DEFAULT current_timestamp,
    updated_at              datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    CONSTRAINT fk_eval_sample_dataset FOREIGN KEY (dataset_id) REFERENCES eval_dataset(id) ON DELETE CASCADE,
    INDEX idx_eval_sample_dataset_idx (dataset_id, sample_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 离线评测运行 ==========
CREATE TABLE IF NOT EXISTS eval_run (
    id                  bigint PRIMARY KEY AUTO_INCREMENT,
    space_id            bigint NOT NULL,
    target_id           bigint NOT NULL,
    prompt_version_id   bigint NULL,
    dataset_id          bigint NOT NULL,
    status              varchar(32) NOT NULL,
    total_samples       int NOT NULL,
    completed_samples   int NOT NULL DEFAULT 0,
    failed_samples      int NOT NULL DEFAULT 0,
    overall_score       decimal(5,2) NULL,
    evaluator_configs   json NOT NULL,
    max_concurrency     int NOT NULL DEFAULT 2,
    started_at          datetime NULL,
    finished_at         datetime NULL,
    trigger_user        varchar(128) NULL,
    version             bigint NOT NULL DEFAULT 0,
    created_at          datetime NOT NULL DEFAULT current_timestamp,
    updated_at          datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    CONSTRAINT fk_eval_run_space FOREIGN KEY (space_id) REFERENCES space(id),
    CONSTRAINT fk_eval_run_target FOREIGN KEY (target_id) REFERENCES eval_target(id),
    CONSTRAINT fk_eval_run_dataset FOREIGN KEY (dataset_id) REFERENCES eval_dataset(id),
    CONSTRAINT fk_eval_run_prompt FOREIGN KEY (prompt_version_id) REFERENCES eval_prompt_version(id),
    INDEX idx_eval_run_space_status (space_id, status),
    INDEX idx_eval_run_target_status (target_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 离线评测样本结果 ==========
CREATE TABLE IF NOT EXISTS eval_run_case_result (
    id                      bigint PRIMARY KEY AUTO_INCREMENT,
    run_id                  bigint NOT NULL,
    sample_id               bigint NOT NULL,
    sample_index            int NOT NULL,
    agent_output            text NOT NULL,
    golden_answer           text NULL,
    evaluator_scores        json NOT NULL,
    weighted_score          decimal(5,2) NULL,
    tokens_in               bigint NULL,
    tokens_out              bigint NULL,
    duration_ms             bigint NULL,
    external_trace_id       varchar(256) NULL,
    langfuse_trace_id       varchar(256) NULL,
    actual_prompt_version_id bigint NULL,
    status                  varchar(32) NOT NULL,
    error_message           varchar(1024) NULL,
    created_at              datetime NOT NULL DEFAULT current_timestamp,
    CONSTRAINT fk_eval_case_run FOREIGN KEY (run_id) REFERENCES eval_run(id) ON DELETE CASCADE,
    INDEX idx_eval_case_run_idx (run_id, sample_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 在线评测策略 ==========
CREATE TABLE IF NOT EXISTS eval_online_policy (
    id                  bigint PRIMARY KEY AUTO_INCREMENT,
    space_id            bigint NOT NULL,
    target_id           bigint NOT NULL,
    name                varchar(256) NOT NULL,
    status              varchar(32) NOT NULL,
    filter_config       json NOT NULL,
    evaluator_configs   json NOT NULL,
    alerting_config     json NULL,
    rate_limit_config   json NULL,
    scan_interval_minutes int NOT NULL DEFAULT 5,
    next_scan_at        datetime NULL,
    created_at          datetime NOT NULL DEFAULT current_timestamp,
    updated_at          datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    CONSTRAINT fk_eval_policy_space FOREIGN KEY (space_id) REFERENCES space(id),
    CONSTRAINT fk_eval_policy_target FOREIGN KEY (target_id) REFERENCES eval_target(id),
    CONSTRAINT uk_eval_policy_space_name UNIQUE (space_id, name),
    INDEX idx_eval_policy_status (status, next_scan_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 在线评分留痕（断点续跑） ==========
CREATE TABLE IF NOT EXISTS eval_online_scoring (
    id                      bigint PRIMARY KEY AUTO_INCREMENT,
    policy_id               bigint NOT NULL,
    langfuse_trace_id       varchar(256) NOT NULL,
    evaluator_scores        json NOT NULL,
    weighted_score          decimal(5,2) NULL,
    low_score               tinyint NOT NULL DEFAULT 0,
    created_at              datetime NOT NULL DEFAULT current_timestamp,
    CONSTRAINT uk_eval_scoring_policy_trace UNIQUE (policy_id, langfuse_trace_id),
    CONSTRAINT fk_eval_scoring_policy FOREIGN KEY (policy_id) REFERENCES eval_online_policy(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== A/B 实验 ==========
CREATE TABLE IF NOT EXISTS eval_ab_experiment (
    id                      bigint PRIMARY KEY AUTO_INCREMENT,
    space_id                bigint NOT NULL,
    name                    varchar(256) NOT NULL,
    hypothesis              varchar(512) NULL,
    status                  varchar(32) NOT NULL,
    control_target_id       bigint NOT NULL,
    control_prompt_version_id bigint NULL,
    treatment_target_ids    json NOT NULL,
    treatment_prompt_version_ids json NULL,
    traffic_percentage      int NOT NULL DEFAULT 50,
    core_metrics            json NULL,
    dataset_id              bigint NULL,
    started_at              datetime NULL,
    finished_at             datetime NULL,
    created_at              datetime NOT NULL DEFAULT current_timestamp,
    updated_at              datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    CONSTRAINT fk_eval_ab_space FOREIGN KEY (space_id) REFERENCES space(id),
    CONSTRAINT fk_eval_ab_control_target FOREIGN KEY (control_target_id) REFERENCES eval_target(id),
    CONSTRAINT uk_eval_ab_space_name UNIQUE (space_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS eval_ab_assignment (
    id              bigint PRIMARY KEY AUTO_INCREMENT,
    experiment_id   bigint NOT NULL,
    user_hash_bucket int NOT NULL,
    assigned_group  varchar(32) NOT NULL,
    CONSTRAINT fk_eval_ab_assign_exp FOREIGN KEY (experiment_id) REFERENCES eval_ab_experiment(id) ON DELETE CASCADE,
    CONSTRAINT uk_eval_ab_assign_exp_bucket UNIQUE (experiment_id, user_hash_bucket)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS eval_ab_run_result (
    id                      bigint PRIMARY KEY AUTO_INCREMENT,
    experiment_id           bigint NOT NULL,
    input_text              text NOT NULL,
    control_output          text NOT NULL,
    treatment_outputs       json NOT NULL,
    control_scores          json NULL,
    treatment_scores        json NULL,
    langfuse_trace_ids      json NULL,
    prompt_versions_used    json NULL,
    created_at              datetime NOT NULL DEFAULT current_timestamp,
    CONSTRAINT fk_eval_ab_result_exp FOREIGN KEY (experiment_id) REFERENCES eval_ab_experiment(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
