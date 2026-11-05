-- 场景补充（补充入口 Run）：场景配置 + 任务父子关系与前置步骤
ALTER TABLE patrol_scene ADD COLUMN resolve_model_id BIGINT NULL AFTER judge_model_id;
ALTER TABLE patrol_scene ADD COLUMN supplements_json MEDIUMTEXT NULL AFTER task_brief;

ALTER TABLE patrol_task ADD COLUMN parent_task_id BIGINT NULL AFTER scene_id;
ALTER TABLE patrol_task ADD COLUMN entry_type VARCHAR(16) NOT NULL DEFAULT 'MAIN' AFTER parent_task_id;
ALTER TABLE patrol_task ADD COLUMN entry_label VARCHAR(128) NULL AFTER entry_type;
ALTER TABLE patrol_task ADD COLUMN setup_steps_json MEDIUMTEXT NULL AFTER entry_label;
CREATE INDEX idx_patrol_task_parent ON patrol_task (parent_task_id);
