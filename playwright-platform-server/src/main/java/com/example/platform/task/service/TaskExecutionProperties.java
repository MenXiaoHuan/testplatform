package com.example.platform.task.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.task.execution")
public class TaskExecutionProperties {
    private int corePoolSize = 2;
    private int maxPoolSize = 4;
    private int queueCapacity = 50;
    private int keepAliveSeconds = 60;
    private int installTimeoutSeconds = 600;
    private int testTimeoutSeconds = 1800;
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
