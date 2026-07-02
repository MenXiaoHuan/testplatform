package com.example.platform.auth.service;

import com.example.platform.auth.config.AuthProperties;
import com.example.platform.auth.crypto.AuthKeyProvider;
import com.example.platform.auth.model.AuthSession;
import com.example.platform.auth.model.AuthUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {
    private final AuthProperties authProperties;
    private final AuthKeyProvider keyProvider;
    private final Map<String, AuthUser> usersByUsername;
    private final Map<String, AuthSession> sessionsById = new ConcurrentHashMap<>();
    private final Supplier<LocalDateTime> nowSupplier;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthServiceImpl(AuthProperties authProperties, AuthKeyProvider keyProvider) {
        this(authProperties, keyProvider, authProperties.getUsers().stream()
                .map(user -> new AuthUser(
                        user.getId(),
                        user.getUsername(),
                        user.getNickname(),
                        user.getPasswordHash(),
                        user.getAvatarObjectKey(),
                        user.isEnabled()))
                .toList(), LocalDateTime::now);
    }

    public AuthServiceImpl(
            AuthProperties authProperties,
            AuthKeyProvider keyProvider,
            List<AuthUser> users,
            Supplier<LocalDateTime> nowSupplier) {
        this.authProperties = authProperties;
        this.keyProvider = keyProvider;
        this.usersByUsername = users.stream().collect(Collectors.toMap(AuthUser::username, user -> user));
        this.nowSupplier = nowSupplier;
    }

    @Override
    public LoginResult login(String username, String encryptedPassword) {
        AuthUser user = Optional.ofNullable(usersByUsername.get(username))
                .filter(AuthUser::enabled)
                .orElseThrow(() -> new IllegalArgumentException("invalid credentials"));
        String rawPassword = keyProvider.decrypt(encryptedPassword);
        if (!passwordEncoder.matches(rawPassword, user.passwordHash())) {
            throw new IllegalArgumentException("invalid credentials");
        }

        String sessionId = UUID.randomUUID().toString();
        AuthSession session = new AuthSession(
                sessionId,
                user.id(),
                user.username(),
                user.nickname(),
                user.avatarObjectKey(),
                null,
                nowSupplier.get().plusDays(authProperties.getSlidingDays()));
        sessionsById.put(sessionId, session);
        return new LoginResult(
                session.sessionId(),
                session.userId(),
                session.username(),
                session.nickname(),
                session.avatarObjectKey(),
                session.lastSpaceId());
    }

    @Override
    public Optional<AuthSession> findSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        AuthSession session = sessionsById.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expiresAt().isBefore(nowSupplier.get())) {
            sessionsById.remove(sessionId);
            return Optional.empty();
        }
        AuthSession refreshed = new AuthSession(
                session.sessionId(),
                session.userId(),
                session.username(),
                session.nickname(),
                session.avatarObjectKey(),
                session.lastSpaceId(),
                nowSupplier.get().plusDays(authProperties.getSlidingDays()));
        sessionsById.put(sessionId, refreshed);
        return Optional.of(refreshed);
    }

    @Override
    public Optional<LoginUser> currentUser(String sessionId) {
        return findSession(sessionId)
                .map(session -> new LoginUser(
                        session.userId(),
                        session.username(),
                        session.nickname(),
                        session.avatarObjectKey(),
                        session.lastSpaceId()));
    }

    @Override
    public void logout(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sessionsById.remove(sessionId);
    }
}
