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
        assertTransactional(RepositoryServiceImpl.class, "update", Long.class, TestRepositoryEntity.class);
        assertTransactional(RepositoryServiceImpl.class, "delete", Long.class);
    }

    @Test
    void sceneWritesShouldBeTransactional() throws NoSuchMethodException {
        assertTransactional(SceneServiceImpl.class, "create", SceneEntity.class);
        assertTransactional(SceneServiceImpl.class, "update", Long.class, SceneEntity.class);
        assertTransactional(SceneServiceImpl.class, "delete", Long.class);
    }

    @Test
    void taskCancelShouldBeTransactional() throws NoSuchMethodException {
        assertTransactional(TaskServiceImpl.class, "cancelTask", Long.class, String.class);
    }

    @Test
    void schedulerLeaseAndDueSceneWritesShouldBeTransactional() throws NoSuchMethodException {
        assertTransactional(SceneScheduleLeaseServiceImpl.class, "tryAcquire", Long.class, LocalDateTime.class);
        assertTransactional(SceneSchedulerServiceImpl.class, "triggerDueScenes", LocalDateTime.class);
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
