-- =====================================================================
-- V14 修正 Prompt 状态唯一约束
-- 原 uk_eval_prompt_active(space_id, prompt_key, status) 会限制同状态只能有 1 条，
-- 导致同一提示词只能有 1 个 DRAFT / DEPRECATED，无法正常创建多版本和自由流转状态。
-- 改为仅对 ACTIVE 做唯一约束：用生成列 active_guard，非 ACTIVE 为 NULL。
-- =====================================================================

ALTER TABLE eval_prompt_version
    DROP INDEX uk_eval_prompt_active;

ALTER TABLE eval_prompt_version
    ADD COLUMN active_guard tinyint
        GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_eval_prompt_active_guard (space_id, prompt_key, active_guard);

CREATE INDEX idx_eval_prompt_status ON eval_prompt_version (space_id, prompt_key, status);
