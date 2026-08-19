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
    private static final int MAX_NICKNAME_LENGTH = 10;

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
    public LoginResult register(String username, String encryptedPassword) {
        String normalizedUsername = normalizeRequiredValue(username, "请输入用户名");
        ensureUsernameAvailable(normalizedUsername);

        String rawPassword = keyProvider.decrypt(encryptedPassword);
        validatePassword(rawPassword);

        PlatformUserEntity user = new PlatformUserEntity();
        user.setUsername(normalizedUsername);
        user.setNickname(null);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setAvatarObjectKey(null);
        user.setEnabled(true);
        user.setLastSpaceId(null);
        platformUserMapper.insert(user);

        return createLoginResult(user);
    }

    @Override
    @Transactional
    public LoginUser setupProfile(String sessionId, String nickname) {
        AuthSession session = findSession(sessionId)
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "请先登录"));

        String normalizedNickname = normalizeNickname(nickname);
        ensureNicknameAvailable(normalizedNickname, session.userId());

        platformUserMapper.updateNickname(session.userId(), normalizedNickname);

        SpaceEntity space = createPersonalSpace(session.userId(), normalizedNickname);
        platformUserMapper.updateLastSpaceId(session.userId(), space.getId());

        return new LoginUser(
                session.userId(),
                session.username(),
                normalizedNickname,
                session.avatarObjectKey(),
                space.getId(),
                false);
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
        boolean needsSetup = needsSetup(session.nickname(), session.lastSpaceId());
        return Optional.of(new AuthSession(
                session.sessionId(),
                session.userId(),
                session.username(),
                session.nickname(),
                session.avatarObjectKey(),
                session.lastSpaceId(),
                refreshedExpiresAt,
                needsSetup));
    }

    @Override
    public Optional<LoginUser> currentUser(String sessionId) {
        return findSession(sessionId)
                .map(session -> new LoginUser(
                        session.userId(),
                        session.username(),
                        session.nickname(),
                        session.avatarObjectKey(),
                        session.lastSpaceId(),
                        session.needsSetup()));
    }

    @Override
    public Optional<LoginUser> updateNickname(String sessionId, String nickname) {
        return findSession(sessionId).map(session -> {
            String normalizedNickname = normalizeNickname(nickname);
            ensureNicknameAvailable(normalizedNickname, session.userId());
            platformUserMapper.updateNickname(session.userId(), normalizedNickname);
            return new LoginUser(
                    session.userId(),
                    session.username(),
                    normalizedNickname,
                    session.avatarObjectKey(),
                    session.lastSpaceId(),
                    false);
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
                    session.lastSpaceId(),
                    session.needsSetup());
        });
    }

    @Override
    public void logout(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        userSessionMapper.deleteBySessionId(sessionId);
    }

    private boolean needsSetup(String nickname, Long lastSpaceId) {
        return (nickname == null || nickname.isBlank()) || lastSpaceId == null;
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new BusinessException("BAD_REQUEST", "请输入昵称");
        }
        String trimmed = nickname.trim();
        if (trimmed.length() > MAX_NICKNAME_LENGTH) {
            throw new BusinessException("BAD_REQUEST", "昵称不能超过" + MAX_NICKNAME_LENGTH + "个字符");
        }
        return trimmed;
    }

    private LoginResult createLoginResult(PlatformUserEntity user) {
        String sessionId = UUID.randomUUID().toString();
        LocalDateTime expiresAt = nowSupplier.get().plusDays(authProperties.getSlidingDays());
        UserSessionEntity session = new UserSessionEntity();
        session.setSessionId(sessionId);
        session.setUserId(user.getId());
        session.setExpiresAt(expiresAt);
        userSessionMapper.insert(session);
        boolean needsSetup = needsSetup(user.getNickname(), user.getLastSpaceId());
        return new LoginResult(
                session.getSessionId(),
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarObjectKey(),
                user.getLastSpaceId(),
                needsSetup);
    }

    private SpaceEntity createPersonalSpace(Long userId, String nickname) {
        String spaceName = nickname + "的测试空间";
        ensureSpaceNameAvailable(spaceName);

        SpaceEntity space = new SpaceEntity();
        space.setName(spaceName);
        space.setDescription(null);
        space.setOwnerUserId(userId);
        space.setCreatedBy(userId);
        spaceMapper.insert(space);

        SpaceMemberEntity member = new SpaceMemberEntity();
        member.setSpaceId(space.getId());
        member.setUserId(userId);
        member.setRole("ADMIN");
        member.setStatus("ACTIVE");
        member.setJoinedAt(nowSupplier.get());
        spaceMemberMapper.insert(member);

        return space;
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
            throw new BusinessException("SPACE_NAME_ALREADY_EXISTS", "空间名称冲突，请修改昵称后重试");
        }
    }
}
