package com.example.platform.task.service;

import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.mapper.TaskMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(TaskRecoveryService.class);
    private static final Collection<String> RECOVERABLE_STATUSES = List.of("QUEUED", "RUNNING");

    private final TaskMapper taskRepository;
    private final SceneMapper sceneMapper;
    private final TaskServiceImpl taskService;

    public TaskRecoveryService(
            TaskMapper taskRepository,
            SceneMapper sceneMapper,
            TaskServiceImpl taskService) {
        this.taskRepository = taskRepository;
        this.sceneMapper = sceneMapper;
        this.taskService = taskService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recoverStaleTasks();
    }

    @Transactional
    public void recoverStaleTasks() {
        List<TaskEntity> staleTasks = taskRepository.findAllByStatusInOrderByCreatedAtAscIdAsc(RECOVERABLE_STATUSES);
        if (staleTasks.isEmpty()) {
            return;
        }

        Set<Long> touchedSceneIds = new LinkedHashSet<>();
        LocalDateTime now = LocalDateTime.now();
        for (TaskEntity task : staleTasks) {
            touchedSceneIds.add(task.getSceneId());
            if ("RUNNING".equalsIgnoreCase(task.getStatus())) {
                markRecoveredRunningTask(task, now);
                continue;
            }
            if (Boolean.TRUE.equals(task.getCancelRequested())) {
                markRecoveredQueuedCancellation(task, now);
            }
        }

        refreshSceneSummaries(touchedSceneIds);

        staleTasks.stream()
                .filter(task -> "QUEUED".equalsIgnoreCase(task.getStatus()))
                .filter(task -> !Boolean.TRUE.equals(task.getCancelRequested()))
                .forEach(task -> {
                    log.info("Recover queued task after restart. taskId={}, sceneId={}", task.getId(), task.getSceneId());
                    taskService.dispatchExistingTask(task.getId(), false);
                });
    }

    private void markRecoveredRunningTask(TaskEntity task, LocalDateTime finishedAt) {
        if (Boolean.TRUE.equals(task.getCancelRequested())) {
            task.setStatus("CANCELED");
            task.setResultCode("CANCELED");
            task.setResultMessage("服务重启时任务已终止");
        } else {
            task.setStatus("FAILED");
            task.setResultCode("SYSTEM_ABORTED");
            task.setResultMessage("服务重启导致任务中断");
        }
        task.setCurrentStage("FINISHED");
        task.setFinishedAt(finishedAt);
        if (task.getStartedAt() != null) {
            task.setDurationMs(Duration.between(task.getStartedAt(), finishedAt).toMillis());
        }
        taskRepository.update(task);
    }

    private void markRecoveredQueuedCancellation(TaskEntity task, LocalDateTime finishedAt) {
        task.setStatus("CANCELED");
        task.setCurrentStage("FINISHED");
        task.setResultCode("CANCELED");
        task.setResultMessage("任务在执行前已取消");
        task.setFinishedAt(finishedAt);
        if (task.getQueuedAt() != null) {
            task.setDurationMs(Duration.between(task.getQueuedAt(), finishedAt).toMillis());
        }
        taskRepository.update(task);
    }

    private void refreshSceneSummaries(Set<Long> sceneIds) {
        for (Long sceneId : sceneIds) {
            TaskEntity summarySource = taskRepository.findFirstBySceneIdOrderByCreatedAtDescIdDesc(sceneId).orElse(null);
            SceneEntity scene = sceneMapper.findById(sceneId).orElse(null);
            if (summarySource == null || scene == null) {
                continue;
            }
            scene.setLastTaskStatus(summarySource.getStatus());
            scene.setLastRunAt(summarySource.getFinishedAt() != null ? summarySource.getFinishedAt() : summarySource.getQueuedAt());
            sceneMapper.update(scene);
        }
    }
}
