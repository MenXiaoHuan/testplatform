package com.example.platform.admin.controller;

import com.example.platform.admin.service.PlatformSettingsService;
import com.example.platform.auth.context.AuthContext;
import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 平台运行时设置接口，仅超级管理员可用。 */
@RestController
@RequestMapping("/api/admin/registration-setting")
public class AdminSettingController {

    private final PlatformSettingsService platformSettingsService;

    public AdminSettingController(PlatformSettingsService platformSettingsService) {
        this.platformSettingsService = platformSettingsService;
    }

    @GetMapping
    public ApiResponse<RegistrationSettingResponse> get() {
        requireSuperAdmin();
        return ApiResponse.ok(new RegistrationSettingResponse(platformSettingsService.isRegistrationEnabled()));
    }

    @PutMapping
    public ApiResponse<RegistrationSettingResponse> update(@RequestBody UpdateRegistrationSettingRequest request) {
        AuthContext actor = requireSuperAdmin();
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        platformSettingsService.setRegistrationEnabled(enabled, actor.userId(), operatorName(actor));
        return ApiResponse.ok(new RegistrationSettingResponse(platformSettingsService.isRegistrationEnabled()));
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

    public record UpdateRegistrationSettingRequest(Boolean enabled) {
    }

    public record RegistrationSettingResponse(boolean enabled) {
    }
}
