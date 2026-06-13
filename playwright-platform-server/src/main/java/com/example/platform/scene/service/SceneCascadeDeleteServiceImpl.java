package com.example.platform.scene.service;

import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.model.SceneJpaRepository;
import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.ArtifactJpaRepository;
import com.example.platform.task.model.CaseResultJpaRepository;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.model.TaskJpaRepository;
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
    private final ObjectStorageService objectStorageService;
    private final String storageBucket;

    public SceneCascadeDeleteServiceImpl(
            SceneJpaRepository sceneRepository,
            TaskJpaRepository taskRepository,
            CaseResultJpaRepository caseResultRepository,
            ArtifactJpaRepository artifactRepository,
            ObjectStorageService objectStorageService,
            @Value("${platform.storage.bucket}") String storageBucket) {
        this.sceneRepository = sceneRepository;
        this.taskRepository = taskRepository;
        this.caseResultRepository = caseResultRepository;
        this.artifactRepository = artifactRepository;
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
        deleteReportObjects(tasks);

        if (!taskIds.isEmpty()) {
            caseResultRepository.deleteAllByTaskIdIn(taskIds);
            artifactRepository.deleteAllByTaskIdIn(taskIds);
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

    private void deleteReportObjects(List<TaskEntity> tasks) {
        for (TaskEntity task : tasks) {
            String objectKey = extractObjectKey(task.getReportUrl());
            if (objectKey != null) {
                objectStorageService.deleteObject(storageBucket, objectKey);
            }
        }
    }

    private String extractObjectKey(String reportUrl) {
        if (reportUrl == null || reportUrl.isBlank()) {
            return null;
        }
        String marker = "/" + storageBucket + "/";
        int markerIndex = reportUrl.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        String objectKey = reportUrl.substring(markerIndex + marker.length());
        return objectKey.isBlank() ? null : objectKey;
    }
}
