package com.example.platform.admin.service;

import com.example.platform.admin.mapper.PlatformSettingMapper;
import com.example.platform.audit.mapper.PlatformAuditLogMapper;
import com.example.platform.audit.model.PlatformAuditLogEntity;
import com.example.platform.auth.config.AuthProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformSettingsServiceImpl implements PlatformSettingsService {

    static final String REGISTRATION_ENABLED_KEY = "registration_enabled";

    private final PlatformSettingMapper settingMapper;
    private final PlatformAuditLogMapper auditLogMapper;
    private final AuthProperties authProperties;

    public PlatformSettingsServiceImpl(
            PlatformSettingMapper settingMapper,
            PlatformAuditLogMapper auditLogMapper,
            AuthProperties authProperties) {
        this.settingMapper = settingMapper;
        this.auditLogMapper = auditLogMapper;
        this.authProperties = authProperties;
    }

    @Override
    public boolean isRegistrationEnabled() {
        return settingMapper.findValueByKey(REGISTRATION_ENABLED_KEY)
                .map(value -> "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()))
                .orElseGet(authProperties::isRegistrationEnabled);
    }

    @Override
    @Transactional
    public void setRegistrationEnabled(boolean enabled, Long operatorId, String operatorName) {
        String before = settingMapper.findValueByKey(REGISTRATION_ENABLED_KEY)
                .orElse(Boolean.toString(authProperties.isRegistrationEnabled()));
        settingMapper.upsert(REGISTRATION_ENABLED_KEY, Boolean.toString(enabled), operatorId);

        PlatformAuditLogEntity auditLog = new PlatformAuditLogEntity();
        auditLog.setEntityType("PLATFORM_SETTING");
        auditLog.setEntityId(0L);
        auditLog.setAction("UPDATE");
        auditLog.setOperatorName(normalizeOperatorName(operatorName));
        auditLog.setDetailJson("""
                {"key":"%s","before":"%s","after":"%s"}
                """.formatted(REGISTRATION_ENABLED_KEY, before, enabled));
        auditLogMapper.insert(auditLog);
    }

    private String normalizeOperatorName(String operatorName) {
        if (operatorName == null || operatorName.isBlank()) {
            return "super-admin";
        }
        return operatorName.trim();
    }
}
