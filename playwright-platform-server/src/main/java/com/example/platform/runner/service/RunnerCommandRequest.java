package com.example.platform.runner.service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * 命令执行请求 —— 封装单次命令执行所需的所有参数。
 *
 * <p>核心职责：
 * <ul>
 *   <li>携带工作空间路径、工作目录、命令内容等执行上下文</li>
 *   <li>传递环境变量、超时时间和取消信号</li>
 * </ul>
 *
 * @param workspaceRoot        工作空间根路径
 * @param workingDirectory      命令执行的工作目录
 * @param stageName             阶段名称（用于日志标识）
 * @param command               要执行的 shell 命令
 * @param extraEnv               额外环境变量映射
 * @param timeout               执行超时时间
 * @param cancellationRequested 取消信号提供者，返回 true 时请求取消
 */
public record RunnerCommandRequest(
        Path workspaceRoot,
        Path workingDirectory,
        String stageName,
        String command,
        Map<String, String> extraEnv,
        Duration timeout,
        BooleanSupplier cancellationRequested) {
}