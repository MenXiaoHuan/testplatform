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
 * 任务执行配置类 —— 定义用于长时间任务执行的专用线程池。
 *
 * <p>HTTP 请求线程只负责入队工作；Playwright 安装、测试执行、日志采集和工件归档
 * 都在此线程池中运行，避免阻塞 Web 请求处理。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #taskExecutionExecutor(TaskExecutionProperties)} —— 创建配置化的任务执行线程池 Bean</li>
 * </ul>
 *
 * <p>依赖：{@link TaskExecutionProperties}（线程池参数配置）。
 */
@Configuration
@EnableConfigurationProperties(TaskExecutionProperties.class)
public class TaskExecutionConfig {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutionConfig.class);

    /**
     * 创建任务执行专用线程池，配置核心线程数、最大线程数、队列容量、拒绝策略等。
     */
    @Bean(name = "taskExecutionExecutor")
    public ThreadPoolTaskExecutor taskExecutionExecutor(TaskExecutionProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        executor.setThreadNamePrefix("task-execution-");
        // 自定义拒绝策略：记录详细日志后抛出异常
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
