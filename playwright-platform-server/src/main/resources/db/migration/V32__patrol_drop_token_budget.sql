-- 移除任务级 Token 预算：场景不再限制单次执行的 Token 消耗，仅保留时长预算与统计字段
ALTER TABLE patrol_scene DROP COLUMN budget_llm_tokens;
ALTER TABLE patrol_task DROP COLUMN budget_llm_tokens;
