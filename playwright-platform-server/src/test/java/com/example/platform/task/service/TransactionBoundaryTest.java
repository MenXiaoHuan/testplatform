package com.example.platform.task.service;

import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.service.RepositoryServiceImpl;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.service.SceneScheduleLeaseServiceImpl;
import com.example.platform.scene.service.SceneSchedulerServiceImpl;
import com.example.platform.scene.service.SceneServiceImpl;
import com.example.platform.task.model.TaskEntity;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionBoundaryTest {
    @Test
    void repositoryWritesShouldBeTransactional() throws NoSuchMethodException {
        assertTransactional(RepositoryServiceImpl.class, "create", TestRepositoryEntity.class);
        assertTransactional(RepositoryServiceImpl.class, "update", Long.class, Long.class, TestRepositoryEntity.class);
        assertTransactional(RepositoryServiceImpl.class, "delete", Long.class, Long.class);
    }

    @Test
    void sceneWritesShouldBeTransactional() throws NoSuchMethodException {
        assertTransactional(SceneServiceImpl.class, "create", SceneEntity.class);
        assertTransactional(SceneServiceImpl.class, "update", Long.class, Long.class, SceneEntity.class);
        assertTransactional(SceneServiceImpl.class, "delete", Long.class, Long.class);
    }

    @Test
    void taskCancelShouldBeTransactional() throws NoSuchMethodException {
        assertTransactional(TaskServiceImpl.class, "cancelTask", Long.class, String.class);
    }

    @Test
    void schedulerLeaseShouldBeTransactional() throws NoSuchMethodException {
        assertTransactional(SceneScheduleLeaseServiceImpl.class, "tryAcquire", Long.class, LocalDateTime.class);
    }

    @Test
    void schedulerLoopShouldNotWrapTaskDispatchInOneTransaction() throws NoSuchMethodException {
        Method trigger = SceneSchedulerServiceImpl.class.getDeclaredMethod(
                "triggerDueScenes", LocalDateTime.class);
        assertThat(trigger.getAnnotation(Transactional.class))
                .as("整轮扫描不能包在一个事务里，否则异步派发可能读不到未提交任务")
                .isNull();
    }

    @Test
    void longTaskExecutionShouldNotBeTransactional() throws NoSuchMethodException {
        Method executeTask = TaskExecutionOrchestrator.class.getDeclaredMethod(
                "executeTask",
                TaskEntity.class,
                TestRepositoryEntity.class,
                SceneEntity.class);

        assertThat(executeTask.getAnnotation(Transactional.class)).isNull();
    }

    private void assertTransactional(Class<?> type, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(methodName, parameterTypes);

        assertThat(method.getAnnotation(Transactional.class))
                .as("%s.%s should declare @Transactional", type.getSimpleName(), methodName)
                .isNotNull();
    }
}
