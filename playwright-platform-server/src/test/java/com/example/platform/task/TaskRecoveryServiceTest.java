package com.example.platform.task;

import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.mapper.TaskMapper;
import com.example.platform.task.service.TaskRecoveryService;
import com.example.platform.task.service.TaskServiceImpl;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRecoveryServiceTest {
    @Test
    void shouldAbortRunningTaskAndRedispatchQueuedTaskOnStartupRecovery() {
        TaskMapper taskRepository = Mockito.mock(TaskMapper.class);
        SceneMapper sceneRepository = Mockito.mock(SceneMapper.class);
        TaskServiceImpl taskService = Mockito.mock(TaskServiceImpl.class);
        TaskRecoveryService service = new TaskRecoveryService(taskRepository, sceneRepository, taskService);

        TaskEntity runningTask = new TaskEntity();
        runningTask.setId(101L);
        runningTask.setSceneId(11L);
        runningTask.setStatus("RUNNING");
        runningTask.setStartedAt(LocalDateTime.now().minusMinutes(3));

        TaskEntity queuedTask = new TaskEntity();
        queuedTask.setId(102L);
        queuedTask.setSceneId(12L);
        queuedTask.setStatus("QUEUED");
        queuedTask.setQueuedAt(LocalDateTime.now().minusMinutes(1));

        SceneEntity runningScene = new SceneEntity();
        runningScene.setId(11L);
        SceneEntity queuedScene = new SceneEntity();
        queuedScene.setId(12L);

        Mockito.when(taskRepository.findAllByStatusInOrderByCreatedAtAscIdAsc(List.of("QUEUED", "RUNNING")))
                .thenReturn(List.of(runningTask, queuedTask));
        Mockito.when(taskRepository.findFirstBySceneIdOrderByCreatedAtDescIdDesc(11L)).thenReturn(Optional.of(runningTask));
        Mockito.when(taskRepository.findFirstBySceneIdOrderByCreatedAtDescIdDesc(12L)).thenReturn(Optional.of(queuedTask));
        Mockito.when(sceneRepository.findById(11L)).thenReturn(Optional.of(runningScene));
        Mockito.when(sceneRepository.findById(12L)).thenReturn(Optional.of(queuedScene));

        service.recoverStaleTasks();

        assertThat(runningTask.getStatus()).isEqualTo("FAILED");
        assertThat(runningTask.getResultCode()).isEqualTo("SYSTEM_ABORTED");
        assertThat(runningTask.getCurrentStage()).isEqualTo("FINISHED");
        Mockito.verify(taskService).dispatchExistingTask(102L, false);
        assertThat(runningScene.getLastTaskStatus()).isEqualTo("FAILED");
        assertThat(queuedScene.getLastTaskStatus()).isEqualTo("QUEUED");
    }

    @Test
    void shouldMarkCanceledQueuedTaskWithoutRedispatch() {
        TaskMapper taskRepository = Mockito.mock(TaskMapper.class);
        SceneMapper sceneRepository = Mockito.mock(SceneMapper.class);
        TaskServiceImpl taskService = Mockito.mock(TaskServiceImpl.class);
        TaskRecoveryService service = new TaskRecoveryService(taskRepository, sceneRepository, taskService);

        TaskEntity queuedTask = new TaskEntity();
        queuedTask.setId(201L);
        queuedTask.setSceneId(21L);
        queuedTask.setStatus("QUEUED");
        queuedTask.setCancelRequested(true);
        queuedTask.setQueuedAt(LocalDateTime.now().minusSeconds(30));

        SceneEntity scene = new SceneEntity();
        scene.setId(21L);

        Mockito.when(taskRepository.findAllByStatusInOrderByCreatedAtAscIdAsc(List.of("QUEUED", "RUNNING")))
                .thenReturn(List.of(queuedTask));
        Mockito.when(taskRepository.findFirstBySceneIdOrderByCreatedAtDescIdDesc(21L)).thenReturn(Optional.of(queuedTask));
        Mockito.when(sceneRepository.findById(21L)).thenReturn(Optional.of(scene));

        service.recoverStaleTasks();

        assertThat(queuedTask.getStatus()).isEqualTo("CANCELED");
        assertThat(queuedTask.getResultCode()).isEqualTo("CANCELED");
        Mockito.verifyNoInteractions(taskService);
    }
}
