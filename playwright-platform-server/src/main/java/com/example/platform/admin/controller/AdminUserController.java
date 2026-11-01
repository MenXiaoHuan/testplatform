package com.example.platform.admin.controller;

import com.example.platform.admin.service.AdminUserService;
import com.example.platform.auth.context.AuthContext;
import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.common.ApiResponse;
import com.example.platform.storage.service.ObjectStorageService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 平台用户管理接口，仅超级管理员可用。 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final ObjectStorageService objectStorageService;
    private final String storageBucket;

    public AdminUserController(
            AdminUserService adminUserService,
            ObjectStorageService objectStorageService,
            @Value("${platform.storage.bucket:qa-report}") String storageBucket) {
        this.adminUserService = adminUserService;
        this.objectStorageService = objectStorageService;
        this.storageBucket = storageBucket;
    }

    @GetMapping
    public ApiResponse<AdminUserPageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        requireSuperAdmin();
        AdminUserService.UserPage result = adminUserService.list(keyword, page, size);
        return ApiResponse.ok(new AdminUserPageResponse(
                result.page(),
                result.size(),
                result.total(),
                result.items().stream().map(this::toResponse).toList()));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminUserResponse> detail(@PathVariable Long id) {
        requireSuperAdmin();
        return ApiResponse.ok(toResponse(adminUserService.detail(id)));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminUserResponse> update(
            @PathVariable Long id,
            @RequestBody UpdateAdminUserRequest request) {
        AuthContext actor = requireSuperAdmin();
        return ApiResponse.ok(toResponse(adminUserService.update(
                id,
                request.nickname(),
                request.enabled() == null || request.enabled(),
                actor.userId(),
                operatorName(actor))));
    }

    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(
            @PathVariable Long id,
            @RequestBody ResetPasswordRequest request) {
        AuthContext actor = requireSuperAdmin();
        adminUserService.resetPassword(id, request.encryptedPassword(), actor.userId(), operatorName(actor));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        AuthContext actor = requireSuperAdmin();
        adminUserService.delete(id, actor.userId(), operatorName(actor));
        return ApiResponse.ok(null);
    }

    private AuthContext requireSuperAdmin() {
        AuthContext context = AuthContextHolder.get();
        if (context == null || !context.superAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "super admin required");
        }
        return context;
    }

    private String operatorName(AuthContext context) {
        return context.nickname() == null || context.nickname().isBlank()
                ? context.username()
                : context.nickname();
    }

    private AdminUserResponse toResponse(AdminUserService.AdminUserView user) {
        return new AdminUserResponse(
                user.id(),
                user.username(),
                user.nickname(),
                resolveAvatarUrl(user.avatarObjectKey()),
                user.enabled(),
                user.superAdmin(),
                user.lastSpaceId(),
                user.createdAt(),
                user.updatedAt());
    }

    private String resolveAvatarUrl(String avatarObjectKey) {
        if (avatarObjectKey == null || avatarObjectKey.isBlank()) {
            return null;
        }
        try {
            return objectStorageService.createPresignedGetUrl(storageBucket, avatarObjectKey);
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    public record UpdateAdminUserRequest(String nickname, Boolean enabled) {
    }

    public record ResetPasswordRequest(String encryptedPassword) {
    }

    public record AdminUserResponse(
            Long id,
            String username,
            String nickname,
            String avatarUrl,
            boolean enabled,
            boolean superAdmin,
            Long lastSpaceId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record AdminUserPageResponse(
            int page,
            int size,
            long total,
            List<AdminUserResponse> items) {
    }
}
