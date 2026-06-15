package com.example.platform.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigurationTest {
    @Test
    void shouldUseLocalMysqlDefaultsThatCanAutoCreateSchema() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationYaml).contains("allowPublicKeyRetrieval=true");
        assertThat(applicationYaml).contains("createDatabaseIfNotExist=true");
        assertThat(applicationYaml).contains("${PLATFORM_DB_USERNAME:root}");
        assertThat(applicationYaml).contains("${PLATFORM_DB_PASSWORD:12345678}");
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
