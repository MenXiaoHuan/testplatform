package com.example.platform.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigurationTest {
    private static final String WEAK_NUMERIC_SECRET = "1234" + "5678";
    private static final String WEAK_MINIO_SECRET = "minio" + "admin";

    @Test
    void shouldNotExposeWeakSecretsInDefaultConfiguration() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationYaml).contains("url: ${PLATFORM_DB_URL}");
        assertThat(applicationYaml).contains("username: ${PLATFORM_DB_USERNAME}");
        assertThat(applicationYaml).contains("password: ${PLATFORM_DB_PASSWORD}");
        assertThat(applicationYaml).contains("access-key: ${PLATFORM_MINIO_ACCESS_KEY}");
        assertThat(applicationYaml).contains("secret-key: ${PLATFORM_MINIO_SECRET_KEY}");
        assertThat(applicationYaml).doesNotContain(WEAK_NUMERIC_SECRET);
        assertThat(applicationYaml).doesNotContain(WEAK_MINIO_SECRET);
    }

    @Test
    void shouldNotExposeWeakSecretsInDevProfile() throws IOException {
        String devApplicationYaml = Files.readString(Path.of("src/main/resources/application-dev.yml"));

        assertThat(devApplicationYaml).contains("allowPublicKeyRetrieval=true");
        assertThat(devApplicationYaml).contains("createDatabaseIfNotExist=true");
        assertThat(devApplicationYaml).contains("${PLATFORM_DB_USERNAME}");
        assertThat(devApplicationYaml).contains("${PLATFORM_DB_PASSWORD}");
        assertThat(devApplicationYaml).contains("${PLATFORM_MINIO_ACCESS_KEY}");
        assertThat(devApplicationYaml).contains("${PLATFORM_MINIO_SECRET_KEY}");
        assertThat(devApplicationYaml).doesNotContain(WEAK_NUMERIC_SECRET);
        assertThat(devApplicationYaml).doesNotContain(WEAK_MINIO_SECRET);
    }

    @Test
    void shouldNotExposeWeakSecretsInDockerOrDocs() throws IOException {
        String compose = Files.readString(Path.of("../docker-compose.yml"));
        String envExample = Files.readString(Path.of("../.env.example"));
        String readme = Files.readString(Path.of("../README.md"));

        assertThat(compose).doesNotContain(WEAK_NUMERIC_SECRET);
        assertThat(compose).doesNotContain(WEAK_MINIO_SECRET);
        assertThat(compose).contains("PLATFORM_REDIS_PASSWORD");
        assertThat(compose).contains("--requirepass");
        assertThat(envExample).doesNotContain(WEAK_NUMERIC_SECRET);
        assertThat(envExample).doesNotContain(WEAK_MINIO_SECRET);
        assertThat(readme).doesNotContain(WEAK_NUMERIC_SECRET);
        assertThat(readme).doesNotContain(WEAK_MINIO_SECRET);
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
    void shouldExposeTomcatRequestThreadDefaults() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationYaml).contains("server:");
        assertThat(applicationYaml).contains("tomcat:");
        assertThat(applicationYaml).contains("threads:");
        assertThat(applicationYaml).contains("max: ${SERVER_TOMCAT_THREADS_MAX:200}");
        assertThat(applicationYaml).contains("min-spare: ${SERVER_TOMCAT_THREADS_MIN_SPARE:10}");
        assertThat(applicationYaml).contains("accept-count: ${SERVER_TOMCAT_ACCEPT_COUNT:100}");
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
