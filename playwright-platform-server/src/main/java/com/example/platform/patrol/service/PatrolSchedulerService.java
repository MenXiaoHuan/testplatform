package com.example.platform.patrol.service;

import com.example.platform.patrol.mapper.PatrolSceneMapper;
import com.example.platform.patrol.model.PatrolSceneEntity;
import com.example.platform.patrol.model.PatrolTaskEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/** 巡页场景定时调度：每 60 秒扫描到期场景并创建任务。 */
@Service
public class PatrolSchedulerService {
    private static final Logger log = LoggerFactory.getLogger(PatrolSchedulerService.class);

    private final PatrolSceneMapper sceneMapper;
    private final PatrolSceneService sceneService;

    public PatrolSchedulerService(PatrolSceneMapper sceneMapper, PatrolSceneService sceneService) {
        this.sceneMapper = sceneMapper;
        this.sceneService = sceneService;
    }

    @Scheduled(fixedDelay = 60000)
    public void triggerDueScenes() {
        List<PatrolSceneEntity> due;
        try {
            due = sceneMapper.findDueScenes(LocalDateTime.now());
        } catch (Exception exception) {
            log.warn("Patrol scheduler scan failed: {}", exception.getMessage());
            return;
        }
        for (PatrolSceneEntity scene : due) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime next = nextRun(scene.getCronExpression(), now);
            try {
                PatrolTaskEntity task = sceneService.runScheduled(scene);
                if (task != null) {
                    log.info("Patrol scheduled task created. sceneId={}, taskId={}, nextRunAt={}",
                            scene.getId(), task.getId(), next);
                }
            } catch (Exception exception) {
                log.warn("Patrol scheduled run failed. sceneId={}, reason={}",
                        scene.getId(), exception.getMessage());
            } finally {
                try {
                    sceneMapper.updateNextRunAt(scene.getId(), next);
                } catch (Exception exception) {
                    log.warn("Update patrol nextRunAt failed. sceneId={}, reason={}",
                            scene.getId(), exception.getMessage());
                }
            }
        }
    }

    private LocalDateTime nextRun(String cron, LocalDateTime now) {
        if (cron == null || cron.isBlank()) {
            return null;
        }
        try {
            return CronExpression.parse(cron).next(now);
        } catch (Exception exception) {
            return null;
        }
    }
}
