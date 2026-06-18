package com.example.platform.task;

import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.task.mapper.TaskMapper;
import com.example.platform.task.mapper.TaskStageLogMapper;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.model.TaskStageLogEntity;
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
class TaskStageLogMapperTest {
    @Autowired
    private TaskStageLogMapper stageLogMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TestRepositoryMapper repositoryMapper;

    @Autowired
    private SceneMapper sceneMapper;

    @Test
    void shouldInsertFindByTaskFindByTaskIdsAndDeleteByTaskIds() {
        Long taskId = insertTask("stage-log-task-repo", "stage-log-scene");
        TaskStageLogEntity installLog = TaskMapperTestSupport.stageLog(taskId, "install", "install.log");
        TaskStageLogEntity runLog = TaskMapperTestSupport.stageLog(taskId, "run", "run.log");

        stageLogMapper.insert(installLog);
        stageLogMapper.insert(runLog);

        assertThat(installLog.getId()).isNotNull();
        assertThat(stageLogMapper.findAllByTaskIdOrderByIdAsc(taskId)).extracting(TaskStageLogEntity::getObjectKey)
                .containsExactly("install.log", "run.log");
        assertThat(stageLogMapper.findAllByTaskIdIn(List.of(taskId))).extracting(TaskStageLogEntity::getStage)
                .containsExactly("install", "run");
        assertThat(stageLogMapper.deleteAllByTaskIdIn(List.of(taskId))).isEqualTo(2);
        assertThat(stageLogMapper.findAllByTaskIdOrderByIdAsc(taskId)).isEmpty();
    }

    private Long insertTask(String repoName, String sceneName) {
        Long repoId = TaskMapperTestSupport.insertRepository(repositoryMapper, repoName);
        Long sceneId = TaskMapperTestSupport.insertScene(repositoryMapper, sceneMapper, repoName + "-scene-repo", sceneName);
        TaskEntity task = TaskMapperTestSupport.task(repoId, sceneId, "PASSED", "main", LocalDateTime.now());
        taskMapper.insert(task);
        return task.getId();
    }
}
