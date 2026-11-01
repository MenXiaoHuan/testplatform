-- =====================================================================
-- V13 合并外部 Agent 类型
-- 统一使用 HTTP_AGENT：Endpoint 为空表示仅通过 Langfuse SDK 接入在线 Trace，
-- Endpoint 非空时同时具备离线评测能力。
-- =====================================================================

UPDATE eval_target
SET target_type = 'HTTP_AGENT',
    updated_at = current_timestamp
WHERE target_type = 'EXTERNAL_AGENT';
