package com.example.platform.task.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 任务执行属性配置 —— 通过 {@code platform.task.execution.*} 前缀配置的线程池和超时参数。
 *
 * <p>核心职责：
 * <ul>
 *   <li>线程池参数：核心线程数、最大线程数、队列容量、线程存活时间</li>
 *   <li>超时配置：Playwright 安装超时、测试执行超时</li>
 *   <li>监控配置：线程池状态日志输出间隔</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "platform.task.execution")
public class TaskExecutionProperties {
    /** 核心线程数，默认 2 */
    private int corePoolSize = 2;
    /** 最大线程数，默认 4 */
    private int maxPoolSize = 4;
    /** 任务队列容量，默认 50 */
    private int queueCapacity = 50;
    /** 空闲线程存活时间（秒），默认 60 */
    private int keepAliveSeconds = 60;
    /** Playwright 安装超时（秒），默认 600 */
    private int installTimeoutSeconds = 600;
    /** 测试执行超时（秒），默认 1800（30 分钟） */
    private int testTimeoutSeconds = 1800;
    /** 线程池监控日志输出间隔（秒），默认 30 */
    private int monitorLogIntervalSeconds = 30;

    public int getCorePoolSize() { return corePoolSize; }
    public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }
    public int getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    public int getKeepAliveSeconds() { return keepAliveSeconds; }
    public void setKeepAliveSeconds(int keepAliveSeconds) { this.keepAliveSeconds = keepAliveSeconds; }
    public int getInstallTimeoutSeconds() { return installTimeoutSeconds; }
    public void setInstallTimeoutSeconds(int installTimeoutSeconds) { this.installTimeoutSeconds = installTimeoutSeconds; }
    public int getTestTimeoutSeconds() { return testTimeoutSeconds; }
    public void setTestTimeoutSeconds(int testTimeoutSeconds) { this.testTimeoutSeconds = testTimeoutSeconds; }
    public int getMonitorLogIntervalSeconds() { return monitorLogIntervalSeconds; }
    public void setMonitorLogIntervalSeconds(int monitorLogIntervalSeconds) { this.monitorLogIntervalSeconds = monitorLogIntervalSeconds; }
}
