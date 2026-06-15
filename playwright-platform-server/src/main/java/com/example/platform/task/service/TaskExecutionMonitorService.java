package com.example.platform.task.service;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutionMonitorService {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutionMonitorService.class);

    private final ThreadPoolTaskExecutor taskExecutionExecutor;

    public TaskExecutionMonitorService(@Qualifier("taskExecutionExecutor") ThreadPoolTaskExecutor taskExecutionExecutor) {
        this.taskExecutionExecutor = taskExecutionExecutor;
    }

    @Scheduled(fixedDelayString = "${platform.task.execution.monitor-log-interval-seconds:30}000")
    public void logExecutorStats() {
        Map<String, Number> snapshot = snapshot();
        int activeCount = snapshot.get("activeCount").intValue();
        int queueSize = snapshot.get("queueSize").intValue();
        if (activeCount == 0 && queueSize == 0) {
            return;
        }
        log.info(
                "Task executor stats: activeCount={}, poolSize={}, maxPoolSize={}, queueSize={}, remainingQueueCapacity={}, completedTaskCount={}",
                snapshot.get("activeCount"),
                snapshot.get("poolSize"),
                snapshot.get("maxPoolSize"),
                snapshot.get("queueSize"),
                snapshot.get("remainingQueueCapacity"),
                snapshot.get("completedTaskCount"));
    }

    public Map<String, Number> snapshot() {
        ThreadPoolExecutor executor = taskExecutionExecutor.getThreadPoolExecutor();
        if (executor == null) {
            return Map.of(
                    "activeCount", 0,
                    "poolSize", 0,
                    "maxPoolSize", taskExecutionExecutor.getMaxPoolSize(),
                    "queueSize", 0,
                    "remainingQueueCapacity", taskExecutionExecutor.getQueueCapacity(),
                    "completedTaskCount", 0L);
        }
        return Map.of(
                "activeCount", executor.getActiveCount(),
                "poolSize", executor.getPoolSize(),
                "maxPoolSize", executor.getMaximumPoolSize(),
                "queueSize", executor.getQueue().size(),
                "remainingQueueCapacity", executor.getQueue().remainingCapacity(),
                "completedTaskCount", executor.getCompletedTaskCount());
    }
}
