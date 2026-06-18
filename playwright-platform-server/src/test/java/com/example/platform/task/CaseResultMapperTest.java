package com.example.platform.task;

import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.task.mapper.CaseResultMapper;
import com.example.platform.task.mapper.TaskMapper;
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
class CaseResultMapperTest {
    @Autowired
    private CaseResultMapper caseResultMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TestRepositoryMapper repositoryMapper;

    @Autowired
    private SceneMapper sceneMapper;

    @Test
    void shouldInsertFindByTaskAndDeleteByTaskIds() {
        Long taskId = insertTask("case-result-task-repo", "case-result-scene");
        CaseResultEntity passed = TaskMapperTestSupport.caseResult(taskId, "suite should pass", "passed");
        CaseResultEntity failed = TaskMapperTestSupport.caseResult(taskId, "suite should fail", "failed");

        caseResultMapper.insert(passed);
        caseResultMapper.insert(failed);

        assertThat(passed.getId()).isNotNull();
        assertThat(caseResultMapper.findAllByTaskIdOrderByIdAsc(taskId)).extracting(CaseResultEntity::getFullName)
                .containsExactly("suite should pass", "suite should fail");
        assertThat(caseResultMapper.deleteAllByTaskIdIn(List.of(taskId))).isEqualTo(2);
        assertThat(caseResultMapper.findAllByTaskIdOrderByIdAsc(taskId)).isEmpty();
    }

    private Long insertTask(String repoName, String sceneName) {
        Long repoId = TaskMapperTestSupport.insertRepository(repositoryMapper, repoName);
        Long sceneId = TaskMapperTestSupport.insertScene(repositoryMapper, sceneMapper, repoName + "-scene-repo", sceneName);
        TaskEntity task = TaskMapperTestSupport.task(repoId, sceneId, "PASSED", "main", LocalDateTime.now());
        taskMapper.insert(task);
        return task.getId();
    }
}
