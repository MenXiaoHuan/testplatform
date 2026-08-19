package com.example.platform.runner.service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * 运行器执行服务接口 —— 定义任务各阶段的执行契约。
 *
 * <p>核心职责：
 * <ul>
 *   <li>提供依赖安装、测试执行、报告生成三个标准阶段入口</li>
 *   <li>支持自定义命令执行（runStage），包含超时和取消控制</li>
 * </ul>
 *
 * <p>实现类：{@link RunnerExecutionServiceImpl}
 */
public interface RunnerExecutionService {
    /**
     * 安装依赖阶段。
     *
     * @param workingDirectory 工作目录
     * @param installCommand   安装命令
     * @param extraEnv          额外环境变量
     * @return 命令退出码
     */
    int installDependencies(Path workingDirectory, String installCommand, Map<String, String> extraEnv);

    /**
     * 运行测试阶段。
     *
     * @param workingDirectory 工作目录
     * @param runCommand       测试命令
     * @param extraEnv          额外环境变量
     * @return 命令退出码
     */
    int runTests(Path workingDirectory, String runCommand, Map<String, String> extraEnv);

    /**
     * 生成报告阶段。
     *
     * @param workingDirectory 工作目录
     * @param reportCommand    报告生成命令
     * @param extraEnv          额外环境变量
     * @return 命令退出码
     */
    int generateReport(Path workingDirectory, String reportCommand, Map<String, String> extraEnv);

    /**
     * 执行自定义阶段命令。
     *
     * @param workspaceRoot         工作空间根路径
     * @param workingDirectory      工作目录
     * @param stageName             阶段名称
     * @param command               要执行的命令
     * @param extraEnv               额外环境变量
     * @param timeout               超时时间
     * @param cancellationRequested 取消信号
     * @return 执行结果
     */
    RunnerCommandResult runStage(
            Path workspaceRoot,
            Path workingDirectory,
            String stageName,
            String command,
            Map<String, String> extraEnv,
            Duration timeout,
            BooleanSupplier cancellationRequested);
}