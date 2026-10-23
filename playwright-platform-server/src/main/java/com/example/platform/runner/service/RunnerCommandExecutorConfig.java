package com.example.platform.runner.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 执行器配置 —— 平台只使用 Docker Runner。
 *
 * <p>核心职责：
 * <ul>
 *   <li>创建 Docker 命令执行器 Bean</li>
 *   <li>启用 RunnerProperties 和 DockerRunnerProperties 的配置绑定</li>
 * </ul>
 *
 * <p>Docker Runner 通过挂载的 docker.sock 启动短生命周期容器；server 进程本身
 * 不直接执行用户命令。这样 runner 与平台进程之间有一层容器隔离，也避免了
 * local 模式把宿主机 shell 暴露给平台用户的风险。
 *
 * <p>依赖：{@link DockerRunnerCommandExecutor}
 */
@Configuration
@EnableConfigurationProperties({RunnerProperties.class, DockerRunnerProperties.class})
public class RunnerCommandExecutorConfig {
    /**
     * 创建 Docker 命令执行器 Bean。
     *
     * @param runnerProperties      运行器配置（工作空间路径、超时、日志上限）
     * @param dockerRunnerProperties Docker 运行器配置
     * @return Docker 命令执行器实例
     */
    @Bean
    public RunnerCommandExecutor runnerCommandExecutor(
            RunnerProperties runnerProperties,
            DockerRunnerProperties dockerRunnerProperties) {
        return new DockerRunnerCommandExecutor(
                dockerRunnerProperties,
                runnerProperties,
                new DockerCommandBuilder(dockerRunnerProperties, runnerProperties),
                new DockerContainerNameFactory(),
                runnerProperties.getMaxLogBytes(),
                runnerProperties.getMaxTestLogBytes());
    }
}
