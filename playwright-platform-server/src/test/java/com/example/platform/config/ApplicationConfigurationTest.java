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
        assertThat(applicationYaml).contains("host: ${PLATFORM_REDIS_HOST}");
        assertThat(applicationYaml).contains("access-key: ${PLATFORM_MINIO_ACCESS_KEY}");
        assertThat(applicationYaml).contains("secret-key: ${PLATFORM_MINIO_SECRET_KEY}");
        assertThat(applicationYaml).doesNotContain("host: ${PLATFORM_REDIS_HOST:localhost}");
        assertThat(applicationYaml).doesNotContain(WEAK_NUMERIC_SECRET);
        assertThat(applicationYaml).doesNotContain(WEAK_MINIO_SECRET);
    }

    @Test
    void shouldNotExposeWeakSecretsInDevProfile() throws IOException {
        String devApplicationYaml = Files.readString(Path.of("src/main/resources/application-dev.yml"));

        assertThat(devApplicationYaml).contains("url: ${PLATFORM_DB_URL}");
        assertThat(devApplicationYaml).contains("endpoint: ${PLATFORM_MINIO_ENDPOINT}");
        assertThat(devApplicationYaml).doesNotContain("jdbc:mysql://localhost");
        assertThat(devApplicationYaml).doesNotContain("http://127.0.0.1");
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
        String readme = Files.readString(Path.of("../README.md"));

        assertThat(compose).doesNotContain(WEAK_NUMERIC_SECRET);
        assertThat(compose).doesNotContain(WEAK_MINIO_SECRET);
        assertThat(compose).contains("PLATFORM_REDIS_PASSWORD");
        assertThat(compose).contains("--requirepass");
        assertThat(readme).doesNotContain(WEAK_NUMERIC_SECRET);
        assertThat(readme).doesNotContain(WEAK_MINIO_SECRET);
        assertThat(readme).doesNotContain("jdbc:mysql://localhost");
        assertThat(readme).doesNotContain("http://127.0.0.1");
    }

    @Test
    void shouldExternalizeComposePortsAndServiceEndpoints() throws IOException {
        String compose = Files.readString(Path.of("../docker-compose.yml"));

        assertThat(compose).contains("${PLATFORM_MYSQL_HOST_PORT");
        assertThat(compose).contains("${PLATFORM_MINIO_API_HOST_PORT");
        assertThat(compose).contains("${PLATFORM_MINIO_CONSOLE_HOST_PORT");
        assertThat(compose).contains("${PLATFORM_REDIS_HOST_PORT");
        assertThat(compose).contains("${PLATFORM_SERVER_HOST_PORT");
        assertThat(compose).contains("${PLATFORM_WEB_HOST_PORT");
        assertThat(compose).contains("${PLATFORM_WEB_API_PROXY_TARGET");
        assertThat(compose).contains("${PLATFORM_MINIO_INTERNAL_ENDPOINT");
        assertThat(compose).contains("${PLATFORM_RUNNER_HOST_WORKSPACE_ROOT");
        assertThat(compose).doesNotContain("\"3307:3306\"");
        assertThat(compose).doesNotContain("\"9000:9000\"");
        assertThat(compose).doesNotContain("\"9001:9001\"");
        assertThat(compose).doesNotContain("\"6379:6379\"");
        assertThat(compose).doesNotContain("\"8080:8080\"");
        assertThat(compose).doesNotContain("\"5173:5173\"");
        assertThat(compose).doesNotContain("VITE_API_PROXY_TARGET: http://server:8080");
    }

    @Test
    void shouldProvideProductionComposeForSingleHostDeployment() throws IOException {
        String productionCompose = Files.readString(Path.of("../docker-compose.prod.yml"));

        assertThat(productionCompose).contains("dockerfile: Dockerfile");
        assertThat(productionCompose).doesNotContain("Dockerfile.dev");
        assertThat(productionCompose).contains("SPRING_PROFILES_ACTIVE: prod");
        assertThat(productionCompose).contains("PLATFORM_WEB_HOST_PORT");
        assertThat(productionCompose).contains("PLATFORM_MINIO_API_HOST_PORT");
        assertThat(productionCompose).contains("PLATFORM_MINIO_CONSOLE_HOST_PORT");
        assertThat(productionCompose).contains("/var/run/docker.sock:/var/run/docker.sock");
        assertThat(productionCompose).doesNotContain("PLATFORM_MYSQL_HOST_PORT");
        assertThat(productionCompose).doesNotContain("PLATFORM_REDIS_HOST_PORT");
        assertThat(productionCompose).doesNotContain("PLATFORM_SERVER_HOST_PORT");
        assertThat(productionCompose).doesNotContain(WEAK_NUMERIC_SECRET);
        assertThat(productionCompose).doesNotContain(WEAK_MINIO_SECRET);
    }

    @Test
    void shouldDocumentProductionDeploymentSteps() throws IOException {
        String deploymentGuide = Files.readString(Path.of("../docs/deployment.md"));

        assertThat(deploymentGuide).contains("docker compose -f docker-compose.prod.yml config");
        assertThat(deploymentGuide).contains("docker compose -f docker-compose.prod.yml up -d --build");
        assertThat(deploymentGuide).contains("docker compose -f docker-compose.prod.yml logs -f server");
        assertThat(deploymentGuide).contains("PLATFORM_DB_PASSWORD=<your-db-password>");
        assertThat(deploymentGuide).contains("PLATFORM_REDIS_PASSWORD=<your-redis-password>");
        assertThat(deploymentGuide).contains("PLATFORM_MINIO_SECRET_KEY=<your-minio-secret-key>");
        assertThat(deploymentGuide).contains("80");
        assertThat(deploymentGuide).contains("443");
        assertThat(deploymentGuide).contains("https://test-platform.example.com");
        assertThat(deploymentGuide).contains("https://api.test-platform.example.com");
        assertThat(deploymentGuide).contains("VITE_API_BASE_URL=https://api.test-platform.example.com");
        assertThat(deploymentGuide).contains("CORS");
        assertThat(deploymentGuide).doesNotContain(WEAK_NUMERIC_SECRET);
        assertThat(deploymentGuide).doesNotContain(WEAK_MINIO_SECRET);
    }

    @Test
    void shouldProxyApiRequestsFromProductionWebContainer() throws IOException {
        String nginxConfig = Files.readString(Path.of("../playwright-platform-web/nginx.conf"));

        assertThat(nginxConfig).contains("location /api/");
        assertThat(nginxConfig).contains("proxy_pass http://server:8080;");
        assertThat(nginxConfig).contains("try_files $uri $uri/ /index.html;");
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
