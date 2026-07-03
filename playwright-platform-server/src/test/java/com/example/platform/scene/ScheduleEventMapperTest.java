package com.example.platform.scene;

import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.mapper.ScheduleEventMapper;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.model.ScheduleEventEntity;
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
class ScheduleEventMapperTest {
    @Autowired
    private ScheduleEventMapper mapper;

    @Autowired
    private SceneMapper sceneMapper;

    @Autowired
    private TestRepositoryMapper repositoryMapper;

    @Test
    void shouldInsertFindAndUpdateScheduleEvent() {
        Long sceneId = insertScene("schedule-event-scene");
        LocalDateTime plannedFireAt = LocalDateTime.of(2026, 6, 18, 10, 0);
        ScheduleEventEntity entity = new ScheduleEventEntity();
        entity.setSceneId(sceneId);
        entity.setPlannedFireAt(plannedFireAt);
        entity.setStatus("ACQUIRED");
        entity.setTriggerReason("cron:0 */5 * * * *");
        entity.setRetryCount(0);

        mapper.insert(entity);

        ScheduleEventEntity existing = mapper.findBySceneIdAndPlannedFireAt(sceneId, plannedFireAt).orElseThrow();
        assertThat(existing.getStatus()).isEqualTo("ACQUIRED");
        assertThat(existing.getTriggerReason()).isEqualTo("cron:0 */5 * * * *");

        existing.setStatus("TASK_CREATED");
        existing.setTaskId(101L);
        mapper.update(existing);

        ScheduleEventEntity updated = mapper.findById(existing.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("TASK_CREATED");
        assertThat(updated.getTaskId()).isEqualTo(101L);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldFindRetryableFailedEventsOrderedByCreatedAt() {
        Long sceneId = insertScene("retryable-failed-scene");
        ScheduleEventEntity first = new ScheduleEventEntity();
        first.setSceneId(sceneId);
        first.setPlannedFireAt(LocalDateTime.of(2026, 6, 18, 10, 0));
        first.setStatus("FAILED");
        first.setTriggerReason("cron:0 */5 * * * *");
        first.setRetryCount(1);
        first.setNextRetryAt(LocalDateTime.of(2026, 6, 18, 10, 1));
        mapper.insert(first);

        ScheduleEventEntity second = new ScheduleEventEntity();
        second.setSceneId(sceneId);
        second.setPlannedFireAt(LocalDateTime.of(2026, 6, 18, 10, 5));
        second.setStatus("FAILED");
        second.setTriggerReason("cron:0 */5 * * * *");
        second.setRetryCount(3);
        second.setNextRetryAt(LocalDateTime.of(2026, 6, 18, 10, 7));
        mapper.insert(second);

        List<ScheduleEventEntity> events = mapper.findRetryableFailedEvents(
                10,
                LocalDateTime.of(2026, 6, 18, 10, 2),
                3);

        assertThat(events).extracting(ScheduleEventEntity::getId).containsExactly(first.getId());
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
