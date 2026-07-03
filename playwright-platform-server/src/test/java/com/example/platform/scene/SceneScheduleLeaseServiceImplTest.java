package com.example.platform.scene;

import com.example.platform.scene.mapper.SceneScheduleStateMapper;
import com.example.platform.scene.model.SceneScheduleStateEntity;
import com.example.platform.scene.service.SchedulerInstanceIdProvider;
import com.example.platform.scene.service.SceneScheduleLeaseServiceImpl;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SceneScheduleLeaseServiceImplTest {
    @Test
    void shouldInsertLeaseStateWhenSceneHasNoState() {
        FakeSceneScheduleStateMapper mapper = new FakeSceneScheduleStateMapper();
        SceneScheduleLeaseServiceImpl service = new SceneScheduleLeaseServiceImpl(
                mapper,
                new SchedulerInstanceIdProvider("scheduler-A"));
        LocalDateTime plannedFireAt = LocalDateTime.of(2026, 6, 18, 10, 0);

        boolean acquired = service.tryAcquire(11L, plannedFireAt);

        assertThat(acquired).isTrue();
        SceneScheduleStateEntity inserted = mapper.inserted;
        assertThat(inserted.getSceneId()).isEqualTo(11L);
        assertThat(mapper.acquireSceneId).isEqualTo(11L);
        assertThat(mapper.acquirePlannedFireAt).isEqualTo(plannedFireAt);
        assertThat(mapper.acquireLeaseOwner).isEqualTo("scheduler-A");
        assertThat(mapper.acquireLeaseUntil).isAfter(LocalDateTime.now());
        assertThat(mapper.acquireCount).isEqualTo(1);
    }

    @Test
    void shouldReturnFalseWhenPlannedFireAtAlreadyAcquired() {
        LocalDateTime plannedFireAt = LocalDateTime.of(2026, 6, 18, 10, 0);
        SceneScheduleStateEntity state = new SceneScheduleStateEntity();
        state.setSceneId(11L);
        state.setLastPlannedFireAt(plannedFireAt);
        FakeSceneScheduleStateMapper mapper = new FakeSceneScheduleStateMapper(state);
        mapper.acquireResult = 0;
        SceneScheduleLeaseServiceImpl service = new SceneScheduleLeaseServiceImpl(
                mapper,
                new SchedulerInstanceIdProvider("scheduler-A"));

        boolean acquired = service.tryAcquire(11L, plannedFireAt);

        assertThat(acquired).isFalse();
        assertThat(mapper.insertCount).isZero();
        assertThat(mapper.acquireCount).isEqualTo(1);
    }

    @Test
    void shouldUpdateExistingLeaseStateWhenPlannedFireAtChanges() {
        LocalDateTime previousFireAt = LocalDateTime.of(2026, 6, 18, 9, 55);
        LocalDateTime plannedFireAt = LocalDateTime.of(2026, 6, 18, 10, 0);
        SceneScheduleStateEntity state = new SceneScheduleStateEntity();
        state.setSceneId(11L);
        state.setLastPlannedFireAt(previousFireAt);
        FakeSceneScheduleStateMapper mapper = new FakeSceneScheduleStateMapper(state);
        SceneScheduleLeaseServiceImpl service = new SceneScheduleLeaseServiceImpl(
                mapper,
                new SchedulerInstanceIdProvider("scheduler-B"));

        boolean acquired = service.tryAcquire(11L, plannedFireAt);

        assertThat(acquired).isTrue();
        assertThat(mapper.acquireSceneId).isEqualTo(11L);
        assertThat(mapper.acquirePlannedFireAt).isEqualTo(plannedFireAt);
        assertThat(mapper.acquireLeaseOwner).isEqualTo("scheduler-B");
        assertThat(mapper.acquireLeaseUntil).isAfter(LocalDateTime.now());
        assertThat(mapper.insertCount).isZero();
    }

    @Test
    void shouldInsertInitialStateWhenConcurrentInsertAlreadyHappened() {
        FakeSceneScheduleStateMapper mapper = new FakeSceneScheduleStateMapper();
        mapper.failInsertWithDuplicate = true;
        SceneScheduleLeaseServiceImpl service = new SceneScheduleLeaseServiceImpl(
                mapper,
                new SchedulerInstanceIdProvider("scheduler-C"));

        boolean acquired = service.tryAcquire(15L, LocalDateTime.of(2026, 6, 18, 10, 0));

        assertThat(acquired).isTrue();
        assertThat(mapper.insertCount).isEqualTo(1);
        assertThat(mapper.acquireCount).isEqualTo(1);
    }

    @Test
    void shouldMarkTriggeredTaskMetadataForMatchedPlannedFireAt() {
        FakeSceneScheduleStateMapper mapper = new FakeSceneScheduleStateMapper();
        SceneScheduleLeaseServiceImpl service = new SceneScheduleLeaseServiceImpl(
                mapper,
                new SchedulerInstanceIdProvider("scheduler-D"));
        LocalDateTime plannedFireAt = LocalDateTime.of(2026, 6, 18, 10, 0);
        LocalDateTime triggeredAt = LocalDateTime.of(2026, 6, 18, 10, 0, 30);

        service.markTriggered(11L, plannedFireAt, 101L, triggeredAt);

        assertThat(mapper.markTriggeredSceneId).isEqualTo(11L);
        assertThat(mapper.markTriggeredPlannedFireAt).isEqualTo(plannedFireAt);
        assertThat(mapper.markTriggeredTaskId).isEqualTo(101L);
        assertThat(mapper.markTriggeredAt).isEqualTo(triggeredAt);
    }

    private static final class FakeSceneScheduleStateMapper implements SceneScheduleStateMapper {
        private SceneScheduleStateEntity existing;
        private SceneScheduleStateEntity inserted;
        private int insertCount;
        private int acquireCount;
        private int acquireResult = 1;
        private boolean failInsertWithDuplicate;
        private Long acquireSceneId;
        private LocalDateTime acquirePlannedFireAt;
        private String acquireLeaseOwner;
        private LocalDateTime acquireLeaseUntil;
        private Long markTriggeredSceneId;
        private LocalDateTime markTriggeredPlannedFireAt;
        private Long markTriggeredTaskId;
        private LocalDateTime markTriggeredAt;

        private FakeSceneScheduleStateMapper() {
        }

        private FakeSceneScheduleStateMapper(SceneScheduleStateEntity existing) {
            this.existing = existing;
        }

        @Override
        public Optional<SceneScheduleStateEntity> findBySceneId(Long sceneId) {
            return Optional.ofNullable(existing).filter(state -> sceneId.equals(state.getSceneId()));
        }

        @Override
        public int insert(SceneScheduleStateEntity entity) {
            if (failInsertWithDuplicate) {
                existing = entity;
                insertCount++;
                throw new org.springframework.dao.DuplicateKeyException("duplicate");
            }
            inserted = entity;
            existing = entity;
            insertCount++;
            return 1;
        }

        @Override
        public int update(SceneScheduleStateEntity entity) {
            existing = entity;
            return 1;
        }

        @Override
        public int tryAcquire(Long sceneId, LocalDateTime plannedFireAt, String leaseOwner, LocalDateTime leaseUntil) {
            acquireCount++;
            acquireSceneId = sceneId;
            acquirePlannedFireAt = plannedFireAt;
            acquireLeaseOwner = leaseOwner;
            acquireLeaseUntil = leaseUntil;
            if (existing != null && (existing.getLastPlannedFireAt() == null
                    || existing.getLastPlannedFireAt().isBefore(plannedFireAt))) {
                existing.setLastPlannedFireAt(plannedFireAt);
                existing.setLeaseOwner(leaseOwner);
                existing.setLeaseUntil(leaseUntil);
            }
            return acquireResult;
        }

        @Override
        public int markTriggered(Long sceneId, LocalDateTime plannedFireAt, Long taskId, LocalDateTime triggeredAt) {
            markTriggeredSceneId = sceneId;
            markTriggeredPlannedFireAt = plannedFireAt;
            markTriggeredTaskId = taskId;
            markTriggeredAt = triggeredAt;
            return 1;
        }

        @Override
        public int deleteBySceneId(Long sceneId) {
            if (existing != null && sceneId.equals(existing.getSceneId())) {
                existing = null;
                return 1;
            }
            return 0;
        }
    }
}
