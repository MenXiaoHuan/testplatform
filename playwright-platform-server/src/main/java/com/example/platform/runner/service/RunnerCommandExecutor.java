package com.example.platform.runner.service;

/**
 * 命令执行器接口 —— 定义任务阶段命令的执行契约。
 *
 * <p>平台当前只有一种实现：{@link DockerRunnerCommandExecutor}。
 * 保留接口是为了让编排层不依赖 Docker 细节，也方便未来替换执行后端；
 * 但平台不再提供在宿主机直接执行用户命令的实现。
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
