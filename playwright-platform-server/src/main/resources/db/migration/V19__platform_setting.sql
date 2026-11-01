-- 平台级运行时设置：用于把注册开关等配置从配置文件迁移到数据库，超管可在界面修改。
CREATE TABLE platform_setting (
    setting_key varchar(64) PRIMARY KEY,
    setting_value varchar(512) NOT NULL,
    updated_by bigint NULL,
    updated_at datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
