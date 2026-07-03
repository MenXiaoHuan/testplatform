package com.example.platform.auth;

import com.example.platform.auth.config.AuthProperties;
import com.example.platform.auth.crypto.AuthKeyProvider;
import com.example.platform.auth.mapper.PlatformUserMapper;
import com.example.platform.auth.mapper.UserSessionMapper;
import com.example.platform.auth.model.AuthSession;
import com.example.platform.auth.model.PlatformUserEntity;
import com.example.platform.auth.model.UserSessionEntity;
import com.example.platform.auth.service.AuthService;
import com.example.platform.auth.service.AuthServiceImpl;
import com.example.platform.space.mapper.SpaceMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import com.example.platform.space.model.SpaceEntity;
import com.example.platform.space.model.SpaceMemberEntity;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.crypto.Cipher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceTest {
    private static final String SECRET_BCRYPT_HASH = "$2a$10$klLc4mpiRtJ2TXtjxrXlN.cgQ2RYYRKPD0cBirSx86XnWTUHPv4aO";

    @Test
    void shouldCreateSessionWhenCredentialsAreValid() throws Exception {
        AuthKeyProvider keyProvider = new AuthKeyProvider();
        InMemoryPlatformUserMapper userMapper = new InMemoryPlatformUserMapper();
        InMemoryUserSessionMapper sessionMapper = new InMemoryUserSessionMapper();
        InMemorySpaceMapper spaceMapper = new InMemorySpaceMapper();
        InMemorySpaceMemberMapper spaceMemberMapper = new InMemorySpaceMemberMapper();
        sessionMapper.bindUsers(userMapper);
        userMapper.save(user(1L, "admin", "平台管理员"));
        AuthService service = new AuthServiceImpl(
                authProperties(),
                keyProvider,
                userMapper,
                sessionMapper,
                spaceMapper,
                spaceMemberMapper,
                LocalDateTime::now);

        AuthService.LoginResult result = service.login("admin", encrypt(keyProvider.publicKey(), "secret"));

        assertThat(result.username()).isEqualTo("admin");
        assertThat(result.nickname()).isEqualTo("平台管理员");
        assertThat(result.sessionId()).isNotBlank();
        assertThat(result.avatarObjectKey()).isEqualTo("avatars/admin.png");
        assertThat(service.findSession(result.sessionId())).map(AuthSession::username).contains("admin");
        assertThat(service.currentUser(result.sessionId())).map(AuthService.LoginUser::nickname).contains("平台管理员");
    }

    @Test
    void shouldUpdateNicknameForCurrentSession() throws Exception {
        AuthKeyProvider keyProvider = new AuthKeyProvider();
        InMemoryPlatformUserMapper userMapper = new InMemoryPlatformUserMapper();
        InMemoryUserSessionMapper sessionMapper = new InMemoryUserSessionMapper();
        InMemorySpaceMapper spaceMapper = new InMemorySpaceMapper();
        InMemorySpaceMemberMapper spaceMemberMapper = new InMemorySpaceMemberMapper();
        sessionMapper.bindUsers(userMapper);
        userMapper.save(user(1L, "admin", ""));
        AuthService service = new AuthServiceImpl(
                authProperties(),
                keyProvider,
                userMapper,
                sessionMapper,
                spaceMapper,
                spaceMemberMapper,
                LocalDateTime::now);

        AuthService.LoginResult result = service.login("admin", encrypt(keyProvider.publicKey(), "secret"));
        AuthService.LoginUser updated = service.updateNickname(result.sessionId(), "新昵称").orElseThrow();

        assertThat(updated.nickname()).isEqualTo("新昵称");
        assertThat(service.currentUser(result.sessionId())).map(AuthService.LoginUser::nickname).contains("新昵称");
    }

    @Test
    void shouldFallbackToUsernameWhenNicknameIsBlank() throws Exception {
        AuthKeyProvider keyProvider = new AuthKeyProvider();
        InMemoryPlatformUserMapper userMapper = new InMemoryPlatformUserMapper();
        InMemoryUserSessionMapper sessionMapper = new InMemoryUserSessionMapper();
        InMemorySpaceMapper spaceMapper = new InMemorySpaceMapper();
        InMemorySpaceMemberMapper spaceMemberMapper = new InMemorySpaceMemberMapper();
        sessionMapper.bindUsers(userMapper);
        userMapper.save(user(2L, "tester", " "));
        AuthService service = new AuthServiceImpl(
                authProperties(),
                keyProvider,
                userMapper,
                sessionMapper,
                spaceMapper,
                spaceMemberMapper,
                LocalDateTime::now);

        AuthService.LoginResult result = service.login("tester", encrypt(keyProvider.publicKey(), "secret"));

        assertThat(result.nickname()).isEqualTo("tester");
        assertThat(service.currentUser(result.sessionId())).map(AuthService.LoginUser::nickname).contains("tester");
    }

    @Test
    void shouldRegisterUserCreatePersonalSpaceAndLoginImmediately() throws Exception {
        AuthKeyProvider keyProvider = new AuthKeyProvider();
        InMemoryPlatformUserMapper userMapper = new InMemoryPlatformUserMapper();
        InMemoryUserSessionMapper sessionMapper = new InMemoryUserSessionMapper();
        InMemorySpaceMapper spaceMapper = new InMemorySpaceMapper();
        InMemorySpaceMemberMapper spaceMemberMapper = new InMemorySpaceMemberMapper();
        sessionMapper.bindUsers(userMapper);
        AuthService service = new AuthServiceImpl(
                authProperties(),
                keyProvider,
                userMapper,
                sessionMapper,
                spaceMapper,
                spaceMemberMapper,
                LocalDateTime::now);

        AuthService.LoginResult result = service.register("zhangsan", "张三", encrypt(keyProvider.publicKey(), "secret123"));

        assertThat(result.username()).isEqualTo("zhangsan");
        assertThat(result.nickname()).isEqualTo("张三");
        assertThat(result.lastSpaceId()).isNotNull();
        assertThat(userMapper.findByUsername("zhangsan")).map(PlatformUserEntity::getLastSpaceId).contains(result.lastSpaceId());
        assertThat(spaceMapper.findById(result.lastSpaceId())).map(SpaceEntity::getName).contains("张三");
        assertThat(spaceMemberMapper.findActiveBySpaceIdAndUserId(result.lastSpaceId(), result.userId()))
                .map(SpaceMemberEntity::getRole)
                .contains("ADMIN");
    }

    private static AuthProperties authProperties() {
        AuthProperties properties = new AuthProperties();
        properties.setCookieName("platform_session");
        properties.setSlidingDays(14);
        return properties;
    }

    private static PlatformUserEntity user(Long id, String username, String nickname) {
        PlatformUserEntity entity = new PlatformUserEntity();
        entity.setId(id);
        entity.setUsername(username);
        entity.setNickname(nickname);
        entity.setPasswordHash(SECRET_BCRYPT_HASH);
        entity.setAvatarObjectKey("avatars/admin.png");
        entity.setEnabled(true);
        entity.setLastSpaceId(null);
        return entity;
    }

    private static String encrypt(PublicKey publicKey, String plaintext) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
    }

    private static final class InMemoryPlatformUserMapper implements PlatformUserMapper {
        private final List<PlatformUserEntity> users = new ArrayList<>();

        void save(PlatformUserEntity entity) {
            users.removeIf(item -> item.getId().equals(entity.getId()));
            users.add(entity);
        }

        @Override
        public Optional<PlatformUserEntity> findEnabledByUsername(String username) {
            return users.stream()
                    .filter(item -> item.getUsername().equals(username) && Boolean.TRUE.equals(item.getEnabled()))
                    .findFirst();
        }

        @Override
        public Optional<PlatformUserEntity> findByUsername(String username) {
            return users.stream().filter(item -> item.getUsername().equals(username)).findFirst();
        }

        @Override
        public Optional<PlatformUserEntity> findByNickname(String nickname) {
            return users.stream().filter(item -> nickname.equals(item.getNickname())).findFirst();
        }

        @Override
        public int insert(PlatformUserEntity entity) {
            long nextId = users.stream().mapToLong(item -> item.getId() == null ? 0L : item.getId()).max().orElse(0L) + 1L;
            entity.setId(nextId);
            save(entity);
            return 1;
        }

        @Override
        public Optional<PlatformUserEntity> findById(Long id) {
            return users.stream().filter(item -> item.getId().equals(id)).findFirst();
        }

        @Override
        public int updateNickname(Long id, String nickname) {
            Optional<PlatformUserEntity> user = findById(id);
            if (user.isEmpty()) {
                return 0;
            }
            user.get().setNickname(nickname);
            return 1;
        }

        @Override
        public int updateAvatarObjectKey(Long id, String avatarObjectKey) {
            Optional<PlatformUserEntity> user = findById(id);
            if (user.isEmpty()) {
                return 0;
            }
            user.get().setAvatarObjectKey(avatarObjectKey);
            return 1;
        }

        @Override
        public int updateLastSpaceId(Long id, Long lastSpaceId) {
            Optional<PlatformUserEntity> user = findById(id);
            if (user.isEmpty()) {
                return 0;
            }
            user.get().setLastSpaceId(lastSpaceId);
            return 1;
        }
    }

    private static final class InMemoryUserSessionMapper implements UserSessionMapper {
        private final List<UserSessionEntity> sessions = new ArrayList<>();
        private InMemoryPlatformUserMapper userMapper;

        void bindUsers(InMemoryPlatformUserMapper userMapper) {
            this.userMapper = userMapper;
        }

        @Override
        public int insert(UserSessionEntity entity) {
            sessions.removeIf(item -> item.getSessionId().equals(entity.getSessionId()));
            sessions.add(entity);
            return 1;
        }

        @Override
        public Optional<AuthSession> findAuthSessionBySessionId(String sessionId) {
            if (userMapper == null) {
                return Optional.empty();
            }
            return sessions.stream()
                    .filter(item -> item.getSessionId().equals(sessionId))
                    .findFirst()
                    .flatMap(item -> userMapper.findById(item.getUserId())
                            .map(user -> new AuthSession(
                                    item.getSessionId(),
                                    user.getId(),
                                    user.getUsername(),
                                    user.getNickname(),
                                    user.getAvatarObjectKey(),
                                    user.getLastSpaceId(),
                                    item.getExpiresAt())));
        }

        @Override
        public int updateExpiresAt(String sessionId, LocalDateTime expiresAt) {
            Optional<UserSessionEntity> session = sessions.stream()
                    .filter(item -> item.getSessionId().equals(sessionId))
                    .findFirst();
            if (session.isEmpty()) {
                return 0;
            }
            session.get().setExpiresAt(expiresAt);
            return 1;
        }

        @Override
        public int deleteBySessionId(String sessionId) {
            return sessions.removeIf(item -> item.getSessionId().equals(sessionId)) ? 1 : 0;
        }
    }

    private static final class InMemorySpaceMapper implements SpaceMapper {
        private final List<SpaceEntity> spaces = new ArrayList<>();

        @Override
        public int insert(SpaceEntity entity) {
            long nextId = spaces.stream().mapToLong(item -> item.getId() == null ? 0L : item.getId()).max().orElse(0L) + 1L;
            entity.setId(nextId);
            spaces.add(entity);
            return 1;
        }

        @Override
        public Optional<SpaceEntity> findByName(String name) {
            return spaces.stream().filter(item -> item.getName().equals(name)).findFirst();
        }

        @Override
        public Optional<SpaceEntity> findById(Long id) {
            return spaces.stream().filter(item -> item.getId().equals(id)).findFirst();
        }

        @Override
        public List<SpaceEntity> findAll() { return List.copyOf(spaces); }

        @Override
        public List<SpaceEntity> findByUserId(Long userId) { return List.of(); }

        @Override
        public int update(SpaceEntity entity) { return 0; }

        @Override
        public int deleteById(Long id) { return 0; }
    }

    private static final class InMemorySpaceMemberMapper implements SpaceMemberMapper {
        private final List<SpaceMemberEntity> members = new ArrayList<>();

        @Override
        public int insert(SpaceMemberEntity entity) {
            members.add(entity);
            return 1;
        }

        @Override
        public Optional<SpaceMemberEntity> findActiveBySpaceIdAndUserId(Long spaceId, Long userId) {
            return members.stream()
                    .filter(item -> item.getSpaceId().equals(spaceId) && item.getUserId().equals(userId) && "ACTIVE".equals(item.getStatus()))
                    .findFirst();
        }

        @Override
        public List<SpaceMemberEntity> findBySpaceId(Long spaceId) { return List.of(); }

        @Override
        public int updateStatus(Long spaceId, Long userId, String status) { return 0; }

        @Override
        public int updateRole(Long spaceId, Long userId, String role) { return 0; }

        @Override
        public int deleteBySpaceId(Long spaceId) { return 0; }
    }
}
