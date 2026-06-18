package com.example.platform.task;

import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.task.mapper.ArtifactMapper;
import com.example.platform.task.mapper.CaseResultMapper;
import com.example.platform.task.mapper.TaskMapper;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.CaseResultEntity;
import com.example.platform.task.model.TaskEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ArtifactMapperTest {
    @Autowired
    private ArtifactMapper artifactMapper;

    @Autowired
    private CaseResultMapper caseResultMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TestRepositoryMapper repositoryMapper;

    @Autowired
    private SceneMapper sceneMapper;

    @Test
    void shouldInsertFindByTaskFindByCaseResultFindByTaskIdsAndDeleteByTaskIds() {
        Long taskId = insertTask("artifact-task-repo", "artifact-scene");
        CaseResultEntity caseResult = TaskMapperTestSupport.caseResult(taskId, "suite should attach artifact", "passed");
        caseResultMapper.insert(caseResult);
        ArtifactEntity trace = TaskMapperTestSupport.artifact(taskId, caseResult.getId(), "trace.zip");
        ArtifactEntity video = TaskMapperTestSupport.artifact(taskId, null, "video.webm");

        artifactMapper.insert(trace);
        artifactMapper.insert(video);

        assertThat(trace.getId()).isNotNull();
        assertThat(artifactMapper.findAllByTaskIdOrderByIdAsc(taskId)).extracting(ArtifactEntity::getObjectKey)
                .containsExactly("trace.zip", "video.webm");
        assertThat(artifactMapper.findAllByCaseResultIdOrderByIdAsc(caseResult.getId())).extracting(ArtifactEntity::getObjectKey)
                .containsExactly("trace.zip");
        assertThat(artifactMapper.findAllByTaskIdIn(List.of(taskId))).extracting(ArtifactEntity::getObjectKey)
                .containsExactly("trace.zip", "video.webm");
        assertThat(artifactMapper.deleteAllByTaskIdIn(List.of(taskId))).isEqualTo(2);
        assertThat(artifactMapper.findAllByTaskIdOrderByIdAsc(taskId)).isEmpty();
    }

    private Long insertTask(String repoName, String sceneName) {
        Long repoId = TaskMapperTestSupport.insertRepository(repositoryMapper, repoName);
        Long sceneId = TaskMapperTestSupport.insertScene(repositoryMapper, sceneMapper, repoName + "-scene-repo", sceneName);
        TaskEntity task = TaskMapperTestSupport.task(repoId, sceneId, "PASSED", "main", LocalDateTime.now());
        taskMapper.insert(task);
        return task.getId();
    }
}
