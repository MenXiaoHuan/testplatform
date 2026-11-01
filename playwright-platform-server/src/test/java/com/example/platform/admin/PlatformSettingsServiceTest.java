package com.example.platform.admin;

import com.example.platform.admin.mapper.PlatformSettingMapper;
import com.example.platform.admin.service.PlatformSettingsService;
import com.example.platform.admin.service.PlatformSettingsServiceImpl;
import com.example.platform.audit.mapper.PlatformAuditLogMapper;
import com.example.platform.auth.config.AuthProperties;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformSettingsServiceTest {

    @Test
    void shouldFallbackToConfigValueWhenSettingMissing() {
        PlatformSettingMapper settingMapper = Mockito.mock(PlatformSettingMapper.class);
        Mockito.when(settingMapper.findValueByKey("registration_enabled")).thenReturn(Optional.empty());
        AuthProperties properties = new AuthProperties();
        properties.setRegistrationEnabled(true);

        PlatformSettingsService service = new PlatformSettingsServiceImpl(
                settingMapper, Mockito.mock(PlatformAuditLogMapper.class), properties);

        assertThat(service.isRegistrationEnabled()).isTrue();
    }

    @Test
    void shouldPreferDatabaseValue() {
        PlatformSettingMapper settingMapper = Mockito.mock(PlatformSettingMapper.class);
        Mockito.when(settingMapper.findValueByKey("registration_enabled")).thenReturn(Optional.of("false"));
        AuthProperties properties = new AuthProperties();
        properties.setRegistrationEnabled(true);

        PlatformSettingsService service = new PlatformSettingsServiceImpl(
                settingMapper, Mockito.mock(PlatformAuditLogMapper.class), properties);

        assertThat(service.isRegistrationEnabled()).isFalse();
    }

    @Test
    void shouldPersistSettingAndWriteAuditLog() {
        PlatformSettingMapper settingMapper = Mockito.mock(PlatformSettingMapper.class);
        PlatformAuditLogMapper auditLogMapper = Mockito.mock(PlatformAuditLogMapper.class);
        Mockito.when(settingMapper.findValueByKey("registration_enabled")).thenReturn(Optional.of("true"));

        PlatformSettingsService service = new PlatformSettingsServiceImpl(
                settingMapper, auditLogMapper, new AuthProperties());

        service.setRegistrationEnabled(false, 1L, "admin");

        Mockito.verify(settingMapper).upsert("registration_enabled", "false", 1L);
        Mockito.verify(auditLogMapper).insert(Mockito.argThat(log ->
                "PLATFORM_SETTING".equals(log.getEntityType()) && "UPDATE".equals(log.getAction())));
    }
}
