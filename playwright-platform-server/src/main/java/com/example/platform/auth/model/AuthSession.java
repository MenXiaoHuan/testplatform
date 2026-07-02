package com.example.platform.auth.model;

import java.time.LocalDateTime;

public record AuthSession(
        String sessionId,
        Long userId,
        String username,
        String nickname,
        String avatarObjectKey,
        Long lastSpaceId,
        LocalDateTime expiresAt) {
}
