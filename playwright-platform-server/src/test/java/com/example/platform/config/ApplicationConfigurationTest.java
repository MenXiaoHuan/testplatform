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
}
