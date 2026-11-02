package com.example.platform.patrol.service;

import com.example.platform.patrol.mapper.PatrolCaseMapper;
import com.example.platform.patrol.mapper.PatrolCaseStepMapper;
import com.example.platform.patrol.mapper.PatrolTaskMapper;
import com.example.platform.patrol.model.PatrolCaseEntity;
import com.example.platform.patrol.model.PatrolCaseStepEntity;
import com.example.platform.patrol.model.PatrolTaskEntity;
import com.example.platform.storage.service.ObjectStorageService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 巡页任务 90 天保留：到期删除数据库记录与全部证据对象。 */
@Service
public class PatrolRetentionService {
    private static final Logger log = LoggerFactory.getLogger(PatrolRetentionService.class);
    private static final int CLEANUP_BATCH = 50;

    private final PatrolTaskMapper taskMapper;
    private final PatrolCaseMapper caseMapper;
    private final PatrolCaseStepMapper caseStepMapper;
    private final ObjectStorageService objectStorageService;
    private final String storageBucket;

    public PatrolRetentionService(PatrolTaskMapper taskMapper,
                                  PatrolCaseMapper caseMapper,
                                  PatrolCaseStepMapper caseStepMapper,
                                  ObjectStorageService objectStorageService,
                                  @Value("${platform.storage.bucket}") String storageBucket) {
        this.taskMapper = taskMapper;
        this.caseMapper = caseMapper;
        this.caseStepMapper = caseStepMapper;
        this.objectStorageService = objectStorageService;
        this.storageBucket = storageBucket;
    }

    /** 每天 02:30 清理到期任务；任务与证据按 90 天保留。 */
    @Scheduled(cron = "${platform.patrol.retention-cron:0 30 2 * * *}")
    public void cleanupExpiredTasks() {
        try {
            List<PatrolTaskEntity> candidates =
                    taskMapper.findRetentionCandidates(java.time.LocalDateTime.now(), CLEANUP_BATCH);
            for (PatrolTaskEntity task : candidates) {
                purgeArtifacts(task);
                taskMapper.deleteById(task.getId());
                log.info("Patrol task expired and removed. taskId={}, retentionUntil={}",
                        task.getId(), task.getRetentionUntil());
            }
        } catch (Exception exception) {
            log.warn("Patrol retention cleanup failed: {}", exception.getMessage());
        }
    }

    /** 删除任务关联的全部证据对象（任务/用例/步骤截图、录屏、控制台、DOM）。 */
    public void purgeArtifacts(PatrolTaskEntity task) {
        Set<String> keys = new LinkedHashSet<>();
        addKey(keys, task.getSnapshotScreenshotKey());
        List<PatrolCaseEntity> cases = caseMapper.findByTaskId(task.getId());
        for (PatrolCaseEntity caseEntity : cases) {
            addKey(keys, caseEntity.getVideoKey());
            addKey(keys, caseEntity.getConsoleKey());
            addKey(keys, caseEntity.getDomKey());
            addKey(keys, caseEntity.getScreenshotKey());
        }
        List<PatrolCaseStepEntity> steps = caseStepMapper.findByTaskId(task.getId());
        for (PatrolCaseStepEntity step : steps) {
            addKey(keys, step.getScreenshotKey());
        }
        for (String key : keys) {
            try {
                objectStorageService.deleteObject(storageBucket, key);
            } catch (Exception exception) {
                log.debug("Delete patrol object failed. key={}, reason={}", key, exception.getMessage());
            }
        }
    }

    private static void addKey(Set<String> keys, String key) {
        if (key != null && !key.isBlank()) {
            keys.add(key);
        }
    }
}
