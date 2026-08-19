package com.example.platform.auth.controller;

import com.example.platform.auth.config.AuthProperties;
import com.example.platform.auth.crypto.AuthKeyProvider;
import com.example.platform.auth.service.AuthService;
import com.example.platform.common.ApiResponse;
import com.example.platform.storage.service.ObjectStorageService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private final AuthService authService;
    private final AuthKeyProvider authKeyProvider;
    private final AuthProperties authProperties;
    private final ObjectStorageService objectStorageService;
    private final String storageBucket;

    public AuthController(
            AuthService authService,
            AuthKeyProvider authKeyProvider,
            AuthProperties authProperties,
            ObjectStorageService objectStorageService,
            @Value("${platform.storage.bucket:qa-report}") String storageBucket) {
        this.authService = authService;
        this.authKeyProvider = authKeyProvider;
        this.authProperties = authProperties;
        this.objectStorageService = objectStorageService;
        this.storageBucket = storageBucket;
    }

    @GetMapping("/public-key")
    public ApiResponse<PublicKeyResponse> publicKey() {
        return ApiResponse.ok(new PublicKeyResponse("RSA", authKeyProvider.publicKeyPem()));
    }

    @PostMapping("/login")
    public ApiResponse<LoginUserResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request.username(), request.encryptedPassword());
        writeSessionCookie(response, result.sessionId());
        return ApiResponse.ok(toLoginUserResponse(result));
    }

    @PostMapping("/register")
    public ApiResponse<LoginUserResponse> register(@RequestBody RegisterRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = authService.register(request.username(), request.encryptedPassword());
        writeSessionCookie(response, result.sessionId());
        return ApiResponse.ok(toLoginUserResponse(result));
    }

    @PostMapping("/setup-profile")
    public ApiResponse<LoginUserResponse> setupProfile(@RequestBody SetupProfileRequest request, HttpServletRequest httpServletRequest) {
        AuthService.LoginUser user = authService.setupProfile(readSessionId(httpServletRequest), request.nickname());
        return ApiResponse.ok(toLoginUserResponse(user));
    }

    private void writeSessionCookie(HttpServletResponse response, String sessionId) {
        Cookie cookie = new Cookie(authProperties.getCookieName(), sessionId);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(authProperties.getSlidingDays() * 24 * 60 * 60);
        response.addCookie(cookie);
    }

    @GetMapping("/me")
    public ApiResponse<LoginUserResponse> me(HttpServletRequest request) {
        AuthService.LoginUser currentUser = authService.currentUser(readSessionId(request))
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "login required"));
        return ApiResponse.ok(toLoginUserResponse(currentUser));
    }

    @PutMapping("/profile")
    public ApiResponse<LoginUserResponse> updateProfile(
            @RequestBody UpdateProfileRequest request,
            HttpServletRequest servletRequest) {
        AuthService.LoginUser currentUser = authService.updateNickname(readSessionId(servletRequest), request.nickname())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "login required"));
        return ApiResponse.ok(toLoginUserResponse(currentUser));
    }

    @PostMapping("/avatar")
    public ApiResponse<LoginUserResponse> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        AuthService.LoginUser currentUser = authService.currentUser(readSessionId(request))
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "login required"));
        validateAvatar(file);
        String objectKey = buildAvatarObjectKey(currentUser.id(), file.getContentType());
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("platform-avatar-", resolveExtension(file.getContentType()));
            file.transferTo(tempFile);
            objectStorageService.uploadFile(storageBucket, objectKey, tempFile);
            AuthService.LoginUser updatedUser = authService.updateAvatar(readSessionId(request), objectKey)
                    .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "login required"));
            return ApiResponse.ok(toLoginUserResponse(updatedUser));
        } catch (IOException exception) {
            throw new IllegalStateException("avatar upload failed", exception);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(readSessionId(request));
        Cookie cookie = new Cookie(authProperties.getCookieName(), "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ApiResponse.ok(null);
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String encryptedPassword) {
    }

    public record RegisterRequest(
            @NotBlank String username,
            @NotBlank String encryptedPassword) {
    }

    public record SetupProfileRequest(
            @NotBlank String nickname) {
    }

    public record LoginUserResponse(
            Long id,
            String username,
            String nickname,
            String avatarObjectKey,
            Long lastSpaceId,
            boolean needsSetup) {
    }

    public record UpdateProfileRequest(String nickname) {
    }

    public record PublicKeyResponse(
            String algorithm,
            String publicKeyPem) {
    }

    private String readSessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (authProperties.getCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
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

    private LoginUserResponse toLoginUserResponse(AuthService.LoginUser currentUser) {
        return new LoginUserResponse(
                currentUser.id(),
                currentUser.username(),
                currentUser.nickname(),
                resolveAvatarUrl(currentUser.avatarObjectKey()),
                currentUser.lastSpaceId(),
                currentUser.needsSetup());
    }

    private LoginUserResponse toLoginUserResponse(AuthService.LoginResult result) {
        return new LoginUserResponse(
                result.userId(),
                result.username(),
                result.nickname(),
                resolveAvatarUrl(result.avatarObjectKey()),
                result.lastSpaceId(),
                result.needsSetup());
    }

    private void validateAvatar(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "请选择头像文件");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new ResponseStatusException(BAD_REQUEST, "头像文件不能超过 2MB");
        }
        if (!ALLOWED_AVATAR_TYPES.contains(file.getContentType())) {
            throw new ResponseStatusException(BAD_REQUEST, "仅支持 PNG、JPG、WEBP 图片");
        }
    }

    private String buildAvatarObjectKey(Long userId, String contentType) {
        return "avatars/users/%d/%s%s".formatted(userId, UUID.randomUUID(), resolveExtension(contentType));
    }

    private String resolveExtension(String contentType) {
        if ("image/jpeg".equals(contentType)) {
            return ".jpg";
        }
        if ("image/webp".equals(contentType)) {
            return ".webp";
        }
        return ".png";
    }
}
