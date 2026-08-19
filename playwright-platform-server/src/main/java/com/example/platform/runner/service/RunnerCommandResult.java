package com.example.platform.runner.service;

import java.nio.file.Path;

/**
 * 命令执行结果 —— 封装命令执行完成后的所有输出信息。
 *
 * <p>核心职责：
 * <ul>
 *   <li>返回进程退出码、超时和取消状态</li>
 *   <li>提供执行耗时和日志文件引用</li>
 * </ul>
 *
 * @param exitCode        进程退出码，-1 表示异常终止
 * @param timedOut        是否因超时被强制终止
 * @param canceled        是否因用户取消被强制终止
 * @param durationMs      执行耗时（毫秒）
 * @param combinedLogFile 合并输出的日志文件路径
 * @param lineCount       日志输出行数
 */
public record RunnerCommandResult(
        int exitCode,
        boolean timedOut,
        boolean canceled,
        long durationMs,
        Path combinedLogFile,
        int lineCount) {
}