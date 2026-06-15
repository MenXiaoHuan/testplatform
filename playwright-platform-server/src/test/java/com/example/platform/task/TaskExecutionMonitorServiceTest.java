package com.example.platform.task;

import com.example.platform.task.service.TaskExecutionMonitorService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class TaskExecutionMonitorServiceTest {
    @Test
    void shouldExposeExecutorSnapshot() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(5);
        executor.initialize();

        TaskExecutionMonitorService service = new TaskExecutionMonitorService(executor);
        Map<String, Number> snapshot = service.snapshot();

        assertThat(snapshot.get("maxPoolSize").intValue()).isEqualTo(2);
        assertThat(snapshot.get("queueSize").intValue()).isGreaterThanOrEqualTo(0);
        assertThat(snapshot.get("remainingQueueCapacity").intValue()).isLessThanOrEqualTo(5);
    }
}
