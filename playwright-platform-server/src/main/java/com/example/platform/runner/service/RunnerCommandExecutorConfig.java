package com.example.platform.runner.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({RunnerProperties.class, DockerRunnerProperties.class})
public class RunnerCommandExecutorConfig {
    @Bean
    public RunnerCommandExecutor runnerCommandExecutor(
            RunnerProperties runnerProperties,
            DockerRunnerProperties dockerRunnerProperties) {
        if (runnerProperties.getMode() == RunnerMode.DOCKER) {
            return new DockerRunnerCommandExecutor(
                    dockerRunnerProperties,
                    runnerProperties,
                    new DockerCommandBuilder(dockerRunnerProperties, runnerProperties),
                    new DockerContainerNameFactory());
        }
        return new LocalRunnerCommandExecutor();
    }
}
