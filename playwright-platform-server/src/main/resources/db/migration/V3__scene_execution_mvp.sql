alter table scene
    add column description varchar(512) null after name,
    add column schedule_enabled tinyint not null default 0 after enabled,
    add column cron_expression varchar(64) null after schedule_enabled,
    add column last_run_at datetime null after cron_expression,
    add column last_task_status varchar(32) null after last_run_at;
