package com.example.platform.auth.model;

public record AuthUser(
        Long id,
        String username,
        String nickname,
        String passwordHash,
        String avatarObjectKey,
        boolean enabled) {
}
