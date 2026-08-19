package com.example.platform.runner.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Docker 运行器配置属性 —— 配置 Docker 执行器的各项参数。
 *
 * <p>核心职责：
 * <ul>
 *   <li>定义 Docker 镜像、网络、资源限制等运行参数</li>
 *   <li>配置容器工作目录映射和镜像拉取超时</li>
 *   <li>通过 Spring Boot 配置文件注入，前缀为 platform.runner.docker</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "platform.runner.docker")
public class DockerRunnerProperties {
    /** Docker 镜像名称，默认使用 Microsoft Playwright 镜像 */
    private String image = "mcr.microsoft.com/playwright:v1.44.0-jammy";
    /** 网络模式，默认使用 bridge */
    private String network = "bridge";
    /** 内存限制，默认 2GB */
    private String memory = "2g";
    /** CPU 限制，默认 2 核 */
    private String cpus = "2";
    /** 容器内工作空间根路径 */
    private String containerWorkspaceRoot = "/workspace/task";
    /** 容器退出后是否自动删除 */
    private boolean removeContainer = true;
    /** 镜像拉取超时时间（秒），默认 30 分钟 */
    private long imagePullTimeoutSeconds = 1800;

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public String getMemory() {
        return memory;
    }

    public void setMemory(String memory) {
        this.memory = memory;
    }

    public String getCpus() {
        return cpus;
    }

    public void setCpus(String cpus) {
        this.cpus = cpus;
    }

    public String getContainerWorkspaceRoot() {
        return containerWorkspaceRoot;
    }

    public void setContainerWorkspaceRoot(String containerWorkspaceRoot) {
        this.containerWorkspaceRoot = containerWorkspaceRoot;
    }

    public boolean isRemoveContainer() {
        return removeContainer;
    }

    public void setRemoveContainer(boolean removeContainer) {
        this.removeContainer = removeContainer;
    }

    public long getImagePullTimeoutSeconds() {
        return imagePullTimeoutSeconds;
    }

    public void setImagePullTimeoutSeconds(long imagePullTimeoutSeconds) {
        this.imagePullTimeoutSeconds = imagePullTimeoutSeconds;
    }
}