package com.example.platform.admin.service;

import java.time.LocalDateTime;
import java.util.List;

/** 超管用户管理业务接口（不含权限校验，权限由 Controller 统一校验）。 */
public interface AdminUserService {

    UserPage list(String keyword, int page, int size);

    AdminUserView detail(Long id);

    AdminUserView update(Long id, String nickname, boolean enabled, Long operatorId, String operatorName);

    void resetPassword(Long id, String encryptedPassword, Long operatorId, String operatorName);

    void delete(Long id, Long operatorId, String operatorName);

    record AdminUserView(
            Long id,
            String username,
            String nickname,
            String avatarObjectKey,
            boolean enabled,
            boolean superAdmin,
            Long lastSpaceId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    record UserPage(int page, int size, long total, List<AdminUserView> items) {
    }
}
