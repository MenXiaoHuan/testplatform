-- 巡页模型提示词配置：空间级覆盖，同一空间同一标签最多一条；未配置时使用内置默认。
CREATE TABLE ai_prompt_config (
    id bigint PRIMARY KEY AUTO_INCREMENT,
    space_id bigint NOT NULL,
    label varchar(16) NOT NULL COMMENT 'PLAN / SUPPLEMENT / JUDGE',
    content mediumtext NOT NULL,
    updated_by varchar(128) NULL,
    created_at datetime NOT NULL DEFAULT current_timestamp,
    updated_at datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    KEY idx_ai_prompt_space (space_id),
    UNIQUE KEY uk_ai_prompt_space_label (space_id, label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
