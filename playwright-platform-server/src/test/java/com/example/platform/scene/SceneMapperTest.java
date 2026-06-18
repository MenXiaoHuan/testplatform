package com.example.platform.scene;

import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.model.SceneEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest(properties = "mybatis.mapper-locations=classpath*:mapper/**/*.xml")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SceneMapperTest {
    @Autowired
    private SceneMapper sceneMapper;

    @Autowired
    private TestRepositoryMapper repositoryMapper;

    @Test
    void shouldInsertFindUpdateAndDetectDuplicateNameIgnoringCase() {
        Long repoId = insertRepository("scene-repo");
        SceneEntity scene = scene(repoId, "Checkout Scene", true, "0/5 * * * *", LocalDateTime.of(2026, 6, 18, 10, 0));

        sceneMapper.insert(scene);

        assertThat(scene.getId()).isNotNull();
        assertThat(sceneMapper.findById(scene.getId())).isPresent();
        assertThat(sceneMapper.existsByNameIgnoreCase("checkout scene")).isTrue();
        assertThat(sceneMapper.existsByNameIgnoreCaseAndIdNot("checkout scene", scene.getId())).isFalse();

        scene.setDescription("updated description");
        scene.setLastTaskStatus("PASSED");
        sceneMapper.update(scene);

        SceneEntity updated = sceneMapper.findById(scene.getId()).orElseThrow();
        assertThat(updated.getDescription()).isEqualTo("updated description");
        assertThat(updated.getLastTaskStatus()).isEqualTo("PASSED");
    }

    @Test
    void shouldFindRepoScenesDueSchedulesAndNullNextRunSchedules() {
        Long repoId = insertRepository("schedule-repo");
        LocalDateTime now = LocalDateTime.of(2026, 6, 18, 10, 0);
        SceneEntity due = scene(repoId, "due-scene", true, "0/5 * * * *", now.minusMinutes(1));
        SceneEntity future = scene(repoId, "future-scene", true, "0/5 * * * *", now.plusMinutes(1));
        SceneEntity disabled = scene(repoId, "disabled-scene", false, "0/5 * * * *", now.minusMinutes(1));
        SceneEntity unscheduled = scene(repoId, "unscheduled-scene", true, "0/5 * * * *", null);
        sceneMapper.insert(due);
        sceneMapper.insert(future);
        sceneMapper.insert(disabled);
        sceneMapper.insert(unscheduled);

        List<SceneEntity> repoScenes = sceneMapper.findAllByRepoId(repoId);
        List<SceneEntity> dueScenes = sceneMapper.findDueScheduledScenes(now);
        List<SceneEntity> nullNextRunScenes = sceneMapper.findAllByScheduleEnabledTrueAndNextRunAtIsNullOrderByIdAsc();

        assertThat(sceneMapper.countAll()).isGreaterThanOrEqualTo(4);
        assertThat(repoScenes).extracting(SceneEntity::getName)
                .containsExactly("due-scene", "future-scene", "disabled-scene", "unscheduled-scene");
        assertThat(dueScenes).extracting(SceneEntity::getName).containsExactly("due-scene");
        assertThat(sceneMapper.findAllByScheduleEnabledTrue()).extracting(SceneEntity::getName)
                .contains("due-scene", "future-scene", "unscheduled-scene")
                .doesNotContain("disabled-scene");
        assertThat(nullNextRunScenes).extracting(SceneEntity::getName).contains("unscheduled-scene");
    }

    @Test
    void shouldDeleteScenesByIdAndRepoId() {
        Long repoId = insertRepository("delete-scene-repo");
        SceneEntity first = scene(repoId, "delete-first", false, null, null);
        SceneEntity second = scene(repoId, "delete-second", false, null, null);
        sceneMapper.insert(first);
        sceneMapper.insert(second);

        assertThat(sceneMapper.deleteById(first.getId())).isEqualTo(1);
        assertThat(sceneMapper.findById(first.getId())).isEmpty();
        assertThat(sceneMapper.deleteAllByRepoId(repoId)).isEqualTo(1);
        assertThat(sceneMapper.findAllByRepoId(repoId)).isEmpty();
    }

    private Long insertRepository(String name) {
        TestRepositoryEntity entity = new TestRepositoryEntity();
        entity.setName(name);
        entity.setGitUrl("https://github.com/demo/testframe.git");
        entity.setDefaultBranch("main");
        entity.setWorkingDirectory("playwright_framework");
        entity.setInstallCommand("npm ci");
        entity.setRunCommandTemplate("npm run test:e2e --");
        entity.setTestRoot("tests");
        entity.setResultsIndexRelativePath("test-results/.playwright-results.json");
        entity.setArtifactRootRelativePath(".playwright-artifacts");
        entity.setEnabled(true);
        repositoryMapper.insert(entity);
        return entity.getId();
    }

    private SceneEntity scene(Long repoId, String name, boolean scheduleEnabled, String cronExpression, LocalDateTime nextRunAt) {
        SceneEntity entity = new SceneEntity();
        entity.setRepoId(repoId);
        entity.setName(name);
        entity.setDescription("demo scene");
        entity.setBranch("main");
        entity.setTestSelectorType("SPEC");
        entity.setTestSelectorValue("tests/demo.spec.ts");
        entity.setProjectName("chromium");
        entity.setBrowser("chromium");
        entity.setEnvJson("{\"BASE_URL\":\"http://localhost\"}");
        entity.setRunCommand("npm run test:e2e -- tests/demo.spec.ts");
        entity.setScheduleEnabled(scheduleEnabled);
        entity.setCronExpression(cronExpression);
        entity.setNextRunAt(nextRunAt);
        return entity;
    }
}
