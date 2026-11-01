package com.example.platform.admin;

import com.example.platform.admin.mapper.PlatformUserAdminMapper;
import com.example.platform.admin.service.AdminUserService;
import com.example.platform.admin.service.AdminUserServiceImpl;
import com.example.platform.audit.mapper.PlatformAuditLogMapper;
import com.example.platform.audit.model.PlatformAuditLogEntity;
import com.example.platform.auth.crypto.AuthKeyProvider;
import com.example.platform.auth.mapper.UserSessionMapper;
import com.example.platform.auth.model.PlatformUserEntity;
import com.example.platform.common.BusinessException;
import com.example.platform.space.mapper.SpaceAccessRequestMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminUserServiceTest {

    private PlatformUserAdminMapper userMapper;
    private UserSessionMapper userSessionMapper;
    private SpaceMemberMapper spaceMemberMapper;
    private SpaceAccessRequestMapper spaceAccessRequestMapper;
    private PlatformAuditLogMapper auditLogMapper;
    private AuthKeyProvider keyProvider;
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        userMapper = Mockito.mock(PlatformUserAdminMapper.class);
        userSessionMapper = Mockito.mock(UserSessionMapper.class);
        spaceMemberMapper = Mockito.mock(SpaceMemberMapper.class);
        spaceAccessRequestMapper = Mockito.mock(SpaceAccessRequestMapper.class);
        auditLogMapper = Mockito.mock(PlatformAuditLogMapper.class);
        keyProvider = Mockito.mock(AuthKeyProvider.class);
        service = new AdminUserServiceImpl(
                userMapper,
                userSessionMapper,
                spaceMemberMapper,
                spaceAccessRequestMapper,
                auditLogMapper,
                keyProvider);
    }

    @Test
    void shouldReturnPagedUsers() {
        Mockito.when(userMapper.count("张")).thenReturn(1L);
        Mockito.when(userMapper.findPage("张", 0, 10)).thenReturn(List.of(user(2L, "zhangsan", false)));

        AdminUserService.UserPage page = service.list("张", 1, 10);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.username()).isEqualTo("zhangsan");
                    assertThat(item.enabled()).isTrue();
                });
    }

    @Test
    void shouldClampPageArguments() {
        Mockito.when(userMapper.count(Mockito.any())).thenReturn(0L);
        Mockito.when(userMapper.findPage(Mockito.any(), Mockito.eq(0), Mockito.eq(100))
                ).thenReturn(List.of());

        AdminUserService.UserPage page = service.list(null, 0, 1000);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(100);
    }

    @Test
    void shouldRejectDisablingSuperAdmin() {
        Mockito.when(userMapper.findById(1L)).thenReturn(Optional.of(user(1L, "admin", true)));

        assertThatThrownBy(() -> service.update(1L, "平台管理员", false, 1L, "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超级管理员");
    }

    @Test
    void shouldClearSessionsWhenDisablingUser() {
        Mockito.when(userMapper.findById(2L)).thenReturn(Optional.of(user(2L, "zhangsan", false)));

        service.update(2L, "张三", false, 1L, "admin");

        Mockito.verify(userMapper).updateProfile(2L, "张三", false);
        Mockito.verify(userSessionMapper).deleteByUserId(2L);
    }

    @Test
    void shouldWriteAuditLogWhenProfileUpdated() {
        Mockito.when(userMapper.findById(2L)).thenReturn(Optional.of(user(2L, "zhangsan", false)));

        service.update(2L, "张三", true, 1L, "admin");

        ArgumentCaptor<PlatformAuditLogEntity> captor = ArgumentCaptor.forClass(PlatformAuditLogEntity.class);
        Mockito.verify(auditLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getEntityType()).isEqualTo("PLATFORM_USER");
        assertThat(captor.getValue().getAction()).isEqualTo("UPDATE");
    }

    @Test
    void shouldRejectRejectedWeakPassword() {
        Mockito.when(userMapper.findById(2L)).thenReturn(Optional.of(user(2L, "zhangsan", false)));
        Mockito.when(keyProvider.decrypt("cipher")).thenReturn("short");

        assertThatThrownBy(() -> service.resetPassword(2L, "cipher", 1L, "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少 8 位");
    }

    @Test
    void shouldRejectDeletingSuperAdmin() {
        Mockito.when(userMapper.findById(1L)).thenReturn(Optional.of(user(1L, "admin", true)));

        assertThatThrownBy(() -> service.delete(1L, 2L, "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超级管理员");
    }

    @Test
    void shouldRejectDeletingSelf() {
        Mockito.when(userMapper.findById(2L)).thenReturn(Optional.of(user(2L, "zhangsan", false)));

        assertThatThrownBy(() -> service.delete(2L, 2L, "zhangsan"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前登录账号");
    }

    @Test
    void shouldCleanupRelationsWhenDeletingUser() {
        Mockito.when(userMapper.findById(2L)).thenReturn(Optional.of(user(2L, "zhangsan", false)));

        service.delete(2L, 1L, "admin");

        Mockito.verify(userSessionMapper).deleteByUserId(2L);
        Mockito.verify(spaceMemberMapper).deleteByUserId(2L);
        Mockito.verify(spaceAccessRequestMapper).deleteByApplicantUserId(2L);
        Mockito.verify(userMapper).deleteById(2L);
    }

    private static PlatformUserEntity user(Long id, String username, boolean superAdmin) {
        PlatformUserEntity entity = new PlatformUserEntity();
        entity.setId(id);
        entity.setUsername(username);
        entity.setNickname(username);
        entity.setEnabled(true);
        entity.setSuperAdmin(superAdmin);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }
}
