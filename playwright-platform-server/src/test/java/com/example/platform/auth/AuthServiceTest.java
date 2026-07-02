package com.example.platform.auth;

import com.example.platform.auth.config.AuthProperties;
import com.example.platform.auth.crypto.AuthKeyProvider;
import com.example.platform.auth.model.AuthSession;
import com.example.platform.auth.model.AuthUser;
import com.example.platform.auth.service.AuthService;
import com.example.platform.auth.service.AuthServiceImpl;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceTest {
    private static final String SECRET_BCRYPT_HASH = "$2a$10$klLc4mpiRtJ2TXtjxrXlN.cgQ2RYYRKPD0cBirSx86XnWTUHPv4aO";

    @Test
    void shouldCreateSessionWhenCredentialsAreValid() throws Exception {
        AuthKeyProvider keyProvider = new AuthKeyProvider();
        AuthService service = new AuthServiceImpl(
                authProperties(),
                keyProvider,
                List.of(new AuthUser(1L, "admin", "平台管理员", SECRET_BCRYPT_HASH, "avatars/admin.png", true)),
                LocalDateTime::now);

        AuthService.LoginResult result = service.login("admin", encrypt(keyProvider.publicKey(), "secret"));

        assertThat(result.username()).isEqualTo("admin");
        assertThat(result.nickname()).isEqualTo("平台管理员");
        assertThat(result.sessionId()).isNotBlank();
        assertThat(result.avatarObjectKey()).isEqualTo("avatars/admin.png");
        assertThat(service.findSession(result.sessionId())).map(AuthSession::username).contains("admin");
        assertThat(service.currentUser(result.sessionId())).map(AuthService.LoginUser::nickname).contains("平台管理员");
    }

    private static AuthProperties authProperties() {
        AuthProperties properties = new AuthProperties();
        properties.setCookieName("platform_session");
        properties.setSlidingDays(14);
        return properties;
    }

    private static String encrypt(PublicKey publicKey, String plaintext) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
    }
}
