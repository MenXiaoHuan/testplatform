-- 巡页任务支持自定义危险操作关键词名单；为空时 runner 回退内置默认名单
alter table patrol_task
    add column dangerous_keywords varchar(1024) null after dangerous_approved_by;
