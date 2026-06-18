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

    @Test
    void shouldExposeRunnerDefaults() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationYaml).contains("runner:");
        assertThat(applicationYaml).contains("mode: ${PLATFORM_RUNNER_MODE:local}");
        assertThat(applicationYaml).contains("workspace-root: ${PLATFORM_RUNNER_WORKSPACE_ROOT:${java.io.tmpdir}/playwright-platform/workspaces}");
        assertThat(applicationYaml).contains("host-workspace-root: ${PLATFORM_RUNNER_HOST_WORKSPACE_ROOT:${platform.runner.workspace-root}}");
        assertThat(applicationYaml).contains("image: ${PLATFORM_RUNNER_DOCKER_IMAGE:mcr.microsoft.com/playwright:v1.44.0-jammy}");
        assertThat(applicationYaml).contains("network: ${PLATFORM_RUNNER_DOCKER_NETWORK:bridge}");
        assertThat(applicationYaml).contains("memory: ${PLATFORM_RUNNER_DOCKER_MEMORY:2g}");
        assertThat(applicationYaml).contains("cpus: ${PLATFORM_RUNNER_DOCKER_CPUS:2}");
        assertThat(applicationYaml).contains("container-workspace-root: ${PLATFORM_RUNNER_DOCKER_CONTAINER_WORKSPACE_ROOT:/workspace/task}");
    }
}
