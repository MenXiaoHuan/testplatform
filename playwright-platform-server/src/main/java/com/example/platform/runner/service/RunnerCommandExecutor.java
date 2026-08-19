package com.example.platform.runner.service;

/**
 * 命令执行器接口 —— 定义任务阶段命令的执行契约。
 *
 * <p>核心职责：
 * <ul>
 *   <li>提供统一的命令执行入口，屏蔽本地/Docker 两种实现差异</li>
 *   <li>通过策略模式，允许运行时选择不同的执行后端</li>
 * </ul>
 *
 * <p>实现类：{@link LocalRunnerCommandExecutor}、{@link DockerRunnerCommandExecutor}
 */
public interface RunnerCommandExecutor {
    /**
     * 执行命令。
     *
     * @param request 执行请求，包含工作目录、命令、环境变量、超时等信息
     * @return 执行结果
     */
    RunnerCommandResult execute(RunnerCommandRequest request);
}