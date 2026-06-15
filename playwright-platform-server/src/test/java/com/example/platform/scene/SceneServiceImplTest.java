package com.example.platform.scene;

import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.model.TestRepositoryJpaRepository;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.model.SceneJpaRepository;
import com.example.platform.scene.service.SceneCascadeDeleteService;
import com.example.platform.scene.service.SceneScheduleLeaseService;
import com.example.platform.scene.service.SceneSchedulerServiceImpl;
import com.example.platform.scene.service.SceneServiceImpl;
import com.example.platform.task.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SceneServiceImplTest {
    @Test
    void shouldUpdateDescriptionAndScheduleFields() {
        SceneJpaRepository repository = Mockito.mock(SceneJpaRepository.class);
        TestRepositoryJpaRepository repositoryJpaRepository = Mockito.mock(TestRepositoryJpaRepository.class);
        SceneCascadeDeleteService sceneCascadeDeleteService = Mockito.mock(SceneCascadeDeleteService.class);
        SceneServiceImpl service = new SceneServiceImpl(repository, repositoryJpaRepository, sceneCascadeDeleteService, new ObjectMapper());

        SceneEntity existing = new SceneEntity();
        existing.setId(1L);
        existing.setRepoId(1L);
        existing.setName("checkout smoke");
        existing.setDescription("old description");
        existing.setBranch("main");
        existing.setTestSelectorType("grep");
        existing.setTestSelectorValue("@smoke");
        existing.setRunCommand("npm run test:e2e");
        existing.setScheduleEnabled(false);
        existing.setCronExpression(null);

        SceneEntity update = new SceneEntity();
        update.setRepoId(2L);
        update.setName("checkout nightly");
        update.setDescription("nightly checkout coverage");
        update.setBranch("release");
        update.setTestSelectorType("file");
        update.setTestSelectorValue("tests/checkout.spec.ts");
        update.setRunCommand("npm run test:e2e -- --project chromium");
        update.setScheduleEnabled(true);
        update.setCronExpression("0 0/30 * * * ?");

        TestRepositoryEntity targetRepository = new TestRepositoryEntity();
        targetRepository.setId(2L);
        targetRepository.setEnabled(true);

        Mockito.when(repository.findById(1L)).thenReturn(Optional.of(existing));
        Mockito.when(repositoryJpaRepository.findById(2L)).thenReturn(Optional.of(targetRepository));
        Mockito.when(repository.save(existing)).thenReturn(existing);

        SceneEntity result = service.update(1L, update);

        assertThat(result.getRepoId()).isEqualTo(2L);
        assertThat(result.getName()).isEqualTo("checkout nightly");
        assertThat(result.getDescription()).isEqualTo("nightly checkout coverage");
        assertThat(result.getBranch()).isEqualTo("release");
        assertThat(result.getTestSelectorType()).isEqualTo("file");
        assertThat(result.getTestSelectorValue()).isEqualTo("tests/checkout.spec.ts");
        assertThat(result.getRunCommand()).isEqualTo("npm run test:e2e -- --project chromium");
        assertThat(result.getScheduleEnabled()).isTrue();
        assertThat(result.getCronExpression()).isEqualTo("0 0/30 * * * ?");
        assertThat(result.getNextRunAt()).isNotNull();
    }

    @Test
    void shouldCreateScheduledTaskWhenCronIsDueAndLeaseAcquired() {
        SceneJpaRepository repository = Mockito.mock(SceneJpaRepository.class);
        SceneScheduleLeaseService leaseService = Mockito.mock(SceneScheduleLeaseService.class);
        TaskService taskService = Mockito.mock(TaskService.class);
        SceneSchedulerServiceImpl service = new SceneSchedulerServiceImpl(repository, leaseService, taskService);

        SceneEntity scheduled = new SceneEntity();
        scheduled.setId(11L);
        scheduled.setScheduleEnabled(true);
        scheduled.setCronExpression("0 */5 * * * *");

        scheduled.setNextRunAt(LocalDateTime.of(2026, 6, 13, 10, 0));
        Mockito.when(repository.findAllByScheduleEnabledTrueAndNextRunAtIsNullOrderByIdAsc()).thenReturn(List.of());
        Mockito.when(repository.findDueScheduledScenes(LocalDateTime.of(2026, 6, 13, 10, 0, 30)))
                .thenReturn(List.of(scheduled));
        Mockito.when(leaseService.tryAcquire(11L, LocalDateTime.of(2026, 6, 13, 10, 0))).thenReturn(true);
        Mockito.when(repository.save(scheduled)).thenReturn(scheduled);

        service.triggerDueScenes(LocalDateTime.of(2026, 6, 13, 10, 0, 30));

        Mockito.verify(repository).findDueScheduledScenes(LocalDateTime.of(2026, 6, 13, 10, 0, 30));
        assertThat(scheduled.getNextRunAt()).isEqualTo(LocalDateTime.of(2026, 6, 13, 10, 5));
        Mockito.verify(taskService).createScheduledTask(11L, "cron:0 */5 * * * *");
    }

    @Test
    void shouldSkipScheduledTaskWhenLeaseIsRejected() {
        SceneJpaRepository repository = Mockito.mock(SceneJpaRepository.class);
        SceneScheduleLeaseService leaseService = Mockito.mock(SceneScheduleLeaseService.class);
        TaskService taskService = Mockito.mock(TaskService.class);
        SceneSchedulerServiceImpl service = new SceneSchedulerServiceImpl(repository, leaseService, taskService);

        SceneEntity scheduled = new SceneEntity();
        scheduled.setId(11L);
        scheduled.setScheduleEnabled(true);
        scheduled.setCronExpression("0 */5 * * * *");

        scheduled.setNextRunAt(LocalDateTime.of(2026, 6, 13, 10, 0));
        Mockito.when(repository.findAllByScheduleEnabledTrueAndNextRunAtIsNullOrderByIdAsc()).thenReturn(List.of());
        Mockito.when(repository.findDueScheduledScenes(LocalDateTime.of(2026, 6, 13, 10, 0, 30)))
                .thenReturn(List.of(scheduled));
        Mockito.when(leaseService.tryAcquire(11L, LocalDateTime.of(2026, 6, 13, 10, 0))).thenReturn(false);

        service.triggerDueScenes(LocalDateTime.of(2026, 6, 13, 10, 0, 30));

        Mockito.verify(taskService, Mockito.never()).createScheduledTask(Mockito.anyLong(), Mockito.anyString());
        Mockito.verify(repository, Mockito.never()).save(Mockito.any(SceneEntity.class));
    }

    @Test
    void shouldRejectDisabledRepositoryWhenCreatingScene() {
        SceneJpaRepository repository = Mockito.mock(SceneJpaRepository.class);
        TestRepositoryJpaRepository repositoryJpaRepository = Mockito.mock(TestRepositoryJpaRepository.class);
        SceneCascadeDeleteService sceneCascadeDeleteService = Mockito.mock(SceneCascadeDeleteService.class);
        SceneServiceImpl service = new SceneServiceImpl(repository, repositoryJpaRepository, sceneCascadeDeleteService, new ObjectMapper());

        SceneEntity scene = new SceneEntity();
        scene.setRepoId(7L);
        scene.setName("login");

        TestRepositoryEntity disabledRepository = new TestRepositoryEntity();
        disabledRepository.setId(7L);
        disabledRepository.setEnabled(false);

        Mockito.when(repositoryJpaRepository.findById(7L)).thenReturn(Optional.of(disabledRepository));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.create(scene));
        Mockito.verify(repository, Mockito.never()).save(Mockito.any(SceneEntity.class));
    }

    @Test
    void shouldNormalizeBlankEnvJsonToNullWhenCreatingScene() {
        SceneJpaRepository repository = Mockito.mock(SceneJpaRepository.class);
        TestRepositoryJpaRepository repositoryJpaRepository = Mockito.mock(TestRepositoryJpaRepository.class);
        SceneCascadeDeleteService sceneCascadeDeleteService = Mockito.mock(SceneCascadeDeleteService.class);
        SceneServiceImpl service = new SceneServiceImpl(repository, repositoryJpaRepository, sceneCascadeDeleteService, new ObjectMapper());

        SceneEntity scene = new SceneEntity();
        scene.setRepoId(9L);
        scene.setName("login");
        scene.setBranch("main");
        scene.setTestSelectorType("file");
        scene.setTestSelectorValue("interview_agent");
        scene.setBrowser("chromium");
        scene.setRunCommand("npx playwright test");
        scene.setEnvJson("");

        TestRepositoryEntity enabledRepository = new TestRepositoryEntity();
        enabledRepository.setId(9L);
        enabledRepository.setEnabled(true);

        Mockito.when(repositoryJpaRepository.findById(9L)).thenReturn(Optional.of(enabledRepository));
        Mockito.when(repository.save(Mockito.any(SceneEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SceneEntity result = service.create(scene);

        assertThat(result.getEnvJson()).isNull();
        assertThat(result.getNextRunAt()).isNull();
    }

    @Test
    void shouldRejectDuplicateSceneNameWhenCreating() {
        SceneJpaRepository repository = Mockito.mock(SceneJpaRepository.class);
        TestRepositoryJpaRepository repositoryJpaRepository = Mockito.mock(TestRepositoryJpaRepository.class);
        SceneCascadeDeleteService sceneCascadeDeleteService = Mockito.mock(SceneCascadeDeleteService.class);
        SceneServiceImpl service = new SceneServiceImpl(repository, repositoryJpaRepository, sceneCascadeDeleteService, new ObjectMapper());

        SceneEntity scene = new SceneEntity();
        scene.setRepoId(9L);
        scene.setName("login");

        TestRepositoryEntity enabledRepository = new TestRepositoryEntity();
        enabledRepository.setId(9L);
        enabledRepository.setEnabled(true);

        Mockito.when(repositoryJpaRepository.findById(9L)).thenReturn(Optional.of(enabledRepository));
        Mockito.when(repository.existsByNameIgnoreCase("login")).thenReturn(true);

        assertThatThrownBy(() -> service.create(scene))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("场景名称已存在，请更换后重试");
        Mockito.verify(repository, Mockito.never()).save(Mockito.any(SceneEntity.class));
    }

    @Test
    void shouldRejectDuplicateSceneNameWhenUpdating() {
        SceneJpaRepository repository = Mockito.mock(SceneJpaRepository.class);
        TestRepositoryJpaRepository repositoryJpaRepository = Mockito.mock(TestRepositoryJpaRepository.class);
        SceneCascadeDeleteService sceneCascadeDeleteService = Mockito.mock(SceneCascadeDeleteService.class);
        SceneServiceImpl service = new SceneServiceImpl(repository, repositoryJpaRepository, sceneCascadeDeleteService, new ObjectMapper());

        SceneEntity existing = new SceneEntity();
        existing.setId(1L);
        existing.setRepoId(9L);
        existing.setName("checkout");

        SceneEntity update = new SceneEntity();
        update.setRepoId(9L);
        update.setName("checkout");

        TestRepositoryEntity enabledRepository = new TestRepositoryEntity();
        enabledRepository.setId(9L);
        enabledRepository.setEnabled(true);

        Mockito.when(repository.findById(1L)).thenReturn(Optional.of(existing));
        Mockito.when(repositoryJpaRepository.findById(9L)).thenReturn(Optional.of(enabledRepository));
        Mockito.when(repository.existsByNameIgnoreCaseAndIdNot("checkout", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, update))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("场景名称已存在，请更换后重试");
        Mockito.verify(repository, Mockito.never()).save(Mockito.any(SceneEntity.class));
    }

    @Test
    void shouldInitializeNextRunAtForLegacyScheduledScene() {
        SceneJpaRepository repository = Mockito.mock(SceneJpaRepository.class);
        SceneScheduleLeaseService leaseService = Mockito.mock(SceneScheduleLeaseService.class);
        TaskService taskService = Mockito.mock(TaskService.class);
        SceneSchedulerServiceImpl service = new SceneSchedulerServiceImpl(repository, leaseService, taskService);

        SceneEntity legacyScene = new SceneEntity();
        legacyScene.setId(21L);
        legacyScene.setScheduleEnabled(true);
        legacyScene.setCronExpression("0 */10 * * * *");

        Mockito.when(repository.findAllByScheduleEnabledTrueAndNextRunAtIsNullOrderByIdAsc()).thenReturn(List.of(legacyScene));
        Mockito.when(repository.findDueScheduledScenes(LocalDateTime.of(2026, 6, 13, 10, 0, 30)))
                .thenReturn(List.of());
        Mockito.when(repository.save(legacyScene)).thenReturn(legacyScene);

        service.triggerDueScenes(LocalDateTime.of(2026, 6, 13, 10, 0, 30));

        assertThat(legacyScene.getNextRunAt()).isEqualTo(LocalDateTime.of(2026, 6, 13, 10, 10));
        Mockito.verify(repository).save(legacyScene);
    }

    @Test
    void shouldReturnPagedSceneCards() {
        SceneJpaRepository repository = Mockito.mock(SceneJpaRepository.class);
        TestRepositoryJpaRepository repositoryJpaRepository = Mockito.mock(TestRepositoryJpaRepository.class);
        SceneCascadeDeleteService sceneCascadeDeleteService = Mockito.mock(SceneCascadeDeleteService.class);
        SceneServiceImpl service = new SceneServiceImpl(repository, repositoryJpaRepository, sceneCascadeDeleteService, new ObjectMapper());

        SceneEntity scene = new SceneEntity();
        scene.setId(11L);
        scene.setRepoId(7L);
        scene.setName("login");
        scene.setDescription("smoke");
        scene.setBranch("main");
        scene.setScheduleEnabled(false);
        scene.setCronExpression(null);
        scene.setEnvJson("{\"foo\":\"bar\"}");

        Mockito.when(repository.findAll(Mockito.any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(scene), PageRequest.of(0, 10), 1));

        var page = service.listCards(1, 10);

        assertThat(page.items()).hasSize(1);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.items().getFirst().environmentVariableCount()).isEqualTo(1);
    }
}
