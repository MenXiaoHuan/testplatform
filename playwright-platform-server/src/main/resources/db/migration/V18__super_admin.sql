-- 平台级超管：现有 admin 账号升级为唯一超管（不提供授予其他用户的界面）
alter table platform_user add column is_super_admin tinyint(1) not null default 0 after enabled;
update platform_user set is_super_admin = 1 where username = 'admin';
