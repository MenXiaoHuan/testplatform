package com.example.platform.task.service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Defines the dedicated executor used for long-running task execution.
 *
 * <p>HTTP request threads should only enqueue work; Playwright installation,
 * test execution, log capture, and artifact archiving run on this pool.
 */
@Configuration
@EnableConfigurationProperties(TaskExecutionProperties.class)
public class TaskExecutionConfig {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutionConfig.class);

    @Bean(name = "taskExecutionExecutor")
    public ThreadPoolTaskExecutor taskExecutionExecutor(TaskExecutionProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        executor.setThreadNamePrefix("task-execution-");
        executor.setRejectedExecutionHandler((task, threadPool) -> {
            int queueSize = threadPool.getQueue() != null ? threadPool.getQueue().size() : -1;
            log.error(
                    "Task execution rejected. activeCount={}, poolSize={}, maxPoolSize={}, queueSize={}",
                    threadPool.getActiveCount(),
                    threadPool.getPoolSize(),
                    threadPool.getMaximumPoolSize(),
                    queueSize);
            throw new RejectedExecutionException("Task execution queue is full");
        });
        executor.initialize();
        return executor;
    }
}
