package com.example.platform.scene.service;

import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.model.SceneJpaRepository;
import com.example.platform.task.service.TaskService;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SceneSchedulerServiceImpl implements SceneSchedulerService {
    private static final Logger log = LoggerFactory.getLogger(SceneSchedulerServiceImpl.class);

    private final SceneJpaRepository sceneRepository;
    private final SceneScheduleLeaseService leaseService;
    private final TaskService taskService;
    private final SceneScheduleTimeResolver sceneScheduleTimeResolver = new SceneScheduleTimeResolver();

    public SceneSchedulerServiceImpl(
            SceneJpaRepository sceneRepository,
            SceneScheduleLeaseService leaseService,
            TaskService taskService) {
        this.sceneRepository = sceneRepository;
        this.leaseService = leaseService;
        this.taskService = taskService;
    }

    @Override
    public void triggerDueScenes(LocalDateTime now) {
        initializeNextRunAtForLegacyScenes(now);
        for (SceneEntity scene : sceneRepository.findDueScheduledScenes(now)) {
            LocalDateTime plannedFireAt = scene.getNextRunAt();
            if (plannedFireAt == null) {
                continue;
            }
            if (!leaseService.tryAcquire(scene.getId(), plannedFireAt)) {
                continue;
            }
            String cronExpression = scene.getCronExpression();
            scene.setNextRunAt(sceneScheduleTimeResolver.resolveNextRunAfter(cronExpression, plannedFireAt));
            sceneRepository.save(scene);
            try {
                taskService.createScheduledTask(scene.getId(), "cron:" + cronExpression);
            } catch (RuntimeException exception) {
                log.warn(
                        "Failed to create scheduled task. sceneId={}, cron={}, reason={}",
                        scene.getId(),
                        cronExpression,
                        exception.getMessage());
            }
        }
    }

    private void initializeNextRunAtForLegacyScenes(LocalDateTime now) {
        for (SceneEntity scene : sceneRepository.findAllByScheduleEnabledTrueAndNextRunAtIsNullOrderByIdAsc()) {
            LocalDateTime nextRunAt = sceneScheduleTimeResolver.resolveNextRunAt(
                    scene.getScheduleEnabled(),
                    scene.getCronExpression(),
                    now);
            if (nextRunAt != null) {
                scene.setNextRunAt(nextRunAt);
                sceneRepository.save(scene);
            }
        }
    }
}
