-- 保留规划阶段的降级/停止原因，避免审批或恢复执行后丢失
ALTER TABLE patrol_task
ADD COLUMN planning_note varchar(1024) NULL AFTER task_brief;
