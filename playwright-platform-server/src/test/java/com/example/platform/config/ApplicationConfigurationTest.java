package com.example.platform.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigurationTest {
    @Test
    void shouldNotExposeWeakSecretsInDefaultConfiguration() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationYaml).contains("url: ${PLATFORM_DB_URL}");
        assertThat(applicationYaml).contains("username: ${PLATFORM_DB_USERNAME}");
        assertThat(applicationYaml).contains("password: ${PLATFORM_DB_PASSWORD}");
        assertThat(applicationYaml).contains("access-key: ${PLATFORM_MINIO_ACCESS_KEY}");
        assertThat(applicationYaml).contains("secret-key: ${PLATFORM_MINIO_SECRET_KEY}");
        assertThat(applicationYaml).doesNotContain("12345678");
        assertThat(applicationYaml).doesNotContain("minioadmin");
    }

    @Test
    void shouldKeepLocalDefaultsInDevProfile() throws IOException {
        String devApplicationYaml = Files.readString(Path.of("src/main/resources/application-dev.yml"));

        assertThat(devApplicationYaml).contains("allowPublicKeyRetrieval=true");
        assertThat(devApplicationYaml).contains("createDatabaseIfNotExist=true");
        assertThat(devApplicationYaml).contains("${PLATFORM_DB_USERNAME:root}");
        assertThat(devApplicationYaml).contains("${PLATFORM_DB_PASSWORD:12345678}");
        assertThat(devApplicationYaml).contains("${PLATFORM_MINIO_ACCESS_KEY:minioadmin}");
        assertThat(devApplicationYaml).contains("${PLATFORM_MINIO_SECRET_KEY:minioadmin}");
    }

    @Test
    void shouldExposeTaskExecutionDefaults() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationYaml).contains("platform:");
        assertThat(applicationYaml).contains("task:");
        assertThat(applicationYaml).contains("execution:");
        assertThat(applicationYaml).contains("core-pool-size: 2");
        assertThat(applicationYaml).contains("max-pool-size: 4");
        assertThat(applicationYaml).contains("queue-capacity: 50");
        assertThat(applicationYaml).contains("install-timeout-seconds: 600");
        assertThat(applicationYaml).contains("test-timeout-seconds: 1800");
        assertThat(applicationYaml).contains("monitor-log-interval-seconds: 30");
    }
}
