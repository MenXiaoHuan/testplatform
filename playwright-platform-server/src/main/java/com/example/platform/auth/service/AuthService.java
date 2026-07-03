package com.example.platform.auth.service;

import com.example.platform.auth.model.AuthSession;
import java.util.Optional;

public interface AuthService {
    LoginResult login(String username, String encryptedPassword);

    LoginResult register(String username, String nickname, String encryptedPassword);

    Optional<AuthSession> findSession(String sessionId);

    Optional<LoginUser> currentUser(String sessionId);

    Optional<LoginUser> updateNickname(String sessionId, String nickname);

    Optional<LoginUser> updateAvatar(String sessionId, String avatarObjectKey);

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
