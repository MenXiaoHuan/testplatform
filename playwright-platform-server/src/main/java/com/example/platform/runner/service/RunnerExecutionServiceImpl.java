package com.example.platform.runner.service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Service;

/**
 * 运行器执行服务实现 —— 将高级阶段操作适配到配置的命令执行器。
 *
 * <p>核心职责：
 * <ul>
 *   <li>将 installDependencies、runTests、generateReport 统一委托给命令执行器</li>
 *   <li>通过 runStage 方法支持带超时和取消控制的任意命令执行</li>
 *   <li>编排层与执行后端解耦，支持本地/Docker 两种模式无缝切换</li>
 * </ul>
 *
 * <p>依赖：{@link RunnerCommandExecutor}
 */
@Service
public class RunnerExecutionServiceImpl implements RunnerExecutionService {
    /** 默认阶段超时时间：1 小时 */
    private static final Duration DEFAULT_STAGE_TIMEOUT = Duration.ofHours(1);

    private final RunnerCommandExecutor commandExecutor;

    public RunnerExecutionServiceImpl(RunnerCommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    /**
     * 执行依赖安装命令。
     */
    @Override
    public int installDependencies(Path workingDirectory, String installCommand, Map<String, String> extraEnv) {
        return runCommand(workingDirectory, installCommand, extraEnv);
    }

    /**
     * 执行测试运行命令。
     */
    @Override
    public int runTests(Path workingDirectory, String runCommand, Map<String, String> extraEnv) {
        return runCommand(workingDirectory, runCommand, extraEnv);
    }

    /**
     * 执行报告生成命令。
     */
    @Override
    public int generateReport(Path workingDirectory, String reportCommand, Map<String, String> extraEnv) {
        return runCommand(workingDirectory, reportCommand, extraEnv);
    }

    /**
     * 执行指定阶段的命令，支持超时和取消控制。
     */
    @Override
    public RunnerCommandResult runStage(
            Path workspaceRoot,
            Path workingDirectory,
            String stageName,
            String command,
            Map<String, String> extraEnv,
            Duration timeout,
            BooleanSupplier cancellationRequested) {
        return commandExecutor.execute(new RunnerCommandRequest(
                workspaceRoot,
                workingDirectory,
                stageName,
                command,
                extraEnv,
                timeout,
                cancellationRequested));
    }

    /**
     * 执行简单命令（旧版兼容），使用默认超时，不支持取消。
     *
     * @param workingDirectory 工作目录
     * @param command          命令
     * @param extraEnv         额外环境变量
     * @return 命令退出码
     */
    private int runCommand(Path workingDirectory, String command, Map<String, String> extraEnv) {
        return runStage(workingDirectory, workingDirectory, "LEGACY", command, extraEnv, DEFAULT_STAGE_TIMEOUT, () -> false).exitCode();
    }
}