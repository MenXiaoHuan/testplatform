package com.example.platform.auth.controller;

import com.example.platform.auth.config.AuthProperties;
import com.example.platform.auth.crypto.AuthKeyProvider;
import com.example.platform.auth.service.AuthService;
import com.example.platform.common.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthKeyProvider authKeyProvider;
    private final AuthProperties authProperties;

    public AuthController(AuthService authService, AuthKeyProvider authKeyProvider, AuthProperties authProperties) {
        this.authService = authService;
        this.authKeyProvider = authKeyProvider;
        this.authProperties = authProperties;
    }

    @GetMapping("/public-key")
    public ApiResponse<PublicKeyResponse> publicKey() {
        return ApiResponse.ok(new PublicKeyResponse("RSA", authKeyProvider.publicKeyPem()));
    }

    @PostMapping("/login")
    public ApiResponse<LoginUserResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request.username(), request.encryptedPassword());
        Cookie cookie = new Cookie(authProperties.getCookieName(), result.sessionId());
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(authProperties.getSlidingDays() * 24 * 60 * 60);
        response.addCookie(cookie);
        return ApiResponse.ok(new LoginUserResponse(
                result.userId(),
                result.username(),
                result.nickname(),
                result.avatarObjectKey(),
                result.lastSpaceId()));
    }

    @GetMapping("/me")
    public ApiResponse<LoginUserResponse> me(HttpServletRequest request) {
        AuthService.LoginUser currentUser = authService.currentUser(readSessionId(request))
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "login required"));
        return ApiResponse.ok(new LoginUserResponse(
                currentUser.id(),
                currentUser.username(),
                currentUser.nickname(),
                currentUser.avatarObjectKey(),
                currentUser.lastSpaceId()));
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

    public record LoginUserResponse(
            Long id,
            String username,
            String nickname,
            String avatarObjectKey,
            Long lastSpaceId) {
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
}
