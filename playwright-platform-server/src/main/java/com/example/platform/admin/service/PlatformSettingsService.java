package com.example.platform.admin.service;

/** 平台级运行时设置（目前只包含注册开关）。 */
public interface PlatformSettingsService {

    /** 是否允许自助注册；DB 未写入时回退到配置文件默认值。 */
    boolean isRegistrationEnabled();

    /** 更新注册开关并持久化到 DB，同时记录审计日志。 */
    void setRegistrationEnabled(boolean enabled, Long operatorId, String operatorName);
}
