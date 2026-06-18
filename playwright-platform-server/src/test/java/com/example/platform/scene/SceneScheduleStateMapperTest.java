package com.example.platform.scene;

import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.mapper.SceneScheduleStateMapper;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.model.SceneScheduleStateEntity;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SceneScheduleStateMapperTest {
    @Autowired
    private SceneScheduleStateMapper mapper;

    @Autowired
    private SceneMapper sceneMapper;

    @Autowired
    private TestRepositoryMapper repositoryMapper;

    @Test
    void shouldInsertFindAndUpdateScheduleState() {
        Long sceneId = insertScene("state-scene");
        SceneScheduleStateEntity state = new SceneScheduleStateEntity();
        state.setSceneId(sceneId);
        state.setLastPlannedFireAt(LocalDateTime.of(2026, 6, 18, 10, 0));
        state.setLastTriggeredAt(LocalDateTime.of(2026, 6, 18, 10, 1));
        state.setLeaseOwner("local-scheduler");
        state.setLeaseUntil(LocalDateTime.of(2026, 6, 18, 10, 2));
        state.setVersion(0L);

        mapper.insert(state);

        SceneScheduleStateEntity existing = mapper.findBySceneId(sceneId).orElseThrow();
        assertThat(existing.getLastPlannedFireAt()).isEqualTo(LocalDateTime.of(2026, 6, 18, 10, 0));
        assertThat(existing.getLastTriggeredAt()).isEqualTo(LocalDateTime.of(2026, 6, 18, 10, 1));
        assertThat(existing.getLeaseOwner()).isEqualTo("local-scheduler");
        assertThat(existing.getVersion()).isZero();

        existing.setLastTaskId(99L);
        existing.setLeaseOwner("next-scheduler");
        existing.setVersion(1L);
        mapper.update(existing);

        SceneScheduleStateEntity updated = mapper.findBySceneId(sceneId).orElseThrow();
        assertThat(updated.getLastTaskId()).isEqualTo(99L);
        assertThat(updated.getLeaseOwner()).isEqualTo("next-scheduler");
        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldReturnEmptyWhenScheduleStateMissing() {
        Long sceneId = insertScene("missing-state-scene");

        assertThat(mapper.findBySceneId(sceneId)).isEmpty();
    }

    private Long insertScene(String name) {
        TestRepositoryEntity repository = new TestRepositoryEntity();
        repository.setName(name + "-repo");
        repository.setGitUrl("https://github.com/demo/testframe.git");
        repository.setDefaultBranch("main");
        repository.setWorkingDirectory("playwright_framework");
        repository.setInstallCommand("npm ci");
        repository.setRunCommandTemplate("npm run test:e2e --");
        repository.setTestRoot("tests");
        repository.setResultsIndexRelativePath("test-results/.playwright-results.json");
        repository.setArtifactRootRelativePath(".playwright-artifacts");
        repository.setEnabled(true);
        repositoryMapper.insert(repository);

        SceneEntity scene = new SceneEntity();
        scene.setRepoId(repository.getId());
        scene.setName(name);
        scene.setDescription("demo scene");
        scene.setBranch("main");
        scene.setTestSelectorType("SPEC");
        scene.setTestSelectorValue("tests/demo.spec.ts");
        scene.setProjectName("chromium");
        scene.setBrowser("chromium");
        scene.setRunCommand("npm run test:e2e -- tests/demo.spec.ts");
        scene.setScheduleEnabled(true);
        scene.setCronExpression("0/5 * * * *");
        scene.setNextRunAt(LocalDateTime.of(2026, 6, 18, 10, 0));
        sceneMapper.insert(scene);
        return scene.getId();
    }
}
