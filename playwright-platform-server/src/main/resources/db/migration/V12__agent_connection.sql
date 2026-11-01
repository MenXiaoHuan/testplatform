-- =====================================================================
-- V12 外部 Agent 接入（模式 A：Agent 通过 Langfuse SDK 上报全链路 trace）
-- =====================================================================

CREATE TABLE IF NOT EXISTS eval_agent_connection (
    id              bigint PRIMARY KEY AUTO_INCREMENT,
    space_id        bigint NOT NULL,
    target_id       bigint NOT NULL,
    agent_key       varchar(128) NOT NULL,
    public_key      varchar(128) NOT NULL,
    secret_hash     char(64) NOT NULL,
    status          varchar(32) NOT NULL DEFAULT 'CONNECTED',
    last_seen_at    datetime NULL,
    trace_count     bigint NOT NULL DEFAULT 0,
    created_at      datetime NOT NULL DEFAULT current_timestamp,
    updated_at      datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    CONSTRAINT fk_eval_agent_conn_space FOREIGN KEY (space_id) REFERENCES space(id),
    CONSTRAINT fk_eval_agent_conn_target FOREIGN KEY (target_id) REFERENCES eval_target(id) ON DELETE CASCADE,
    CONSTRAINT uk_eval_agent_conn_target UNIQUE (target_id),
    CONSTRAINT uk_eval_agent_conn_public UNIQUE (public_key),
    CONSTRAINT uk_eval_agent_conn_agent_key UNIQUE (space_id, agent_key),
    INDEX idx_eval_agent_conn_space_status (space_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
