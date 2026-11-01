-- =====================================================================
-- V15 内置评测集支持
-- builtin_key 在同一空间内唯一；is_builtin=1 的内置评测集不允许删除。
-- =====================================================================

ALTER TABLE eval_dataset
    ADD COLUMN builtin_key varchar(64) NULL AFTER external_source,
    ADD COLUMN is_builtin tinyint NOT NULL DEFAULT 0 AFTER builtin_key,
    ADD UNIQUE KEY uk_eval_dataset_builtin_key (space_id, builtin_key);
