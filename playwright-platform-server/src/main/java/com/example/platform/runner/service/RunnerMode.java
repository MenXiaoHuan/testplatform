package com.example.platform.runner.service;

/**
 * 运行器模式枚举 —— 定义命令执行的后端选择。
 *
 * <p>核心职责：
 * <ul>
 *   <li>LOCAL：在宿主机上直接执行，适用于本地开发环境</li>
 *   <li>DOCKER：在 Docker 容器中执行，提供环境隔离和资源限制</li>
 * </ul>
 */
public enum RunnerMode {
    /** 本地执行模式 */
    LOCAL,
    /** Docker 容器执行模式 */
    DOCKER
}