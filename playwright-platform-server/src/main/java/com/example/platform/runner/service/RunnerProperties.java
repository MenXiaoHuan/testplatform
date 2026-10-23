package com.example.platform.runner.service;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 运行器配置属性 —— 平台只使用 Docker Runner。
 *
 * <p>核心职责：
 * <ul>
 *   <li>配置工作空间根路径和主机映射路径</li>
 *   <li>配置 git clone 超时和日志上限</li>
 *   <li>通过 Spring Boot 配置文件注入，前缀为 platform.runner</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "platform.runner")
public class RunnerProperties {
    /** 工作空间根路径（容器内路径） */
    private Path workspaceRoot;
    /** 主机工作空间根路径（Docker 挂载的物理路径） */
    private Path hostWorkspaceRoot;
    /** git clone 超时时间（秒） */
    private int gitCloneTimeoutSeconds = 120;
    /** 安装/准备阶段单个日志最多落盘字节数 */
    private long maxLogBytes = 50L * 1024 * 1024;
    /** 测试阶段日志上限，默认比安装阶段更宽松 */
    private long maxTestLogBytes = 200L * 1024 * 1024;
    /** known_hosts 路径；为空时使用 git 默认配置 */
    private String knownHostsPath;
    /** 是否克隆 Git submodule */
    private boolean submodulesEnabled;
    /** 是否允许 Git LFS 下载；false 时设置 GIT_LFS_SKIP_SMUDGE=1 */
    private boolean lfsEnabled;

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

    public int getGitCloneTimeoutSeconds() {
        return gitCloneTimeoutSeconds;
    }

    public void setGitCloneTimeoutSeconds(int gitCloneTimeoutSeconds) {
        this.gitCloneTimeoutSeconds = gitCloneTimeoutSeconds;
    }

    public long getMaxLogBytes() {
        return maxLogBytes;
    }

    public void setMaxLogBytes(long maxLogBytes) {
        this.maxLogBytes = maxLogBytes;
    }

    public long getMaxTestLogBytes() {
        return maxTestLogBytes;
    }

    public void setMaxTestLogBytes(long maxTestLogBytes) {
        this.maxTestLogBytes = maxTestLogBytes;
    }

    public String getKnownHostsPath() {
        return knownHostsPath;
    }

    public void setKnownHostsPath(String knownHostsPath) {
        this.knownHostsPath = knownHostsPath;
    }

    public boolean isSubmodulesEnabled() {
        return submodulesEnabled;
    }

    public void setSubmodulesEnabled(boolean submodulesEnabled) {
        this.submodulesEnabled = submodulesEnabled;
    }

    public boolean isLfsEnabled() {
        return lfsEnabled;
    }

    public void setLfsEnabled(boolean lfsEnabled) {
        this.lfsEnabled = lfsEnabled;
    }
}