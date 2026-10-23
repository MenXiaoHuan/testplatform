package com.example.platform.task.service;

import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.mapper.ArtifactMapper;
import com.example.platform.task.mapper.CaseResultMapper;
import com.example.platform.task.mapper.TaskMapper;
import com.example.platform.task.mapper.TaskStageLogMapper;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.model.TaskStageLogEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 可选的任务数据保留清理。
 *
 * <p>默认 {@code platform.task.retention.days=0} 不清理；配置为 90 后每天清理
 * 已完成且超过保留期的任务，同时删除 MinIO 中的产物/阶段日志对象。
 * 这是单机项目的最小保留策略，不引入独立归档系统。
 */
@Service
public class TaskRetentionService {
    private static final Logger log = LoggerFactory.getLogger(TaskRetentionService.class);
    private static final int BATCH_SIZE = 100;

    private final TaskMapper taskMapper;
    private final CaseResultMapper caseResultMapper;
    private final ArtifactMapper artifactMapper;
    private final TaskStageLogMapper taskStageLogMapper;
    private final ObjectStorageService objectStorageService;
    private final String storageBucket;
    private final int retentionDays;

    public TaskRetentionService(
            TaskMapper taskMapper,
            CaseResultMapper caseResultMapper,
            ArtifactMapper artifactMapper,
            TaskStageLogMapper taskStageLogMapper,
            ObjectStorageService objectStorageService,
            @Value("${platform.storage.bucket:qa-report}") String storageBucket,
            @Value("${platform.task.retention.days:0}") int retentionDays) {
        this.taskMapper = taskMapper;
        this.caseResultMapper = caseResultMapper;
        this.artifactMapper = artifactMapper;
        this.taskStageLogMapper = taskStageLogMapper;
        this.objectStorageService = objectStorageService;
        this.storageBucket = storageBucket;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${platform.task.retention.cron:0 30 3 * * *}")
    @Transactional
    public void scheduledCleanup() {
        cleanupExpiredTasks();
    }

    /**
     * 删除超过保留期的任务。返回删除的任务数，便于测试/手动触发时观察。
     */
    @Transactional
    public int cleanupExpiredTasks() {
        if (retentionDays <= 0) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<TaskEntity> tasks = taskMapper.findFinishedBefore(cutoff, BATCH_SIZE);
        if (tasks.isEmpty()) {
            return 0;
        }

        int deleted = 0;
        for (TaskEntity task : tasks) {
            List<Long> taskIds = List.of(task.getId());
            List<ArtifactEntity> artifacts = artifactMapper.findAllByTaskIdIn(taskIds);
            List<TaskStageLogEntity> stageLogs = taskStageLogMapper.findAllByTaskIdIn(taskIds);

            for (ArtifactEntity artifact : artifacts) {
                deleteObject(artifact.getBucket(), artifact.getObjectKey(), task.getId());
            }
            for (TaskStageLogEntity stageLog : stageLogs) {
                deleteObject(storageBucket, stageLog.getObjectKey(), task.getId());
            }

            caseResultMapper.deleteAllByTaskIdIn(taskIds);
            artifactMapper.deleteAllByTaskIdIn(taskIds);
            taskStageLogMapper.deleteAllByTaskIdIn(taskIds);
            taskMapper.deleteById(task.getId());
            deleted++;
        }
        log.info("Task retention cleanup finished. retentionDays={}, deleted={}", retentionDays, deleted);
        return deleted;
    }

    private void deleteObject(String bucket, String objectKey, Long taskId) {
        if (bucket == null || bucket.isBlank() || objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            objectStorageService.deleteObject(bucket, objectKey);
        } catch (Exception exception) {
            // 删对象失败不阻塞数据库清理；对象可以后续用 MinIO 生命周期规则兜底。
            log.warn("Failed to delete retained object. taskId={}, bucket={}, key={}, reason={}",
                    taskId, bucket, objectKey, exception.getMessage());
        }
    }
}
