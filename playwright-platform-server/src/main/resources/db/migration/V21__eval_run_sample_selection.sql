-- 记录离线任务启动时用户指定的用例范围，重跑时按原范围覆盖执行，而不是重跑评测集全集。
-- NULL 表示「全部用例」。
ALTER TABLE eval_run
    ADD COLUMN sample_ids json NULL COMMENT '本次任务指定的用例 ID 列表；NULL 表示评测集全部用例';
