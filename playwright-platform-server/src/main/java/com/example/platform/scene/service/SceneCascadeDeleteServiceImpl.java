package com.example.platform.scene.service;

import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.model.SceneJpaRepository;
import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.ArtifactJpaRepository;
import com.example.platform.task.model.CaseResultJpaRepository;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.model.TaskJpaRepository;
import com.example.platform.task.model.TaskStageLogEntity;
import com.example.platform.task.model.TaskStageLogJpaRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SceneCascadeDeleteServiceImpl implements SceneCascadeDeleteService {
    private final SceneJpaRepository sceneRepository;
    private final TaskJpaRepository taskRepository;
    private final CaseResultJpaRepository caseResultRepository;
    private final ArtifactJpaRepository artifactRepository;
    private final TaskStageLogJpaRepository taskStageLogRepository;
    private final ObjectStorageService objectStorageService;
    private final String storageBucket;

    public SceneCascadeDeleteServiceImpl(
            SceneJpaRepository sceneRepository,
            TaskJpaRepository taskRepository,
            CaseResultJpaRepository caseResultRepository,
            ArtifactJpaRepository artifactRepository,
            TaskStageLogJpaRepository taskStageLogRepository,
            ObjectStorageService objectStorageService,
            @Value("${platform.storage.bucket}") String storageBucket) {
        this.sceneRepository = sceneRepository;
        this.taskRepository = taskRepository;
        this.caseResultRepository = caseResultRepository;
        this.artifactRepository = artifactRepository;
        this.taskStageLogRepository = taskStageLogRepository;
        this.objectStorageService = objectStorageService;
        this.storageBucket = storageBucket;
    }

    @Override
    @Transactional
    public void deleteSceneGraph(Long sceneId) {
        SceneEntity scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + sceneId));

        List<TaskEntity> tasks = taskRepository.findAllBySceneIdOrderByIdAsc(sceneId);
        List<Long> taskIds = tasks.stream().map(TaskEntity::getId).toList();

        deleteArtifactObjects(taskIds);
        deleteStageLogObjects(taskIds);

        if (!taskIds.isEmpty()) {
            caseResultRepository.deleteAllByTaskIdIn(taskIds);
            artifactRepository.deleteAllByTaskIdIn(taskIds);
            taskStageLogRepository.deleteAllByTaskIdIn(taskIds);
        }

        taskRepository.deleteAllBySceneId(sceneId);
        sceneRepository.delete(scene);
    }

    private void deleteArtifactObjects(List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return;
        }
        List<ArtifactEntity> artifacts = artifactRepository.findAllByTaskIdIn(taskIds);
        for (ArtifactEntity artifact : artifacts) {
            if (artifact.getBucket() != null && artifact.getObjectKey() != null) {
                objectStorageService.deleteObject(artifact.getBucket(), artifact.getObjectKey());
            }
        }
    }

    private void deleteStageLogObjects(List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return;
        }
        List<TaskStageLogEntity> stageLogs = taskStageLogRepository.findAllByTaskIdIn(taskIds);
        for (TaskStageLogEntity stageLog : stageLogs) {
            if (stageLog.getObjectKey() != null && !stageLog.getObjectKey().isBlank()) {
                objectStorageService.deleteObject(storageBucket, stageLog.getObjectKey());
            }
        }
    }

}
