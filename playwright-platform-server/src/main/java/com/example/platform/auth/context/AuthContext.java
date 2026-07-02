package com.example.platform.auth.context;

public record AuthContext(
        Long userId,
        String username,
        String nickname,
        String avatarObjectKey,
        Long lastSpaceId) {
}
