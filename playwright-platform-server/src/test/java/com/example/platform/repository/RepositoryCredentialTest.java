package com.example.platform.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.common.CredentialCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.service.RepositoryCascadeDeleteService;
import com.example.platform.repository.service.RepositoryServiceImpl;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class RepositoryCredentialTest {
    private static final String TEST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void shouldEncryptPrivateRepositoryTokenAndNeverExposePlaintext() throws Exception {
        TestRepositoryMapper mapper = Mockito.mock(TestRepositoryMapper.class);
        RepositoryCascadeDeleteService cascade = Mockito.mock(RepositoryCascadeDeleteService.class);
        CredentialCipher cipher = new CredentialCipher(TEST_KEY);
        RepositoryServiceImpl service = new RepositoryServiceImpl(mapper, cascade, null, cipher);
        Mockito.when(mapper.insert(Mockito.any())).thenAnswer(invocation -> {
            TestRepositoryEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });

        TestRepositoryEntity payload = repository("https://github.com/org/private.git");
        payload.setAuthType("HTTPS_TOKEN");
        payload.setCredentialUsername("x-access-token");
        payload.setCredentialSecret("ghp_super_secret");

        TestRepositoryEntity result = service.create(payload);

        ArgumentCaptor<TestRepositoryEntity> captor = ArgumentCaptor.forClass(TestRepositoryEntity.class);
        Mockito.verify(mapper).insert(captor.capture());
        String stored = captor.getValue().getCredentialSecret();
        assertThat(stored).startsWith("v1:");
        assertThat(stored).doesNotContain("ghp_super_secret");
        assertThat(cipher.decrypt(stored)).isEqualTo("ghp_super_secret");
        assertThat(result.isCredentialConfigured()).isTrue();
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(result);
        assertThat(json).doesNotContain("ghp_super_secret").doesNotContain("credentialSecret");
    }

    @Test
    void shouldKeepExistingSecretWhenUpdateLeavesSecretBlank() {
        TestRepositoryMapper mapper = Mockito.mock(TestRepositoryMapper.class);
        RepositoryCascadeDeleteService cascade = Mockito.mock(RepositoryCascadeDeleteService.class);
        CredentialCipher cipher = new CredentialCipher(TEST_KEY);
        RepositoryServiceImpl service = new RepositoryServiceImpl(mapper, cascade, null, cipher);
        TestRepositoryEntity existing = repository("https://github.com/org/private.git");
        existing.setId(9L);
        existing.setAuthType("HTTPS_TOKEN");
        existing.setCredentialSecret(cipher.encrypt("old-token"));
        existing.setCredentialUpdatedAt(LocalDateTime.now());
        Mockito.when(mapper.findByIdAndSpaceId(9L, 1L)).thenReturn(Optional.of(existing));

        TestRepositoryEntity payload = repository("https://github.com/org/private.git");
        payload.setAuthType("HTTPS_TOKEN");
        payload.setCredentialSecret("");
        service.update(1L, 9L, payload);

        assertThat(cipher.decrypt(existing.getCredentialSecret())).isEqualTo("old-token");
    }

    @Test
    void shouldRejectSwitchingAuthTypeWithoutNewSecret() {
        TestRepositoryMapper mapper = Mockito.mock(TestRepositoryMapper.class);
        RepositoryCascadeDeleteService cascade = Mockito.mock(RepositoryCascadeDeleteService.class);
        CredentialCipher cipher = new CredentialCipher(TEST_KEY);
        RepositoryServiceImpl service = new RepositoryServiceImpl(mapper, cascade, null, cipher);
        TestRepositoryEntity existing = repository("https://github.com/org/private.git");
        existing.setId(9L);
        existing.setAuthType("HTTPS_TOKEN");
        existing.setCredentialSecret(cipher.encrypt("old-token"));
        Mockito.when(mapper.findByIdAndSpaceId(9L, 1L)).thenReturn(Optional.of(existing));

        TestRepositoryEntity payload = repository("git@github.com:org/private.git");
        payload.setAuthType("SSH_KEY");
        payload.setCredentialSecret("");

        assertThatThrownBy(() -> service.update(1L, 9L, payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("私有仓库需要配置");
    }

    private TestRepositoryEntity repository(String gitUrl) {
        TestRepositoryEntity entity = new TestRepositoryEntity();
        entity.setSpaceId(1L);
        entity.setName("repo");
        entity.setGitUrl(gitUrl);
        entity.setDefaultBranch("main");
        entity.setInstallCommand("npm ci");
        entity.setRunCommandTemplate("npx playwright test");
        entity.setTestRoot("tests");
        entity.setEnabled(true);
        return entity;
    }
}
