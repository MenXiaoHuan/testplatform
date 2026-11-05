package com.example.platform.patrol.service;

import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 巡页执行专用线程池：并行度 = {@code platform.patrol.max-concurrent}，
 * 并行已满时新任务进入队列而非直接拒绝。
 */
@Configuration
public class PatrolExecutionConfig {
    private static final Logger log = LoggerFactory.getLogger(PatrolExecutionConfig.class);

    @Bean(name = "patrolExecutionExecutor")
    public ThreadPoolTaskExecutor patrolExecutionExecutor(
            @Value("${platform.patrol.max-concurrent:10}") int maxConcurrent,
            @Value("${platform.patrol.queue-capacity:100}") int queueCapacity) {
        int threads = maxConcurrent <= 0 ? 10 : maxConcurrent;
        int queue = queueCapacity <= 0 ? 100 : queueCapacity;
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        // 每个任务分“快照+规划”“执行+判定”两阶段提交，队列放宽避免审批恢复时被拒。
        executor.setQueueCapacity(queue * 2);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("patrol-execution-");
        executor.setRejectedExecutionHandler((task, threadPool) -> {
            int queueSize = threadPool.getQueue() != null ? threadPool.getQueue().size() : -1;
            log.error("Patrol execution rejected. activeCount={}, poolSize={}, queueSize={}",
                    threadPool.getActiveCount(), threadPool.getPoolSize(), queueSize);
            throw new RejectedExecutionException("Patrol execution queue is full");
        });
        executor.initialize();
        return executor;
    }
}
