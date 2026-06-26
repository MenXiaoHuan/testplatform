package com.example.platform.scene;

import com.example.platform.cache.DetailCacheService;
import com.example.platform.common.PageResponse;
import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.service.SceneCascadeDeleteService;
import com.example.platform.scene.service.SceneScheduleLeaseService;
import com.example.platform.scene.service.SceneSchedulerServiceImpl;
import com.example.platform.scene.service.SceneServiceImpl;
import com.example.platform.task.dto.CaseResultResponse;
import com.example.platform.task.dto.SceneTaskListResponse;
import com.example.platform.task.dto.TaskDiagnosticsResponse;
import com.example.platform.task.dto.TaskDetailResponse;
import com.example.platform.task.dto.TaskStageLogResponse;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.CaseResultEntity;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SceneServiceImplTest {
    @Test
    void shouldUpdateDescriptionAndScheduleFields() {
        SceneMapper sceneMapper = Mockito.mock(SceneMapper.class);
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        SceneCascadeDeleteService sceneCascadeDeleteService = Mockito.mock(SceneCascadeDeleteService.class);
        SceneServiceImpl service = new SceneServiceImpl(sceneMapper, repositoryMapper, sceneCascadeDeleteService, new ObjectMapper());

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

        Mockito.when(sceneMapper.findById(1L)).thenReturn(Optional.of(existing));
        Mockito.when(repositoryMapper.findById(2L)).thenReturn(Optional.of(targetRepository));
        Mockito.when(sceneMapper.update(existing)).thenReturn(1);

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
        Mockito.verify(sceneMapper).update(existing);
    }

    @Test
    void shouldCreateScheduledTaskWhenCronIsDueAndLeaseAcquired() {
        SceneMapper repository = Mockito.mock(SceneMapper.class);
        FakeSceneScheduleLeaseService leaseService = new FakeSceneScheduleLeaseService(true);
        FakeTaskService taskService = new FakeTaskService();
        SceneSchedulerServiceImpl service = new SceneSchedulerServiceImpl(repository, leaseService, taskService);

        SceneEntity scheduled = new SceneEntity();
        scheduled.setId(11L);
        scheduled.setScheduleEnabled(true);
        scheduled.setCronExpression("0 */5 * * * *");

        scheduled.setNextRunAt(LocalDateTime.of(2026, 6, 13, 10, 0));
        Mockito.when(repository.findAllByScheduleEnabledTrueAndNextRunAtIsNullOrderByIdAsc()).thenReturn(List.of());
        Mockito.when(repository.findDueScheduledScenes(LocalDateTime.of(2026, 6, 13, 10, 0, 30)))
                .thenReturn(List.of(scheduled));
        Mockito.when(repository.update(scheduled)).thenReturn(1);

        service.triggerDueScenes(LocalDateTime.of(2026, 6, 13, 10, 0, 30));

        Mockito.verify(repository).findDueScheduledScenes(LocalDateTime.of(2026, 6, 13, 10, 0, 30));
        assertThat(leaseService.sceneId).isEqualTo(11L);
        assertThat(leaseService.plannedFireAt).isEqualTo(LocalDateTime.of(2026, 6, 13, 10, 0));
        assertThat(scheduled.getNextRunAt()).isEqualTo(LocalDateTime.of(2026, 6, 13, 10, 5));
        assertThat(taskService.scheduledTaskCount).isEqualTo(1);
        assertThat(taskService.scheduledSceneId).isEqualTo(11L);
        assertThat(taskService.scheduledTriggerReason).isEqualTo("cron:0 */5 * * * *");
    }

    @Test
    void shouldSkipScheduledTaskWhenLeaseIsRejected() {
        SceneMapper repository = Mockito.mock(SceneMapper.class);
        FakeSceneScheduleLeaseService leaseService = new FakeSceneScheduleLeaseService(false);
        FakeTaskService taskService = new FakeTaskService();
        SceneSchedulerServiceImpl service = new SceneSchedulerServiceImpl(repository, leaseService, taskService);

        SceneEntity scheduled = new SceneEntity();
        scheduled.setId(11L);
        scheduled.setScheduleEnabled(true);
        scheduled.setCronExpression("0 */5 * * * *");

        scheduled.setNextRunAt(LocalDateTime.of(2026, 6, 13, 10, 0));
        Mockito.when(repository.findAllByScheduleEnabledTrueAndNextRunAtIsNullOrderByIdAsc()).thenReturn(List.of());
        Mockito.when(repository.findDueScheduledScenes(LocalDateTime.of(2026, 6, 13, 10, 0, 30)))
                .thenReturn(List.of(scheduled));

        service.triggerDueScenes(LocalDateTime.of(2026, 6, 13, 10, 0, 30));

        assertThat(leaseService.sceneId).isEqualTo(11L);
        assertThat(leaseService.plannedFireAt).isEqualTo(LocalDateTime.of(2026, 6, 13, 10, 0));
        assertThat(taskService.scheduledTaskCount).isZero();
        Mockito.verify(repository, Mockito.never()).update(Mockito.any(SceneEntity.class));
    }

    @Test
    void shouldRejectDisabledRepositoryWhenCreatingScene() {
        SceneMapper sceneMapper = Mockito.mock(SceneMapper.class);
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        SceneCascadeDeleteService sceneCascadeDeleteService = Mockito.mock(SceneCascadeDeleteService.class);
        SceneServiceImpl service = new SceneServiceImpl(sceneMapper, repositoryMapper, sceneCascadeDeleteService, new ObjectMapper());

        SceneEntity scene = new SceneEntity();
        scene.setRepoId(7L);
        scene.setName("login");

        TestRepositoryEntity disabledRepository = new TestRepositoryEntity();
        disabledRepository.setId(7L);
        disabledRepository.setEnabled(false);

        Mockito.when(repositoryMapper.findById(7L)).thenReturn(Optional.of(disabledRepository));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.create(scene));
        Mockito.verify(sceneMapper, Mockito.never()).insert(Mockito.any(SceneEntity.class));
    }

    @Test
    void shouldRejectMissingRepositoryWhenCreatingScene() {
        SceneMapper sceneMapper = Mockito.mock(SceneMapper.class);
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        SceneCascadeDeleteService sceneCascadeDeleteService = Mockito.mock(SceneCascadeDeleteService.class);
        SceneServiceImpl service = new SceneServiceImpl(sceneMapper, repositoryMapper, sceneCascadeDeleteService, new ObjectMapper());

        SceneEntity scene = new SceneEntity();
        scene.setRepoId(9L);
        scene.setName("login");

        Mockito.when(repositoryMapper.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(scene))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("所属仓库不存在，请重新选择");
        Mockito.verify(sceneMapper, Mockito.never()).insert(Mockito.any(SceneEntity.class));
    }

    @Test
    void shouldNormalizeSelectorAndBlankFieldsWhenCreatingScene() {
        SceneMapper sceneMapper = Mockito.mock(SceneMapper.class);
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        SceneCascadeDeleteService sceneCascadeDeleteService = Mockito.mock(SceneCascadeDeleteService.class);
        SceneServiceImpl service = new SceneServiceImpl(sceneMapper, repositoryMapper, sceneCascadeDeleteService, new ObjectMapper());

        SceneEntity scene = new SceneEntity();
        scene.setRepoId(9L);
        scene.setName(" login ");
        scene.setBranch("main");
        scene.setMatchValue("interview_agent");
        scene.setBrowser("chromium");
        scene.setRunCommand("npx playwright test");
        scene.setEnvJson("");
        scene.setCronExpression(" ");

        TestRepositoryEntity enabledRepository = new TestRepositoryEntity();
        enabledRepository.setId(9L);
        enabledRepository.setEnabled(true);

        Mockito.when(repositoryMapper.findById(9L)).thenReturn(Optional.of(enabledRepository));
        Mockito.when(sceneMapper.insert(Mockito.any(SceneEntity.class))).thenAnswer(invocation -> {
            SceneEntity entity = invocation.getArgument(0);
            entity.setId(33L);
            return 1;
        });

        SceneEntity result = service.create(scene);

        assertThat(result.getId()).isEqualTo(33L);
        assertThat(result.getName()).isEqualTo("login");
        assertThat(result.getTestSelectorType()).isEqualTo("file");
        assertThat(result.getTestSelectorValue()).isEqualTo("interview_agent");
        assertThat(result.getMatchValue()).isEqualTo("interview_agent");
        assertThat(result.getEnvJson()).isNull();
        assertThat(result.getCronExpression()).isNull();
        assertThat(result.getNextRunAt()).isNull();
    }

    @Test
    void shouldRejectDuplicateSceneNameWhenCreating() {
        SceneMapper sceneMapper = Mockito.mock(SceneMapper.class);
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        SceneCascadeDeleteService sceneCascadeDeleteService = Mockito.mock(SceneCascadeDeleteService.class);
        SceneServiceImpl service = new SceneServiceImpl(sceneMapper, repositoryMapper, sceneCascadeDeleteService, new ObjectMapper());

        SceneEntity scene = new SceneEntity();
        scene.setRepoId(9L);
        scene.setName("login");

        TestRepositoryEntity enabledRepository = new TestRepositoryEntity();
        enabledRepository.setId(9L);
        enabledRepository.setEnabled(true);

        Mockito.when(repositoryMapper.findById(9L)).thenReturn(Optional.of(enabledRepository));
        Mockito.when(sceneMapper.existsByNameIgnoreCase("login")).thenReturn(true);

        assertThatThrownBy(() -> service.create(scene))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("场景名称已存在，请更换后重试");
        Mockito.verify(sceneMapper, Mockito.never()).insert(Mockito.any(SceneEntity.class));
    }

    @Test
    void shouldRejectDuplicateSceneNameWhenUpdating() {
        SceneMapper sceneMapper = Mockito.mock(SceneMapper.class);
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        SceneCascadeDeleteService sceneCascadeDeleteService = Mockito.mock(SceneCascadeDeleteService.class);
        SceneServiceImpl service = new SceneServiceImpl(sceneMapper, repositoryMapper, sceneCascadeDeleteService, new ObjectMapper());

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

        Mockito.when(sceneMapper.findById(1L)).thenReturn(Optional.of(existing));
        Mockito.when(repositoryMapper.findById(9L)).thenReturn(Optional.of(enabledRepository));
        Mockito.when(sceneMapper.existsByNameIgnoreCaseAndIdNot("checkout", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, update))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("场景名称已存在，请更换后重试");
        Mockito.verify(sceneMapper, Mockito.never()).update(Mockito.any(SceneEntity.class));
    }

    @Test
    void shouldInitializeNextRunAtForLegacyScheduledScene() {
        SceneMapper repository = Mockito.mock(SceneMapper.class);
        FakeSceneScheduleLeaseService leaseService = new FakeSceneScheduleLeaseService(true);
        FakeTaskService taskService = new FakeTaskService();
        SceneSchedulerServiceImpl service = new SceneSchedulerServiceImpl(repository, leaseService, taskService);

        SceneEntity legacyScene = new SceneEntity();
        legacyScene.setId(21L);
        legacyScene.setScheduleEnabled(true);
        legacyScene.setCronExpression("0 */10 * * * *");

        Mockito.when(repository.findAllByScheduleEnabledTrueAndNextRunAtIsNullOrderByIdAsc()).thenReturn(List.of(legacyScene));
        Mockito.when(repository.findDueScheduledScenes(LocalDateTime.of(2026, 6, 13, 10, 0, 30)))
                .thenReturn(List.of());
        Mockito.when(repository.update(legacyScene)).thenReturn(1);

        service.triggerDueScenes(LocalDateTime.of(2026, 6, 13, 10, 0, 30));

        assertThat(legacyScene.getNextRunAt()).isEqualTo(LocalDateTime.of(2026, 6, 13, 10, 10));
        Mockito.verify(repository).update(legacyScene);
    }

    @Test
    void shouldReturnPagedSceneCards() {
        SceneMapper sceneMapper = Mockito.mock(SceneMapper.class);
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        SceneCascadeDeleteService sceneCascadeDeleteService = Mockito.mock(SceneCascadeDeleteService.class);
        SceneServiceImpl service = new SceneServiceImpl(sceneMapper, repositoryMapper, sceneCascadeDeleteService, new ObjectMapper());

        SceneEntity scene = new SceneEntity();
        scene.setId(11L);
        scene.setRepoId(7L);
        scene.setName("login");
        scene.setDescription("smoke");
        scene.setBranch("main");
        scene.setScheduleEnabled(false);
        scene.setCronExpression(null);
        scene.setEnvJson("{\"foo\":\"bar\"}");

        Mockito.when(sceneMapper.findPage(10, 0)).thenReturn(List.of(scene));
        Mockito.when(sceneMapper.countAll()).thenReturn(1L);

        var page = service.listCards(1, 10);

        assertThat(page.items()).hasSize(1);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.items().getFirst().environmentVariableCount()).isEqualTo(1);
    }

    @Test
    void shouldDelegateTriggerScheduledScenesToScheduler() {
        SceneMapper sceneMapper = Mockito.mock(SceneMapper.class);
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        SceneCascadeDeleteService sceneCascadeDeleteService = Mockito.mock(SceneCascadeDeleteService.class);
        AtomicReference<LocalDateTime> capturedNow = new AtomicReference<>();
        com.example.platform.scene.service.SceneSchedulerService schedulerService = capturedNow::set;
        SceneServiceImpl service = new SceneServiceImpl(
                sceneMapper,
                repositoryMapper,
                sceneCascadeDeleteService,
                new ObjectMapper(),
                schedulerService);

        service.triggerScheduledScenes();

        assertThat(capturedNow.get()).isNotNull();
        Mockito.verify(sceneMapper, Mockito.never()).findAllByScheduleEnabledTrue();
    }

    @Test
    void shouldReturnSceneFromDetailCacheHit() {
        SceneMapper sceneMapper = Mockito.mock(SceneMapper.class);
        DetailCacheService detailCacheService = Mockito.mock(DetailCacheService.class);
        SceneServiceImpl service = new SceneServiceImpl(
                sceneMapper,
                Mockito.mock(TestRepositoryMapper.class),
                Mockito.mock(SceneCascadeDeleteService.class),
                new ObjectMapper(),
                null,
                detailCacheService);
        SceneEntity cached = new SceneEntity();
        cached.setId(1L);
        cached.setName("cached");
        Mockito.when(detailCacheService.getOrLoad(
                        Mockito.eq("scene"),
                        Mockito.eq(1L),
                        Mockito.eq(SceneEntity.class),
                        Mockito.any()))
                .thenReturn(Optional.of(cached));

        SceneEntity result = service.get(1L);

        assertThat(result).isSameAs(cached);
        Mockito.verify(sceneMapper, Mockito.never()).findById(Mockito.anyLong());
    }

    @Test
    void shouldThrowWhenSceneEmptyValueIsCached() {
        SceneMapper sceneMapper = Mockito.mock(SceneMapper.class);
        DetailCacheService detailCacheService = Mockito.mock(DetailCacheService.class);
        SceneServiceImpl service = new SceneServiceImpl(
                sceneMapper,
                Mockito.mock(TestRepositoryMapper.class),
                Mockito.mock(SceneCascadeDeleteService.class),
                new ObjectMapper(),
                null,
                detailCacheService);
        Mockito.when(detailCacheService.getOrLoad(
                        Mockito.eq("scene"),
                        Mockito.eq(404L),
                        Mockito.eq(SceneEntity.class),
                        Mockito.any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Scene not found: 404");
        Mockito.verify(sceneMapper, Mockito.never()).findById(Mockito.anyLong());
    }

    @Test
    void shouldInvalidateSceneDetailCacheWhenWritingScene() {
        SceneMapper sceneMapper = Mockito.mock(SceneMapper.class);
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        SceneCascadeDeleteService sceneCascadeDeleteService = Mockito.mock(SceneCascadeDeleteService.class);
        DetailCacheService detailCacheService = Mockito.mock(DetailCacheService.class);
        SceneServiceImpl service = new SceneServiceImpl(
                sceneMapper,
                repositoryMapper,
                sceneCascadeDeleteService,
                new ObjectMapper(),
                null,
                detailCacheService);
        TestRepositoryEntity repository = new TestRepositoryEntity();
        repository.setId(9L);
        repository.setEnabled(true);
        Mockito.when(repositoryMapper.findById(9L)).thenReturn(Optional.of(repository));
        Mockito.when(sceneMapper.insert(Mockito.any(SceneEntity.class))).thenAnswer(invocation -> {
            SceneEntity entity = invocation.getArgument(0);
            entity.setId(33L);
            return 1;
        });
        SceneEntity existing = new SceneEntity();
        existing.setId(33L);
        existing.setRepoId(9L);
        existing.setName("login");
        Mockito.when(sceneMapper.findById(33L)).thenReturn(Optional.of(existing));
        Mockito.when(detailCacheService.getOrLoad(
                        Mockito.eq("scene"),
                        Mockito.eq(33L),
                        Mockito.eq(SceneEntity.class),
                        Mockito.any()))
                .thenAnswer(invocation -> invocation.<Supplier<Optional<SceneEntity>>>getArgument(3).get());

        SceneEntity created = new SceneEntity();
        created.setRepoId(9L);
        created.setName("login");
        service.create(created);
        SceneEntity update = new SceneEntity();
        update.setRepoId(9L);
        update.setName("login-new");
        service.update(33L, update);
        service.delete(33L);

        Mockito.verify(detailCacheService, Mockito.times(3)).invalidate("scene", 33L);
        Mockito.verify(sceneCascadeDeleteService).deleteSceneGraph(33L);
    }

    private static final class FakeSceneScheduleLeaseService implements SceneScheduleLeaseService {
        private final boolean acquireResult;
        private Long sceneId;
        private LocalDateTime plannedFireAt;

        private FakeSceneScheduleLeaseService(boolean acquireResult) {
            this.acquireResult = acquireResult;
        }

        @Override
        public boolean tryAcquire(Long sceneId, LocalDateTime plannedFireAt) {
            this.sceneId = sceneId;
            this.plannedFireAt = plannedFireAt;
            return acquireResult;
        }
    }

    private static final class FakeTaskService implements TaskService {
        private int scheduledTaskCount;
        private Long scheduledSceneId;
        private String scheduledTriggerReason;

        @Override
        public TaskEntity createAndStart(Long sceneId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskEntity createAndRun(Long sceneId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskEntity createScheduledTask(Long sceneId, String triggerReason) {
            scheduledTaskCount++;
            scheduledSceneId = sceneId;
            scheduledTriggerReason = triggerReason;
            return new TaskEntity();
        }

        @Override
        public PageResponse<SceneTaskListResponse> list(int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PageResponse<SceneTaskListResponse> listByScene(Long sceneId, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskDetailResponse getDetail(Long taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskDiagnosticsResponse getDiagnostics(Long taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskEntity get(Long taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ArtifactEntity> listArtifacts(Long taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CaseResultResponse> listCaseResultResponses(Long taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CaseResultEntity> listCaseResults(Long taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ArtifactEntity> listArtifactsByCaseResult(Long caseResultId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancelTask(Long taskId, String operatorName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TaskStageLogResponse> listStageLogs(Long taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResponseEntity<Resource> downloadArtifact(Long taskId, Long artifactId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResponseEntity<Resource> downloadStageLog(Long taskId, Long stageLogId) {
            throw new UnsupportedOperationException();
        }
    }
}
