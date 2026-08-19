package com.example.platform.runner.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 执行器配置 —— 根据运行模式选择本地或 Docker 执行器。
 *
 * <p>核心职责：
 * <ul>
 *   <li>根据 platform.runner.mode 配置动态注入对应的执行器实现</li>
 *   <li>启用 RunnerProperties 和 DockerRunnerProperties 的配置绑定</li>
 * </ul>
 *
 * <p>依赖：{@link LocalRunnerCommandExecutor}、{@link DockerRunnerCommandExecutor}
 */
@Configuration
@EnableConfigurationProperties({RunnerProperties.class, DockerRunnerProperties.class})
public class RunnerCommandExecutorConfig {
    /**
     * 根据配置的运行模式创建对应的命令执行器 Bean。
     *
     * @param runnerProperties     运行器配置（包含模式和工作空间路径）
     * @param dockerRunnerProperties Docker 运行器配置
     * @return 命令执行器实例
     */
    @Bean
    public RunnerCommandExecutor runnerCommandExecutor(
            RunnerProperties runnerProperties,
            DockerRunnerProperties dockerRunnerProperties) {
        // Docker 模式：在容器中执行命令
        if (runnerProperties.getMode() == RunnerMode.DOCKER) {
            return new DockerRunnerCommandExecutor(
                    dockerRunnerProperties,
                    runnerProperties,
                    new DockerCommandBuilder(dockerRunnerProperties, runnerProperties),
                    new DockerContainerNameFactory());
        }
        // 默认使用本地执行器
        return new LocalRunnerCommandExecutor();
    }
}