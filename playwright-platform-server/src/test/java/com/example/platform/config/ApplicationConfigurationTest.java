package com.example.platform.config;

import com.example.platform.runner.service.RunnerProperties;
import com.example.platform.task.service.TaskExecutionProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

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
    void shouldKeepDevProfileFreeOfDuplicateConfiguration() throws IOException {
        Path devConfig = Path.of("src/main/resources/application-dev.yml");
        if (!Files.exists(devConfig)) {
            // 当前项目采用单一 application.yml；如果以后新增 dev 覆盖，也不能复制公共配置。
            return;
        }
        String devApplicationYaml = Files.readString(devConfig);

        // 公共配置只在 application.yml 维护一份，避免 dev/prod 两套键互相漂移。
        assertThat(devApplicationYaml).doesNotContain("storage:");
        assertThat(devApplicationYaml).doesNotContain("runner:");
        assertThat(devApplicationYaml).doesNotContain("jdbc:mysql://localhost");
        assertThat(devApplicationYaml).doesNotContain("http://127.0.0.1");
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
        assertThat(compose).contains("${PLATFORM_MINIO_PUBLIC_ENDPOINT");
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
    void shouldExposeTaskExecutionDefaultsUnderPlatformPrefix() throws IOException {
        Map<String, Object> properties = loadApplicationProperties();

        assertThat(properties).containsKeys(
                "platform.task.execution.core-pool-size",
                "platform.task.execution.max-pool-size",
                "platform.task.execution.queue-capacity",
                "platform.task.execution.install-timeout-seconds",
                "platform.task.execution.test-timeout-seconds",
                "platform.task.execution.monitor-log-interval-seconds");
        assertThat(properties).doesNotContainKeys(
                "task.execution.core-pool-size",
                "task.execution.max-pool-size");
    }

    @Test
    void shouldBindPlatformConfigurationPrefixes() {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("test", Map.of(
                "platform.task.execution.core-pool-size", "7",
                "platform.runner.workspace-root", "/tmp/ws",
                "platform.runner.host-workspace-root", "/host/ws",
                "platform.runner.git-clone-timeout-seconds", "99")));
        Binder binder = new Binder(ConfigurationPropertySources.from(sources));

        TaskExecutionProperties taskProperties = binder
                .bind("platform.task.execution", Bindable.of(TaskExecutionProperties.class))
                .orElseThrow(() -> new IllegalStateException("task execution properties not bound"));
        RunnerProperties runnerProperties = binder
                .bind("platform.runner", Bindable.of(RunnerProperties.class))
                .orElseThrow(() -> new IllegalStateException("runner properties not bound"));

        assertThat(taskProperties.getCorePoolSize()).isEqualTo(7);
        assertThat(runnerProperties.getWorkspaceRoot()).isEqualTo(Path.of("/tmp/ws"));
        assertThat(runnerProperties.getGitCloneTimeoutSeconds()).isEqualTo(99);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadApplicationProperties() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load(
                "application", new FileSystemResource("src/main/resources/application.yml"));
        return (Map<String, Object>) sources.get(0).getSource();
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
    void shouldExposeRunnerDefaultsUnderPlatformPrefix() throws IOException {
        Map<String, Object> properties = loadApplicationProperties();

        assertThat(properties).containsKeys(
                "platform.runner.workspace-root",
                "platform.runner.host-workspace-root",
                "platform.runner.git-clone-timeout-seconds",
                "platform.runner.docker.image",
                "platform.runner.docker.network",
                "platform.runner.docker.memory",
                "platform.runner.docker.cpus",
                "platform.runner.docker.container-workspace-root");
        assertThat(properties).doesNotContainKey("runner.mode");
        assertThat(Files.readString(Path.of("../docker-compose.yml")))
                .contains("${PLATFORM_RUNNER_HOST_WORKSPACE_ROOT:?Set PLATFORM_RUNNER_HOST_WORKSPACE_ROOT in .env}:${PLATFORM_RUNNER_WORKSPACE_ROOT:?Set PLATFORM_RUNNER_WORKSPACE_ROOT in .env}");
    }
}
