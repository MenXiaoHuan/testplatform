package com.example.platform.task;

import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.task.mapper.TaskMapper;
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
class TaskMapperTest {
    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TestRepositoryMapper repositoryMapper;

    @Autowired
    private SceneMapper sceneMapper;

    @Test
    void shouldInsertFindUpdateAndPageTasks() {
        Long repoId = TaskMapperTestSupport.insertRepository(repositoryMapper, "task-page-repo");
        Long sceneId = TaskMapperTestSupport.insertScene(repositoryMapper, sceneMapper, "task-page-scene-repo", "task-page-scene");
        TaskEntity first = TaskMapperTestSupport.task(repoId, sceneId, "QUEUED", "main",
                LocalDateTime.of(2026, 6, 18, 9, 0));
        TaskEntity second = TaskMapperTestSupport.task(repoId, sceneId, "RUNNING", "feature",
                LocalDateTime.of(2026, 6, 18, 9, 1));

        taskMapper.insert(first);
        taskMapper.insert(second);

        assertThat(first.getId()).isNotNull();
        assertThat(taskMapper.findById(first.getId())).isPresent();
        assertThat(taskMapper.findPage(10, 0)).extracting(TaskEntity::getId).contains(second.getId(), first.getId());
        assertThat(taskMapper.countAll()).isGreaterThanOrEqualTo(2);
        assertThat(taskMapper.findBySceneIdPage(sceneId, 10, 0)).extracting(TaskEntity::getId)
                .contains(second.getId(), first.getId());
        assertThat(taskMapper.countBySceneId(sceneId)).isEqualTo(2);

        first.setStatus("PASSED");
        first.setCurrentStage("finished");
        first.setResultCode("OK");
        first.setResultMessage("all good");
        first.setCancelRequested(true);
        first.setCancelRequestedAt(LocalDateTime.of(2026, 6, 18, 9, 2));
        first.setCancelRequestedBy("tester");
        first.setStartedAt(LocalDateTime.of(2026, 6, 18, 9, 3));
        first.setFinishedAt(LocalDateTime.of(2026, 6, 18, 9, 4));
        first.setDurationMs(60_000L);
        taskMapper.update(first);

        TaskEntity updated = taskMapper.findById(first.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("PASSED");
        assertThat(updated.getCurrentStage()).isEqualTo("finished");
        assertThat(updated.getCancelRequested()).isTrue();
        assertThat(updated.getDurationMs()).isEqualTo(60_000L);
    }

    @Test
    void shouldCoverLegacyTaskQueryMethods() {
        Long repoId = TaskMapperTestSupport.insertRepository(repositoryMapper, "task-query-repo");
        Long sceneId = TaskMapperTestSupport.insertScene(repositoryMapper, sceneMapper, "task-query-scene-repo", "task-query-scene");
        TaskEntity queued = TaskMapperTestSupport.task(repoId, sceneId, "QUEUED", "main",
                LocalDateTime.of(2026, 6, 18, 8, 0));
        TaskEntity running = TaskMapperTestSupport.task(repoId, sceneId, "RUNNING", "main",
                LocalDateTime.of(2026, 6, 18, 8, 1));
        TaskEntity passed = TaskMapperTestSupport.task(repoId, sceneId, "PASSED", "main",
                LocalDateTime.of(2026, 6, 18, 8, 2));
        taskMapper.insert(queued);
        taskMapper.insert(running);
        taskMapper.insert(passed);

        assertThat(taskMapper.findAllBySceneIdOrderByCreatedAtDescIdDesc(sceneId)).extracting(TaskEntity::getId)
                .containsExactly(passed.getId(), running.getId(), queued.getId());
        assertThat(taskMapper.findAllByStatusInOrderByCreatedAtAscIdAsc(List.of("QUEUED", "RUNNING")))
                .extracting(TaskEntity::getId)
                .containsExactly(queued.getId(), running.getId());
        assertThat(taskMapper.findAllByOrderByCreatedAtDescIdDesc()).extracting(TaskEntity::getId)
                .contains(passed.getId(), running.getId(), queued.getId());
        assertThat(taskMapper.findAllByRepoIdOrderByIdAsc(repoId)).extracting(TaskEntity::getId)
                .containsExactly(queued.getId(), running.getId(), passed.getId());
        assertThat(taskMapper.findAllBySceneIdOrderByIdAsc(sceneId)).extracting(TaskEntity::getId)
                .containsExactly(queued.getId(), running.getId(), passed.getId());
        assertThat(taskMapper.findFirstBySceneIdOrderByCreatedAtDescIdDesc(sceneId)).map(TaskEntity::getId)
                .contains(passed.getId());
        assertThat(taskMapper.existsBySceneIdAndStatusIn(sceneId, List.of("QUEUED", "RUNNING"))).isTrue();
        assertThat(taskMapper.existsBySceneIdAndStatusIn(sceneId, List.of("CANCELLED"))).isFalse();
    }

    @Test
    void shouldDeleteTasksByRepoAndScene() {
        Long repoId = TaskMapperTestSupport.insertRepository(repositoryMapper, "task-delete-repo");
        Long sceneId = TaskMapperTestSupport.insertScene(repositoryMapper, sceneMapper, "task-delete-scene-repo", "task-delete-scene");
        TaskEntity byRepo = TaskMapperTestSupport.task(repoId, sceneId, "QUEUED", "main", LocalDateTime.now());
        TaskEntity byScene = TaskMapperTestSupport.task(repoId, sceneId, "RUNNING", "main", LocalDateTime.now());
        taskMapper.insert(byRepo);
        taskMapper.insert(byScene);

        assertThat(taskMapper.deleteAllByRepoId(repoId)).isEqualTo(2);
        assertThat(taskMapper.findAllByRepoIdOrderByIdAsc(repoId)).isEmpty();

        TaskEntity remaining = TaskMapperTestSupport.task(repoId, sceneId, "QUEUED", "main", LocalDateTime.now());
        taskMapper.insert(remaining);
        assertThat(taskMapper.deleteAllBySceneId(sceneId)).isEqualTo(1);
        assertThat(taskMapper.findAllBySceneIdOrderByIdAsc(sceneId)).isEmpty();
    }
}
