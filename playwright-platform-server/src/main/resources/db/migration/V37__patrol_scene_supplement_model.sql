-- 巡页场景：补全模型与解析模型、规划模型、判定模型分别独立选择
ALTER TABLE patrol_scene ADD COLUMN supplement_model_id BIGINT NULL AFTER resolve_model_id;
