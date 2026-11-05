-- 巡页任务：可执行解决建议（JSON 字符串数组），供概览「降级提示」直接展示
ALTER TABLE patrol_task ADD COLUMN advice_json TEXT NULL AFTER result_message;
