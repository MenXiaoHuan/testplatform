package com.example.platform.auth.service;

import com.example.platform.auth.config.AuthProperties;
import com.example.platform.auth.crypto.AuthKeyProvider;
import com.example.platform.auth.mapper.PlatformUserMapper;
import com.example.platform.auth.mapper.UserSessionMapper;
import com.example.platform.auth.model.AuthSession;
import com.example.platform.auth.model.PlatformUserEntity;
import com.example.platform.auth.model.UserSessionEntity;
import com.example.platform.common.BusinessException;
import com.example.platform.space.mapper.SpaceMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import com.example.platform.space.model.SpaceEntity;
import com.example.platform.space.model.SpaceMemberEntity;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {
    private final AuthProperties authProperties;
    private final AuthKeyProvider keyProvider;
    private final PlatformUserMapper platformUserMapper;
    private final UserSessionMapper userSessionMapper;
    private final SpaceMapper spaceMapper;
    private final SpaceMemberMapper spaceMemberMapper;
    private final Supplier<LocalDateTime> nowSupplier;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    public AuthServiceImpl(
            AuthProperties authProperties,
            AuthKeyProvider keyProvider,
            PlatformUserMapper platformUserMapper,
            UserSessionMapper userSessionMapper,
            SpaceMapper spaceMapper,
            SpaceMemberMapper spaceMemberMapper) {
        this(authProperties, keyProvider, platformUserMapper, userSessionMapper, spaceMapper, spaceMemberMapper, LocalDateTime::now);
    }

    public AuthServiceImpl(
            AuthProperties authProperties,
            AuthKeyProvider keyProvider,
            PlatformUserMapper platformUserMapper,
            UserSessionMapper userSessionMapper,
            SpaceMapper spaceMapper,
            SpaceMemberMapper spaceMemberMapper,
            Supplier<LocalDateTime> nowSupplier) {
        this.authProperties = authProperties;
        this.keyProvider = keyProvider;
        this.platformUserMapper = platformUserMapper;
        this.userSessionMapper = userSessionMapper;
        this.spaceMapper = spaceMapper;
        this.spaceMemberMapper = spaceMemberMapper;
        this.nowSupplier = nowSupplier;
    }

    @Override
    public LoginResult login(String username, String encryptedPassword) {
        PlatformUserEntity user = platformUserMapper.findEnabledByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("invalid credentials"));
        String rawPassword = keyProvider.decrypt(encryptedPassword);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("invalid credentials");
        }

        return createLoginResult(user);
    }

    @Override
    @Transactional
    public LoginResult register(String username, String nickname, String encryptedPassword) {
        String normalizedUsername = normalizeRequiredValue(username, "请输入用户名");
        String normalizedNickname = normalizeRequiredValue(nickname, "请输入昵称");
        ensureUsernameAvailable(normalizedUsername);
        ensureNicknameAvailable(normalizedNickname, null);

        String rawPassword = keyProvider.decrypt(encryptedPassword);
        validatePassword(rawPassword);

        PlatformUserEntity user = new PlatformUserEntity();
        user.setUsername(normalizedUsername);
        user.setNickname(normalizedNickname);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setAvatarObjectKey(null);
        user.setEnabled(true);
        user.setLastSpaceId(null);
        platformUserMapper.insert(user);

        String spaceName = resolvePersonalSpaceName(user);
        ensureSpaceNameAvailable(spaceName);

        SpaceEntity space = new SpaceEntity();
        space.setName(spaceName);
        space.setDescription(null);
        space.setOwnerUserId(user.getId());
        space.setCreatedBy(user.getId());
        spaceMapper.insert(space);

        SpaceMemberEntity member = new SpaceMemberEntity();
        member.setSpaceId(space.getId());
        member.setUserId(user.getId());
        member.setRole("ADMIN");
        member.setStatus("ACTIVE");
        member.setJoinedAt(nowSupplier.get());
        spaceMemberMapper.insert(member);

        platformUserMapper.updateLastSpaceId(user.getId(), space.getId());
        user.setLastSpaceId(space.getId());
        return createLoginResult(user);
    }

    @Override
    public Optional<AuthSession> findSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        AuthSession session = userSessionMapper.findAuthSessionBySessionId(sessionId).orElse(null);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expiresAt().isBefore(nowSupplier.get())) {
            userSessionMapper.deleteBySessionId(sessionId);
            return Optional.empty();
        }
        LocalDateTime refreshedExpiresAt = nowSupplier.get().plusDays(authProperties.getSlidingDays());
        userSessionMapper.updateExpiresAt(sessionId, refreshedExpiresAt);
        return Optional.of(new AuthSession(
                session.sessionId(),
                session.userId(),
                session.username(),
                resolveNickname(session.nickname(), session.username()),
                session.avatarObjectKey(),
                session.lastSpaceId(),
                refreshedExpiresAt));
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
    public Optional<LoginUser> updateNickname(String sessionId, String nickname) {
        return findSession(sessionId).map(session -> {
            String resolvedNickname = resolveNickname(nickname, session.username());
            ensureNicknameAvailable(resolvedNickname, session.userId());
            platformUserMapper.updateNickname(session.userId(), resolvedNickname);
            return new LoginUser(
                    session.userId(),
                    session.username(),
                    resolvedNickname,
                    session.avatarObjectKey(),
                    session.lastSpaceId());
        });
    }

    @Override
    public Optional<LoginUser> updateAvatar(String sessionId, String avatarObjectKey) {
        return findSession(sessionId).map(session -> {
            platformUserMapper.updateAvatarObjectKey(session.userId(), avatarObjectKey);
            return new LoginUser(
                    session.userId(),
                    session.username(),
                    session.nickname(),
                    avatarObjectKey,
                    session.lastSpaceId());
        });
    }

    @Override
    public void logout(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        userSessionMapper.deleteBySessionId(sessionId);
    }

    private String resolveNickname(String nickname, String username) {
        if (nickname == null || nickname.isBlank()) {
            return username == null || username.isBlank() ? "未命名用户" : username.trim();
        }
        return nickname.trim();
    }

    private LoginResult createLoginResult(PlatformUserEntity user) {
        String sessionId = UUID.randomUUID().toString();
        LocalDateTime expiresAt = nowSupplier.get().plusDays(authProperties.getSlidingDays());
        UserSessionEntity session = new UserSessionEntity();
        session.setSessionId(sessionId);
        session.setUserId(user.getId());
        session.setExpiresAt(expiresAt);
        userSessionMapper.insert(session);
        return new LoginResult(
                session.getSessionId(),
                user.getId(),
                user.getUsername(),
                resolveNickname(user.getNickname(), user.getUsername()),
                user.getAvatarObjectKey(),
                user.getLastSpaceId());
    }

    private String normalizeRequiredValue(String value, String missingMessage) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("BAD_REQUEST", missingMessage);
        }
        return value.trim();
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new BusinessException("INVALID_PASSWORD", "密码至少 8 位，且需包含字母和数字");
        }
        boolean hasLetter = rawPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException("INVALID_PASSWORD", "密码至少 8 位，且需包含字母和数字");
        }
    }

    private void ensureUsernameAvailable(String username) {
        if (platformUserMapper.findByUsername(username).isPresent()) {
            throw new BusinessException("USERNAME_ALREADY_EXISTS", "该用户名已被使用，请换一个");
        }
    }

    private void ensureNicknameAvailable(String nickname, Long currentUserId) {
        platformUserMapper.findByNickname(nickname)
                .filter(user -> currentUserId == null || !user.getId().equals(currentUserId))
                .ifPresent(user -> {
                    throw new BusinessException("NICKNAME_ALREADY_EXISTS", "该昵称已被使用，请换一个");
                });
    }

    private void ensureSpaceNameAvailable(String name) {
        if (spaceMapper.findByName(name).isPresent()) {
            throw new BusinessException("SPACE_NAME_ALREADY_EXISTS", "系统为你生成个人空间时发现名称冲突，请修改昵称后重试");
        }
    }

    private String resolvePersonalSpaceName(PlatformUserEntity user) {
        return resolveNickname(user.getNickname(), user.getUsername());
    }
}
