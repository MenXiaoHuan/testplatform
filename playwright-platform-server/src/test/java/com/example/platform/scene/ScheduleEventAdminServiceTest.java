package com.example.platform.scene;

import com.example.platform.common.PageResponse;
import com.example.platform.audit.mapper.PlatformAuditLogMapper;
import com.example.platform.audit.model.PlatformAuditLogEntity;
import com.example.platform.scene.dto.ScheduleEventIssueResponse;
import com.example.platform.scene.dto.ScheduleEventRetryRequest;
import com.example.platform.scene.mapper.ScheduleEventMapper;
import com.example.platform.scene.model.ScheduleEventEntity;
import com.example.platform.scene.service.ScheduleEventAdminServiceImpl;
import com.example.platform.scene.service.ScheduleEventService;
import com.example.platform.scene.service.SceneScheduleLeaseService;
import com.example.platform.task.dto.TaskRunResponse;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.service.TaskService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleEventAdminServiceTest {
    @Test
    void shouldListOperationalIssueEvents() {
        FakeScheduleEventService scheduleEventService = new FakeScheduleEventService();
        ScheduleEventEntity failed = new ScheduleEventEntity();
        failed.setId(1L);
        failed.setSpaceId(7L);
        failed.setSceneId(11L);
        failed.setPlannedFireAt(LocalDateTime.of(2026, 7, 2, 12, 0));
        failed.setStatus("FAILED");
        scheduleEventService.issueEvents = List.of(failed);
        ScheduleEventAdminServiceImpl service = new ScheduleEventAdminServiceImpl(
                scheduleEventService,
                new FakeTaskService(),
                new FakeSceneScheduleLeaseService(),
                new FakePlatformAuditLogMapper(),
                new FakeScheduleEventMapper());

        PageResponse<ScheduleEventIssueResponse> page = service.listIssueEvents(List.of("FAILED", "ABANDONED"), 7L, 11L, 1, 20);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().id()).isEqualTo(1L);
        assertThat(scheduleEventService.listStatuses).containsExactly("FAILED", "ABANDONED");
        assertThat(scheduleEventService.listSceneId).isEqualTo(11L);
        assertThat(scheduleEventService.listPage).isEqualTo(1);
        assertThat(scheduleEventService.listSize).isEqualTo(20);
    }

    @Test
    void shouldRetryFailedEventAndMarkTaskCreated() {
        FakeScheduleEventService scheduleEventService = new FakeScheduleEventService();
        ScheduleEventEntity event = new ScheduleEventEntity();
        event.setId(7L);
        event.setSpaceId(7L);
        event.setSceneId(11L);
        event.setPlannedFireAt(LocalDateTime.of(2026, 7, 2, 12, 0));
        event.setTriggerReason("cron:0 */5 * * * *");
        event.setStatus("FAILED");
        scheduleEventService.findById = Optional.of(event);
        FakeTaskService taskService = new FakeTaskService();
        FakeSceneScheduleLeaseService leaseService = new FakeSceneScheduleLeaseService();
        FakePlatformAuditLogMapper auditLogMapper = new FakePlatformAuditLogMapper();
        ScheduleEventAdminServiceImpl service = new ScheduleEventAdminServiceImpl(
                scheduleEventService,
                taskService,
                leaseService,
                auditLogMapper,
                new FakeScheduleEventMapper());

        TaskRunResponse createdTask = service.retryEvent(7L, 7L, new ScheduleEventRetryRequest("alice", "u-1001", "manual retry after fix"));

        assertThat(createdTask.id()).isEqualTo(101L);
        assertThat(taskService.sceneId).isEqualTo(11L);
        assertThat(taskService.triggerReason).isEqualTo("cron:0 */5 * * * *");
        assertThat(scheduleEventService.markTaskCreatedEventId).isEqualTo(7L);
        assertThat(leaseService.markTriggeredSceneId).isEqualTo(11L);
        assertThat(auditLogMapper.inserted.getEntityType()).isEqualTo("SCHEDULE_EVENT");
        assertThat(auditLogMapper.inserted.getEntityId()).isEqualTo(7L);
        assertThat(auditLogMapper.inserted.getAction()).isEqualTo("RETRY");
        assertThat(auditLogMapper.inserted.getOperatorName()).isEqualTo("alice");
        assertThat(auditLogMapper.inserted.getDetailJson()).contains("\"taskId\":101");
        assertThat(auditLogMapper.inserted.getDetailJson()).contains("\"operatorId\":\"u-1001\"");
        assertThat(auditLogMapper.inserted.getDetailJson()).contains("\"comment\":\"manual retry after fix\"");
        assertThat(scheduleEventService.startRetryEventId).isEqualTo(7L);
    }

    @Test
    void shouldUseAnonymousOperatorWhenRetryOperatorMissing() {
        FakeScheduleEventService scheduleEventService = new FakeScheduleEventService();
        ScheduleEventEntity event = new ScheduleEventEntity();
        event.setId(8L);
        event.setSpaceId(7L);
        event.setSceneId(12L);
        event.setPlannedFireAt(LocalDateTime.of(2026, 7, 2, 12, 5));
        event.setTriggerReason("cron:0 */5 * * * *");
        event.setStatus("FAILED");
        scheduleEventService.findById = Optional.of(event);
        FakePlatformAuditLogMapper auditLogMapper = new FakePlatformAuditLogMapper();
        ScheduleEventAdminServiceImpl service = new ScheduleEventAdminServiceImpl(
                scheduleEventService,
                new FakeTaskService(),
                new FakeSceneScheduleLeaseService(),
                auditLogMapper,
                new FakeScheduleEventMapper());

        service.retryEvent(7L, 8L, new ScheduleEventRetryRequest(" ", null, null));

        assertThat(auditLogMapper.inserted.getOperatorName()).isEqualTo("anonymous");
    }

    @Test
    void shouldRejectRetryForNonRecoverableStatus() {
        FakeScheduleEventService scheduleEventService = new FakeScheduleEventService();
        ScheduleEventEntity event = new ScheduleEventEntity();
        event.setId(7L);
        event.setSpaceId(7L);
        event.setStatus("TASK_CREATED");
        scheduleEventService.findById = Optional.of(event);
        ScheduleEventAdminServiceImpl service = new ScheduleEventAdminServiceImpl(
                scheduleEventService,
                new FakeTaskService(),
                new FakeSceneScheduleLeaseService(),
                new FakePlatformAuditLogMapper(),
                new FakeScheduleEventMapper());

        assertThatThrownBy(() -> service.retryEvent(7L, 7L, new ScheduleEventRetryRequest("alice", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("only FAILED or ABANDONED events can be retried");
    }

    @Test
    void shouldRejectRetryWhenAnotherRequestAlreadyStartedIt() {
        FakeScheduleEventService scheduleEventService = new FakeScheduleEventService();
        ScheduleEventEntity event = new ScheduleEventEntity();
        event.setId(9L);
        event.setSpaceId(7L);
        event.setSceneId(11L);
        event.setPlannedFireAt(LocalDateTime.of(2026, 7, 2, 12, 0));
        event.setTriggerReason("cron:0 */5 * * * *");
        event.setStatus("FAILED");
        scheduleEventService.findById = Optional.of(event);
        scheduleEventService.startRetry = Optional.empty();
        ScheduleEventAdminServiceImpl service = new ScheduleEventAdminServiceImpl(
                scheduleEventService,
                new FakeTaskService(),
                new FakeSceneScheduleLeaseService(),
                new FakePlatformAuditLogMapper(),
                new FakeScheduleEventMapper());

        assertThatThrownBy(() -> service.retryEvent(7L, 9L, new ScheduleEventRetryRequest("alice", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("retry already in progress or event state changed");
    }

    private static final class FakeScheduleEventService implements ScheduleEventService {
        private List<ScheduleEventEntity> issueEvents = List.of();
        private List<String> listStatuses = List.of();
        private Long listSpaceId;
        private Long listSceneId;
        private int listPage;
        private int listSize;
        private Optional<ScheduleEventEntity> findById = Optional.empty();
        private Long markTaskCreatedEventId;
        private Long startRetryEventId;
        private Optional<ScheduleEventEntity> startRetry = null;

        @Override
        public Optional<ScheduleEventEntity> createAcquiredEvent(Long sceneId, LocalDateTime plannedFireAt, String triggerReason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markTaskCreated(Long eventId, Long taskId) {
            markTaskCreatedEventId = eventId;
        }

        @Override
        public void markFailed(Long eventId, String errorMessage, String failureCategory) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ScheduleEventEntity> listRetryableFailedEvents(int limit, LocalDateTime now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PageResponse<ScheduleEventEntity> listIssueEvents(List<String> statuses, Long spaceId, Long sceneId, int page, int size) {
            listStatuses = statuses;
            listSpaceId = spaceId;
            listSceneId = sceneId;
            listPage = page;
            listSize = size;
            return PageResponse.of(issueEvents, issueEvents.size(), page, size);
        }

        @Override
        public Optional<ScheduleEventEntity> get(Long eventId) {
            return findById.filter(event -> eventId.equals(event.getId()));
        }

        @Override
        public Optional<ScheduleEventEntity> startRetry(Long eventId) {
            startRetryEventId = eventId;
            Optional<ScheduleEventEntity> source = startRetry != null
                    ? startRetry
                    : get(eventId);
            return source.map(event -> {
                event.setStatus("RETRYING");
                return event;
            });
        }

        @Override
        public Long createAgentEvent(Long spaceId, String traceId, String sessionId, String userMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void completeAgentEvent(Long eventId, boolean success, String errorMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PageResponse<ScheduleEventEntity> listEventsWithFilter(
                List<String> statuses, Long spaceId, Long sceneId, String scheduleType, int page, int size) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeTaskService implements TaskService {
        private Long sceneId;
        private String triggerReason;

        @Override
        public TaskEntity createScheduledTask(Long sceneId, String triggerReason) {
            this.sceneId = sceneId;
            this.triggerReason = triggerReason;
            TaskEntity task = new TaskEntity();
            task.setId(101L);
            task.setSceneId(sceneId);
            task.setRepoId(21L);
            task.setStatus("QUEUED");
            task.setTriggerType("SCHEDULED");
            task.setTriggerReason(triggerReason);
            task.setTriggerUser("scheduler");
            task.setBranch("main");
            task.setRunnerName("centralized-runner");
            task.setCurrentStage("QUEUED");
            task.setResolvedBranch("main");
            task.setResolvedBrowser("chromium");
            task.setResolvedTestRoot("tests");
            task.setResolvedRunCommand("npm run test:e2e");
            return task;
        }

        @Override public TaskEntity createAndStart(Long sceneId) { throw new UnsupportedOperationException(); }
        @Override public TaskEntity createAndStart(Long spaceId, Long sceneId) { throw new UnsupportedOperationException(); }
        @Override public TaskEntity createAndRun(Long sceneId) { throw new UnsupportedOperationException(); }
        @Override public com.example.platform.common.PageResponse<com.example.platform.task.dto.SceneTaskListResponse> list(int page, int size) { throw new UnsupportedOperationException(); }
        @Override public com.example.platform.common.PageResponse<com.example.platform.task.dto.SceneTaskListResponse> list(Long spaceId, int page, int size) { throw new UnsupportedOperationException(); }
        @Override public com.example.platform.common.PageResponse<com.example.platform.task.dto.SceneTaskListResponse> listByScene(Long sceneId, int page, int size) { throw new UnsupportedOperationException(); }
        @Override public com.example.platform.common.PageResponse<com.example.platform.task.dto.SceneTaskListResponse> listByScene(Long spaceId, Long sceneId, int page, int size) { throw new UnsupportedOperationException(); }
        @Override public com.example.platform.task.dto.TaskDetailResponse getDetail(Long taskId) { throw new UnsupportedOperationException(); }
        @Override public com.example.platform.task.dto.TaskDetailResponse getDetail(Long spaceId, Long taskId) { throw new UnsupportedOperationException(); }
        @Override public com.example.platform.task.dto.TaskDiagnosticsResponse getDiagnostics(Long taskId) { throw new UnsupportedOperationException(); }
        @Override public com.example.platform.task.dto.TaskDiagnosticsResponse getDiagnostics(Long spaceId, Long taskId) { throw new UnsupportedOperationException(); }
        @Override public TaskEntity get(Long taskId) { throw new UnsupportedOperationException(); }
        @Override public TaskEntity get(Long spaceId, Long taskId) { throw new UnsupportedOperationException(); }
        @Override public java.util.List<com.example.platform.task.model.ArtifactEntity> listArtifacts(Long taskId) { throw new UnsupportedOperationException(); }
        @Override public java.util.List<com.example.platform.task.model.ArtifactEntity> listArtifacts(Long spaceId, Long taskId) { throw new UnsupportedOperationException(); }
        @Override public java.util.List<com.example.platform.task.dto.CaseResultResponse> listCaseResultResponses(Long taskId) { throw new UnsupportedOperationException(); }
        @Override public java.util.List<com.example.platform.task.dto.CaseResultResponse> listCaseResultResponses(Long spaceId, Long taskId) { throw new UnsupportedOperationException(); }
        @Override public java.util.List<com.example.platform.task.model.CaseResultEntity> listCaseResults(Long taskId) { throw new UnsupportedOperationException(); }
        @Override public java.util.List<com.example.platform.task.model.ArtifactEntity> listArtifactsByCaseResult(Long caseResultId) { throw new UnsupportedOperationException(); }
        @Override public java.util.List<com.example.platform.task.model.ArtifactEntity> listArtifactsByCaseResult(Long spaceId, Long taskId, Long caseResultId) { throw new UnsupportedOperationException(); }
        @Override public void cancelTask(Long taskId, String operatorName) { throw new UnsupportedOperationException(); }
        @Override public void cancelTask(Long spaceId, Long taskId, String operatorName) { throw new UnsupportedOperationException(); }
        @Override public java.util.List<com.example.platform.task.dto.TaskStageLogResponse> listStageLogs(Long taskId) { throw new UnsupportedOperationException(); }
        @Override public java.util.List<com.example.platform.task.dto.TaskStageLogResponse> listStageLogs(Long spaceId, Long taskId) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadArtifact(Long taskId, Long artifactId) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadArtifact(Long spaceId, Long taskId, Long artifactId) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadStageLog(Long taskId, Long stageLogId) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadStageLog(Long spaceId, Long taskId, Long stageLogId) { throw new UnsupportedOperationException(); }
    }

    private static final class FakeSceneScheduleLeaseService implements SceneScheduleLeaseService {
        private Long markTriggeredSceneId;

        @Override
        public boolean tryAcquire(Long sceneId, LocalDateTime plannedFireAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markTriggered(Long sceneId, LocalDateTime plannedFireAt, Long taskId, LocalDateTime triggeredAt) {
            markTriggeredSceneId = sceneId;
        }
    }

    private static final class FakePlatformAuditLogMapper implements PlatformAuditLogMapper {
        private PlatformAuditLogEntity inserted;

        @Override
        public int insert(PlatformAuditLogEntity entity) {
            inserted = entity;
            return 1;
        }
    }

    private static final class FakeScheduleEventMapper implements ScheduleEventMapper {
        @Override
        public int insert(ScheduleEventEntity entity) { throw new UnsupportedOperationException(); }
        @Override
        public Optional<ScheduleEventEntity> findBySceneIdAndPlannedFireAt(Long sceneId, LocalDateTime plannedFireAt) { throw new UnsupportedOperationException(); }
        @Override
        public Optional<ScheduleEventEntity> findById(Long id) { throw new UnsupportedOperationException(); }
        @Override
        public int update(ScheduleEventEntity entity) { throw new UnsupportedOperationException(); }
        @Override
        public int updateStatus(Long id, String status, String errorMessage, String failureCategory, LocalDateTime lastErrorAt) { throw new UnsupportedOperationException(); }
        @Override
        public int tryStartRetry(Long id) { throw new UnsupportedOperationException(); }
        @Override
        public List<ScheduleEventEntity> findRetryableFailedEvents(int limit, LocalDateTime now, int maxRetries) { throw new UnsupportedOperationException(); }
        @Override
        public List<ScheduleEventEntity> findEventsPageWithFilter(Long spaceId, Long sceneId, List<String> statuses, String scheduleType, int limit, int offset) { throw new UnsupportedOperationException(); }
        @Override
        public long countEventsWithFilter(Long spaceId, Long sceneId, List<String> statuses, String scheduleType) { throw new UnsupportedOperationException(); }
        @Override
        public List<ScheduleEventEntity> findEventsPageV2(Long spaceId, Long sceneId, String scheduleType, String sceneNameLike, String traceId, int limit, int offset) { throw new UnsupportedOperationException(); }
        @Override
        public long countEventsV2(Long spaceId, Long sceneId, String scheduleType, String sceneNameLike, String traceId) { throw new UnsupportedOperationException(); }
        @Override
        public List<Map<String, Object>> findSceneNamesForIds(List<Long> ids) { return List.of(); }
        @Override
        public long countIssueEvents(List<String> statuses, Long sceneId) { throw new UnsupportedOperationException(); }
        @Override
        public long countIssueEventsBySpaceId(List<String> statuses, Long spaceId) { throw new UnsupportedOperationException(); }
        @Override
        public long countIssueEventsBySpaceIdAndSceneId(List<String> statuses, Long spaceId, Long sceneId) { throw new UnsupportedOperationException(); }
        @Override
        public List<ScheduleEventEntity> findIssueEventsPage(List<String> statuses, Long sceneId, int limit, int offset) { throw new UnsupportedOperationException(); }
        @Override
        public List<ScheduleEventEntity> findIssueEventsPageBySpaceId(List<String> statuses, Long spaceId, int limit, int offset) { throw new UnsupportedOperationException(); }
        @Override
        public List<ScheduleEventEntity> findIssueEventsPageBySpaceIdAndSceneId(List<String> statuses, Long spaceId, Long sceneId, int limit, int offset) { throw new UnsupportedOperationException(); }
        @Override
        public int deleteAllBySceneId(Long sceneId) { throw new UnsupportedOperationException(); }
    }
}
