package com.example.platform.admin.service;

import com.example.platform.admin.mapper.PlatformUserAdminMapper;
import com.example.platform.audit.mapper.PlatformAuditLogMapper;
import com.example.platform.audit.model.PlatformAuditLogEntity;
import com.example.platform.auth.crypto.AuthKeyProvider;
import com.example.platform.auth.mapper.UserSessionMapper;
import com.example.platform.auth.model.PlatformUserEntity;
import com.example.platform.common.BusinessException;
import com.example.platform.space.mapper.SpaceAccessRequestMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final int MAX_NICKNAME_LENGTH = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final PlatformUserAdminMapper userMapper;
    private final UserSessionMapper userSessionMapper;
    private final SpaceMemberMapper spaceMemberMapper;
    private final SpaceAccessRequestMapper spaceAccessRequestMapper;
    private final PlatformAuditLogMapper auditLogMapper;
    private final AuthKeyProvider keyProvider;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminUserServiceImpl(
            PlatformUserAdminMapper userMapper,
            UserSessionMapper userSessionMapper,
            SpaceMemberMapper spaceMemberMapper,
            SpaceAccessRequestMapper spaceAccessRequestMapper,
            PlatformAuditLogMapper auditLogMapper,
            AuthKeyProvider keyProvider) {
        this.userMapper = userMapper;
        this.userSessionMapper = userSessionMapper;
        this.spaceMemberMapper = spaceMemberMapper;
        this.spaceAccessRequestMapper = spaceAccessRequestMapper;
        this.auditLogMapper = auditLogMapper;
        this.keyProvider = keyProvider;
    }

    @Override
    public UserPage list(String keyword, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        long total = userMapper.count(normalizedKeyword);
        List<AdminUserView> items = userMapper
                .findPage(normalizedKeyword, (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(this::toView)
                .toList();
        return new UserPage(safePage, safeSize, total, items);
    }

    @Override
    public AdminUserView detail(Long id) {
        return toView(requireUser(id));
    }

    @Override
    @Transactional
    public AdminUserView update(Long id, String nickname, boolean enabled, Long operatorId, String operatorName) {
        PlatformUserEntity user = requireUser(id);
        String normalizedNickname = normalizeNickname(nickname);
        if (Boolean.TRUE.equals(user.getSuperAdmin()) && !enabled) {
            throw new BusinessException("BAD_REQUEST", "不能禁用超级管理员账号");
        }
        if (id.equals(operatorId) && !enabled) {
            throw new BusinessException("BAD_REQUEST", "不能禁用当前登录账号");
        }

        boolean enabledBefore = Boolean.TRUE.equals(user.getEnabled());
        String nicknameBefore = user.getNickname();
        userMapper.updateProfile(id, normalizedNickname, enabled);
        if (enabledBefore && !enabled) {
            userSessionMapper.deleteByUserId(id);
        }

        PlatformAuditLogEntity auditLog = new PlatformAuditLogEntity();
        auditLog.setEntityType("PLATFORM_USER");
        auditLog.setEntityId(id);
        auditLog.setAction("UPDATE");
        auditLog.setOperatorName(normalizeOperatorName(operatorName));
        auditLog.setDetailJson("""
                {"nicknameBefore":"%s","nicknameAfter":"%s","enabledBefore":%s,"enabledAfter":%s}
                """.formatted(
                escapeJson(nicknameBefore),
                escapeJson(normalizedNickname),
                enabledBefore,
                enabled));
        auditLogMapper.insert(auditLog);

        return detail(id);
    }

    @Override
    @Transactional
    public void resetPassword(Long id, String encryptedPassword, Long operatorId, String operatorName) {
        requireUser(id);
        if (encryptedPassword == null || encryptedPassword.isBlank()) {
            throw new BusinessException("BAD_REQUEST", "请输入新密码");
        }
        String rawPassword = keyProvider.decrypt(encryptedPassword);
        validatePassword(rawPassword);
        userMapper.updatePasswordHash(id, passwordEncoder.encode(rawPassword));
        if (!id.equals(operatorId)) {
            userSessionMapper.deleteByUserId(id);
        }

        PlatformAuditLogEntity auditLog = new PlatformAuditLogEntity();
        auditLog.setEntityType("PLATFORM_USER");
        auditLog.setEntityId(id);
        auditLog.setAction("RESET_PASSWORD");
        auditLog.setOperatorName(normalizeOperatorName(operatorName));
        auditLog.setDetailJson("{\"passwordChanged\":true}");
        auditLogMapper.insert(auditLog);
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId, String operatorName) {
        PlatformUserEntity user = requireUser(id);
        if (Boolean.TRUE.equals(user.getSuperAdmin())) {
            throw new BusinessException("BAD_REQUEST", "不能删除超级管理员账号");
        }
        if (id.equals(operatorId)) {
            throw new BusinessException("BAD_REQUEST", "不能删除当前登录账号");
        }

        userSessionMapper.deleteByUserId(id);
        spaceMemberMapper.deleteByUserId(id);
        spaceAccessRequestMapper.deleteByApplicantUserId(id);
        userMapper.deleteById(id);

        PlatformAuditLogEntity auditLog = new PlatformAuditLogEntity();
        auditLog.setEntityType("PLATFORM_USER");
        auditLog.setEntityId(id);
        auditLog.setAction("DELETE");
        auditLog.setOperatorName(normalizeOperatorName(operatorName));
        auditLog.setDetailJson("""
                {"username":"%s"}
                """.formatted(escapeJson(user.getUsername())));
        auditLogMapper.insert(auditLog);
    }

    private PlatformUserEntity requireUser(Long id) {
        return userMapper.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    private AdminUserView toView(PlatformUserEntity user) {
        return new AdminUserView(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarObjectKey(),
                Boolean.TRUE.equals(user.getEnabled()),
                Boolean.TRUE.equals(user.getSuperAdmin()),
                user.getLastSpaceId(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new BusinessException("BAD_REQUEST", "请输入昵称");
        }
        String trimmed = nickname.trim();
        if (trimmed.length() > MAX_NICKNAME_LENGTH) {
            throw new BusinessException("BAD_REQUEST", "昵称不能超过" + MAX_NICKNAME_LENGTH + "个字符");
        }
        return trimmed;
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new BusinessException("INVALID_PASSWORD", "密码至少 8 位，且需包含字母和数字");
        }
        boolean hasLetter = rawPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException("INVALID_PASSWORD", "密码至少 8 位，且需包含字母和数字");
        }
    }

    private String normalizeOperatorName(String operatorName) {
        if (operatorName == null || operatorName.isBlank()) {
            return "super-admin";
        }
        return operatorName.trim();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
