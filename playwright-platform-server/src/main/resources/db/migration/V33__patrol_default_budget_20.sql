-- 巡页默认时长预算由 15 分钟调整为 20 分钟（仅改列默认值，不影响已有数据）。
ALTER TABLE patrol_scene ALTER COLUMN budget_duration_minutes SET DEFAULT 20;
ALTER TABLE patrol_task ALTER COLUMN budget_duration_minutes SET DEFAULT 20;
