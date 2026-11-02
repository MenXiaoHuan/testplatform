-- 巡页模型日志：记录各阶段模型上下文、完整原始输出与调用状态
CREATE TABLE patrol_model_log (
    id bigint PRIMARY KEY AUTO_INCREMENT,
    task_id bigint NOT NULL,
    case_id bigint NULL,
    case_name varchar(200) NULL,
    stage varchar(32) NOT NULL,
    purpose varchar(64) NOT NULL,
    model_id varchar(128) NULL,
    status varchar(16) NOT NULL DEFAULT 'SUCCESS',
    system_prompt longtext NULL,
    user_prompt longtext NULL,
    raw_output longtext NULL,
    prompt_tokens int NOT NULL DEFAULT 0,
    completion_tokens int NOT NULL DEFAULT 0,
    duration_ms bigint NOT NULL DEFAULT 0,
    error_message text NULL,
    created_at datetime NOT NULL DEFAULT current_timestamp,
    KEY idx_patrol_model_log_task (task_id, id),
    KEY idx_patrol_model_log_case (case_id),
    CONSTRAINT fk_patrol_model_log_task FOREIGN KEY (task_id) REFERENCES patrol_task(id) ON DELETE CASCADE,
    CONSTRAINT fk_patrol_model_log_case FOREIGN KEY (case_id) REFERENCES patrol_case(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
