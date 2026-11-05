-- 移除用例数上限：巡页不再限制单次任务规划/执行的用例数量，仅保留时长预算
ALTER TABLE patrol_scene DROP COLUMN budget_cases;
ALTER TABLE patrol_task DROP COLUMN budget_cases;
