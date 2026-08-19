package com.example.platform.runner.service;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 运行器配置属性 —— 配置运行器的运行模式和工作空间路径。
 *
 * <p>核心职责：
 * <ul>
 *   <li>定义运行模式（LOCAL 或 DOCKER）</li>
 *   <li>配置工作空间根路径和主机映射路径</li>
 *   <li>通过 Spring Boot 配置文件注入，前缀为 platform.runner</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "platform.runner")
public class RunnerProperties {
    /** 运行模式，默认 LOCAL */
    private RunnerMode mode = RunnerMode.LOCAL;
    /** 工作空间根路径（容器内路径） */
    private Path workspaceRoot;
    /** 主机工作空间根路径（Docker 模式下的物理路径） */
    private Path hostWorkspaceRoot;

    public RunnerMode getMode() {
        return mode;
    }

    public void setMode(RunnerMode mode) {
        this.mode = mode;
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public Path getHostWorkspaceRoot() {
        return hostWorkspaceRoot;
    }

    public void setHostWorkspaceRoot(Path hostWorkspaceRoot) {
        this.hostWorkspaceRoot = hostWorkspaceRoot;
    }
}