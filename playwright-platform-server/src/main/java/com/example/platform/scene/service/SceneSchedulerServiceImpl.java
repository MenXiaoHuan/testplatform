package com.example.platform.scene.service;

import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.task.service.TaskService;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SceneSchedulerServiceImpl implements SceneSchedulerService {
    private static final Logger log = LoggerFactory.getLogger(SceneSchedulerServiceImpl.class);

    private final SceneMapper sceneMapper;
    private final SceneScheduleLeaseService leaseService;
    private final TaskService taskService;
    private final SceneScheduleTimeResolver sceneScheduleTimeResolver = new SceneScheduleTimeResolver();

    public SceneSchedulerServiceImpl(
            SceneMapper sceneMapper,
            SceneScheduleLeaseService leaseService,
            TaskService taskService) {
        this.sceneMapper = sceneMapper;
        this.leaseService = leaseService;
        this.taskService = taskService;
    }

    @Override
    public void triggerDueScenes(LocalDateTime now) {
        initializeNextRunAtForLegacyScenes(now);
        for (SceneEntity scene : sceneMapper.findDueScheduledScenes(now)) {
            LocalDateTime plannedFireAt = scene.getNextRunAt();
            if (plannedFireAt == null) {
                continue;
            }
            if (!leaseService.tryAcquire(scene.getId(), plannedFireAt)) {
                continue;
            }
            String cronExpression = scene.getCronExpression();
            scene.setNextRunAt(sceneScheduleTimeResolver.resolveNextRunAfter(cronExpression, plannedFireAt));
            sceneMapper.update(scene);
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
        for (SceneEntity scene : sceneMapper.findAllByScheduleEnabledTrueAndNextRunAtIsNullOrderByIdAsc()) {
            LocalDateTime nextRunAt = sceneScheduleTimeResolver.resolveNextRunAt(
                    scene.getScheduleEnabled(),
                    scene.getCronExpression(),
                    now);
            if (nextRunAt != null) {
                scene.setNextRunAt(nextRunAt);
                sceneMapper.update(scene);
            }
        }
    }
}
