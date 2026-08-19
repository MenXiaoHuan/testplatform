package com.example.platform.scene;

import com.example.platform.scene.mapper.ScheduleEventMapper;
import com.example.platform.scene.model.ScheduleEventEntity;
import com.example.platform.scene.service.ScheduleEventServiceImpl;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleEventServiceImplTest {
    @Test
    void shouldCreateAcquiredEventOncePerSceneAndPlannedFireAt() {
        FakeScheduleEventMapper mapper = new FakeScheduleEventMapper();
        ScheduleEventServiceImpl service = new ScheduleEventServiceImpl(mapper, 3, 60);
        LocalDateTime plannedFireAt = LocalDateTime.of(2026, 6, 18, 10, 0);

        Optional<ScheduleEventEntity> created = service.createAcquiredEvent(11L, plannedFireAt, "cron:0 */5 * * * *");

        assertThat(created).isPresent();
        assertThat(created.orElseThrow().getStatus()).isEqualTo("ACQUIRED");
        assertThat(created.orElseThrow().getSceneId()).isEqualTo(11L);
        assertThat(created.orElseThrow().getPlannedFireAt()).isEqualTo(plannedFireAt);
    }

    @Test
    void shouldReturnEmptyWhenScheduleEventAlreadyExists() {
        FakeScheduleEventMapper mapper = new FakeScheduleEventMapper();
        mapper.failWithDuplicate = true;
        ScheduleEventServiceImpl service = new ScheduleEventServiceImpl(mapper, 3, 60);

        Optional<ScheduleEventEntity> created = service.createAcquiredEvent(
                11L,
                LocalDateTime.of(2026, 6, 18, 10, 0),
                "cron:0 */5 * * * *");

        assertThat(created).isEmpty();
    }

    @Test
    void shouldMarkTaskCreatedAndFailed() {
        FakeScheduleEventMapper mapper = new FakeScheduleEventMapper();
        ScheduleEventEntity entity = new ScheduleEventEntity();
        entity.setId(7L);
        entity.setStatus("ACQUIRED");
        mapper.existing = entity;
        ScheduleEventServiceImpl service = new ScheduleEventServiceImpl(mapper, 3, 60);

        service.markTaskCreated(7L, 101L);
        assertThat(entity.getStatus()).isEqualTo("TASK_CREATED");
        assertThat(entity.getTaskId()).isEqualTo(101L);

        service.markFailed(7L, "boom", "RETRYABLE_SYSTEM");
        assertThat(entity.getStatus()).isEqualTo("FAILED");
        assertThat(entity.getErrorMessage()).isEqualTo("boom");
        assertThat(entity.getFailureCategory()).isEqualTo("RETRYABLE_SYSTEM");
        assertThat(entity.getRetryCount()).isEqualTo(1);
        assertThat(entity.getNextRetryAt()).isNotNull();
        assertThat(entity.getLastErrorAt()).isNotNull();
    }

    @Test
    void shouldListRetryableFailedEvents() {
        FakeScheduleEventMapper mapper = new FakeScheduleEventMapper();
        ScheduleEventEntity failed = new ScheduleEventEntity();
        failed.setId(9L);
        failed.setSceneId(11L);
        failed.setStatus("FAILED");
        mapper.retryableFailedEvents = List.of(failed);
        ScheduleEventServiceImpl service = new ScheduleEventServiceImpl(mapper, 3, 60);

        LocalDateTime now = LocalDateTime.of(2026, 7, 2, 12, 40);
        List<ScheduleEventEntity> events = service.listRetryableFailedEvents(10, now);

        assertThat(events).containsExactly(failed);
        assertThat(mapper.requestedLimit).isEqualTo(10);
        assertThat(mapper.requestedNow).isEqualTo(now);
        assertThat(mapper.requestedMaxRetries).isEqualTo(3);
    }

    @Test
    void shouldAbandonEventAfterMaxRetries() {
        FakeScheduleEventMapper mapper = new FakeScheduleEventMapper();
        ScheduleEventEntity entity = new ScheduleEventEntity();
        entity.setId(7L);
        entity.setStatus("FAILED");
        entity.setRetryCount(2);
        mapper.existing = entity;
        ScheduleEventServiceImpl service = new ScheduleEventServiceImpl(mapper, 3, 60);

        service.markFailed(7L, "still failing", "RETRYABLE_SYSTEM");

        assertThat(entity.getStatus()).isEqualTo("ABANDONED");
        assertThat(entity.getRetryCount()).isEqualTo(3);
        assertThat(entity.getNextRetryAt()).isNull();
    }

    @Test
    void shouldAbandonImmediatelyForNonRetryableFailureCategory() {
        FakeScheduleEventMapper mapper = new FakeScheduleEventMapper();
        ScheduleEventEntity entity = new ScheduleEventEntity();
        entity.setId(8L);
        entity.setStatus("RETRYING");
        entity.setRetryCount(0);
        mapper.existing = entity;
        ScheduleEventServiceImpl service = new ScheduleEventServiceImpl(mapper, 3, 60);

        service.markFailed(8L, "scene not found", "NON_RETRYABLE_CONFIG");

        assertThat(entity.getStatus()).isEqualTo("ABANDONED");
        assertThat(entity.getFailureCategory()).isEqualTo("NON_RETRYABLE_CONFIG");
        assertThat(entity.getNextRetryAt()).isNull();
    }

    @Test
    void shouldStartRetryOnlyWhenEventIsRetryable() {
        FakeScheduleEventMapper mapper = new FakeScheduleEventMapper();
        ScheduleEventEntity entity = new ScheduleEventEntity();
        entity.setId(9L);
        entity.setStatus("FAILED");
        mapper.existing = entity;
        ScheduleEventServiceImpl service = new ScheduleEventServiceImpl(mapper, 3, 60);

        Optional<ScheduleEventEntity> started = service.startRetry(9L);

        assertThat(started).isPresent();
        assertThat(started.orElseThrow().getStatus()).isEqualTo("RETRYING");
        assertThat(mapper.startRetryEventId).isEqualTo(9L);
    }

    @Test
    void shouldReturnEmptyWhenRetryAlreadyClaimedByAnotherRequest() {
        FakeScheduleEventMapper mapper = new FakeScheduleEventMapper();
        mapper.tryStartRetryResult = 0;
        ScheduleEventServiceImpl service = new ScheduleEventServiceImpl(mapper, 3, 60);

        Optional<ScheduleEventEntity> started = service.startRetry(10L);

        assertThat(started).isEmpty();
    }

    private static final class FakeScheduleEventMapper implements ScheduleEventMapper {
        private ScheduleEventEntity existing;
        private boolean failWithDuplicate;
        private List<ScheduleEventEntity> retryableFailedEvents = List.of();
        private int requestedLimit;
        private LocalDateTime requestedNow;
        private int requestedMaxRetries;
        private Long startRetryEventId;
        private int tryStartRetryResult = 1;

        @Override
        public int insert(ScheduleEventEntity entity) {
            if (failWithDuplicate) {
                throw new DuplicateKeyException("duplicate");
            }
            entity.setId(7L);
            existing = entity;
            return 1;
        }

        @Override
        public Optional<ScheduleEventEntity> findBySceneIdAndPlannedFireAt(Long sceneId, LocalDateTime plannedFireAt) {
            return Optional.ofNullable(existing)
                    .filter(event -> sceneId.equals(event.getSceneId()) && plannedFireAt.equals(event.getPlannedFireAt()));
        }

        @Override
        public Optional<ScheduleEventEntity> findById(Long id) {
            return Optional.ofNullable(existing).filter(event -> id.equals(event.getId()));
        }

        @Override
        public int update(ScheduleEventEntity entity) {
            existing = entity;
            return 1;
        }

        @Override
        public int tryStartRetry(Long id) {
            startRetryEventId = id;
            if (tryStartRetryResult > 0 && existing != null && id.equals(existing.getId())) {
                existing.setStatus("RETRYING");
            }
            return tryStartRetryResult;
        }

        @Override
        public List<ScheduleEventEntity> findRetryableFailedEvents(int limit, LocalDateTime now, int maxRetries) {
            requestedLimit = limit;
            requestedNow = now;
            requestedMaxRetries = maxRetries;
            return retryableFailedEvents;
        }

        @Override
        public long countIssueEvents(List<String> statuses, Long sceneId) {
            return retryableFailedEvents.size();
        }

        @Override
        public long countIssueEventsBySpaceId(List<String> statuses, Long spaceId) {
            return retryableFailedEvents.size();
        }

        @Override
        public long countIssueEventsBySpaceIdAndSceneId(List<String> statuses, Long spaceId, Long sceneId) {
            return retryableFailedEvents.size();
        }

        @Override
        public List<ScheduleEventEntity> findIssueEventsPage(List<String> statuses, Long sceneId, int limit, int offset) {
            return retryableFailedEvents;
        }

        @Override
        public List<ScheduleEventEntity> findIssueEventsPageBySpaceId(List<String> statuses, Long spaceId, int limit, int offset) {
            return retryableFailedEvents;
        }

        @Override
        public List<ScheduleEventEntity> findIssueEventsPageBySpaceIdAndSceneId(
                List<String> statuses,
                Long spaceId,
                Long sceneId,
                int limit,
                int offset) {
            return retryableFailedEvents;
        }

        @Override
        public int deleteAllBySceneId(Long sceneId) {
            if (existing != null && sceneId.equals(existing.getSceneId())) {
                existing = null;
                return 1;
            }
            return 0;
        }

        @Override
        public List<ScheduleEventEntity> findEventsPageWithFilter(
                Long spaceId, Long sceneId, List<String> statuses, String scheduleType, int limit, int offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countEventsWithFilter(Long spaceId, Long sceneId, List<String> statuses, String scheduleType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updateStatus(Long id, String status, String errorMessage, String failureCategory, LocalDateTime lastErrorAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ScheduleEventEntity> findEventsPageV2(Long spaceId, Long sceneId, String scheduleType, String sceneNameLike, String traceId, int limit, int offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countEventsV2(Long spaceId, Long sceneId, String scheduleType, String sceneNameLike, String traceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Map<String, Object>> findSceneNamesForIds(List<Long> ids) {
            return List.of();
        }
    }
}
