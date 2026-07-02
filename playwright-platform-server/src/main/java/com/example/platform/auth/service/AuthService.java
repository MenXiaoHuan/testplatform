package com.example.platform.auth.service;

import com.example.platform.auth.model.AuthSession;
import java.util.Optional;

public interface AuthService {
    LoginResult login(String username, String encryptedPassword);

    Optional<AuthSession> findSession(String sessionId);

    Optional<LoginUser> currentUser(String sessionId);

    void logout(String sessionId);

    record LoginResult(
            String sessionId,
            Long userId,
            String username,
            String nickname,
            String avatarObjectKey,
            Long lastSpaceId) {
    }

    record LoginUser(
            Long id,
            String username,
            String nickname,
            String avatarObjectKey,
            Long lastSpaceId) {
    }
}
