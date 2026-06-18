package com.example.platform.scene.service;

import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.mapper.ArtifactMapper;
import com.example.platform.task.mapper.CaseResultMapper;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.mapper.TaskMapper;
import com.example.platform.task.model.TaskStageLogEntity;
import com.example.platform.task.mapper.TaskStageLogMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SceneCascadeDeleteServiceImpl implements SceneCascadeDeleteService {
    private final SceneMapper sceneMapper;
    private final TaskMapper taskRepository;
    private final CaseResultMapper caseResultRepository;
    private final ArtifactMapper artifactRepository;
    private final TaskStageLogMapper taskStageLogRepository;
    private final ObjectStorageService objectStorageService;
    private final String storageBucket;

    public SceneCascadeDeleteServiceImpl(
            SceneMapper sceneMapper,
            TaskMapper taskRepository,
            CaseResultMapper caseResultRepository,
            ArtifactMapper artifactRepository,
            TaskStageLogMapper taskStageLogRepository,
            ObjectStorageService objectStorageService,
            @Value("${platform.storage.bucket}") String storageBucket) {
        this.sceneMapper = sceneMapper;
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
        sceneMapper.findById(sceneId)
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
        sceneMapper.deleteById(sceneId);
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
