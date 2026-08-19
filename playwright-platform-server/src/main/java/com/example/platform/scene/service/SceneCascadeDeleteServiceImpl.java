package com.example.platform.scene.service;

import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.mapper.SceneScheduleStateMapper;
import com.example.platform.scene.mapper.ScheduleEventMapper;
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

/**
 * 场景级联删除服务实现类 —— 场景删除时清理所有关联数据与对象存储文件。
 *
 * <p>删除顺序：先删对象存储文件 → 再删数据库记录（制品、阶段日志、用例结果、调度事件、任务、场景）。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #deleteSceneGraph} —— 级联删除场景及其所有关联数据</li>
 *   <li>{@link #deleteArtifactObjects} —— 删除制品关联的对象存储文件</li>
 *   <li>{@link #deleteStageLogObjects} —— 删除阶段日志关联的对象存储文件</li>
 * </ul>
 *
 * <p>依赖：{@link SceneMapper}、{@link ScheduleEventMapper}、{@link SceneScheduleStateMapper}、
 * {@link com.example.platform.task.mapper.TaskMapper}、{@link ObjectStorageService}。
 */
@Service
public class SceneCascadeDeleteServiceImpl implements SceneCascadeDeleteService {
    private final SceneMapper sceneMapper;
    private final ScheduleEventMapper scheduleEventMapper;
    private final SceneScheduleStateMapper sceneScheduleStateMapper;
    private final TaskMapper taskRepository;
    private final CaseResultMapper caseResultRepository;
    private final ArtifactMapper artifactRepository;
    private final TaskStageLogMapper taskStageLogRepository;
    private final ObjectStorageService objectStorageService;
    private final String storageBucket;

    public SceneCascadeDeleteServiceImpl(
            SceneMapper sceneMapper,
            ScheduleEventMapper scheduleEventMapper,
            SceneScheduleStateMapper sceneScheduleStateMapper,
            TaskMapper taskRepository,
            CaseResultMapper caseResultRepository,
            ArtifactMapper artifactRepository,
            TaskStageLogMapper taskStageLogRepository,
            ObjectStorageService objectStorageService,
            @Value("${platform.storage.bucket}") String storageBucket) {
        this.sceneMapper = sceneMapper;
        this.scheduleEventMapper = scheduleEventMapper;
        this.sceneScheduleStateMapper = sceneScheduleStateMapper;
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

        scheduleEventMapper.deleteAllBySceneId(sceneId);
        sceneScheduleStateMapper.deleteBySceneId(sceneId);
        taskRepository.deleteAllBySceneId(sceneId);
        sceneMapper.deleteById(sceneId);
    }

    /** 删除指定任务 ID 列表关联的制品对象存储文件。 */
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

    /** 删除指定任务 ID 列表关联的阶段日志对象存储文件。 */
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
