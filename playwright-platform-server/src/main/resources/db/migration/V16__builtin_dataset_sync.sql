-- =====================================================================
-- V16 内置评测集官方数据同步状态
-- =====================================================================

ALTER TABLE eval_dataset
    ADD COLUMN sync_status varchar(32) NOT NULL DEFAULT 'IDLE' AFTER is_builtin,
    ADD COLUMN last_synced_at datetime NULL AFTER sync_status,
    ADD COLUMN sync_error varchar(1024) NULL AFTER last_synced_at;
