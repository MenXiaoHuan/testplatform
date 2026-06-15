package com.example.platform.scene;

import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.model.SceneJpaRepository;
import com.example.platform.scene.service.SceneCascadeDeleteServiceImpl;
import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.ArtifactJpaRepository;
import com.example.platform.task.model.CaseResultJpaRepository;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.model.TaskJpaRepository;
import com.example.platform.task.model.TaskStageLogEntity;
import com.example.platform.task.model.TaskStageLogJpaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SceneCascadeDeleteServiceImplTest {
    @Test
    void shouldDeleteSceneGraphAndStorageObjects() {
        SceneJpaRepository sceneRepository = Mockito.mock(SceneJpaRepository.class);
        TaskJpaRepository taskRepository = Mockito.mock(TaskJpaRepository.class);
        CaseResultJpaRepository caseResultRepository = Mockito.mock(CaseResultJpaRepository.class);
        ArtifactJpaRepository artifactRepository = Mockito.mock(ArtifactJpaRepository.class);
        TaskStageLogJpaRepository taskStageLogRepository = Mockito.mock(TaskStageLogJpaRepository.class);
        ObjectStorageService objectStorageService = Mockito.mock(ObjectStorageService.class);

        SceneEntity scene = new SceneEntity();
        scene.setId(11L);
        scene.setRepoId(1L);

        TaskEntity task = new TaskEntity();
        task.setId(101L);
        task.setSceneId(11L);

        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setTaskId(101L);
        artifact.setBucket("qa-report");
        artifact.setObjectKey("runs/101/artifacts/trace.zip");

        TaskStageLogEntity stageLog = new TaskStageLogEntity();
        stageLog.setTaskId(101L);
        stageLog.setObjectKey("runs/101/logs/testing.log");

        Mockito.when(sceneRepository.findById(11L)).thenReturn(Optional.of(scene));
        Mockito.when(taskRepository.findAllBySceneIdOrderByIdAsc(11L)).thenReturn(List.of(task));
        Mockito.when(artifactRepository.findAllByTaskIdIn(List.of(101L))).thenReturn(List.of(artifact));
        Mockito.when(taskStageLogRepository.findAllByTaskIdIn(List.of(101L))).thenReturn(List.of(stageLog));

        SceneCascadeDeleteServiceImpl service = new SceneCascadeDeleteServiceImpl(
                sceneRepository,
                taskRepository,
                caseResultRepository,
                artifactRepository,
                taskStageLogRepository,
                objectStorageService,
                "qa-report");

        service.deleteSceneGraph(11L);

        Mockito.verify(objectStorageService).deleteObject("qa-report", "runs/101/artifacts/trace.zip");
        Mockito.verify(objectStorageService).deleteObject("qa-report", "runs/101/logs/testing.log");
        Mockito.verify(caseResultRepository).deleteAllByTaskIdIn(List.of(101L));
        Mockito.verify(artifactRepository).deleteAllByTaskIdIn(List.of(101L));
        Mockito.verify(taskStageLogRepository).deleteAllByTaskIdIn(List.of(101L));
        Mockito.verify(taskRepository).deleteAllBySceneId(11L);
        Mockito.verify(sceneRepository).delete(scene);
    }

    @Test
    void shouldDeleteSceneWithoutTasks() {
        SceneJpaRepository sceneRepository = Mockito.mock(SceneJpaRepository.class);
        TaskJpaRepository taskRepository = Mockito.mock(TaskJpaRepository.class);
        CaseResultJpaRepository caseResultRepository = Mockito.mock(CaseResultJpaRepository.class);
        ArtifactJpaRepository artifactRepository = Mockito.mock(ArtifactJpaRepository.class);
        TaskStageLogJpaRepository taskStageLogRepository = Mockito.mock(TaskStageLogJpaRepository.class);
        ObjectStorageService objectStorageService = Mockito.mock(ObjectStorageService.class);

        SceneEntity scene = new SceneEntity();
        scene.setId(12L);

        Mockito.when(sceneRepository.findById(12L)).thenReturn(Optional.of(scene));
        Mockito.when(taskRepository.findAllBySceneIdOrderByIdAsc(12L)).thenReturn(List.of());

        SceneCascadeDeleteServiceImpl service = new SceneCascadeDeleteServiceImpl(
                sceneRepository,
                taskRepository,
                caseResultRepository,
                artifactRepository,
                taskStageLogRepository,
                objectStorageService,
                "qa-report");

        service.deleteSceneGraph(12L);

        Mockito.verifyNoInteractions(objectStorageService);
        Mockito.verify(caseResultRepository, Mockito.never()).deleteAllByTaskIdIn(Mockito.anyList());
        Mockito.verify(artifactRepository, Mockito.never()).deleteAllByTaskIdIn(Mockito.anyList());
        Mockito.verify(taskStageLogRepository, Mockito.never()).deleteAllByTaskIdIn(Mockito.anyList());
        Mockito.verify(taskRepository).deleteAllBySceneId(12L);
        Mockito.verify(sceneRepository).delete(scene);
    }
}
