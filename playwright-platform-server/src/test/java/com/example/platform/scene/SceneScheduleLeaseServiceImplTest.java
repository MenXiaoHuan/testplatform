package com.example.platform.scene;

import com.example.platform.scene.mapper.SceneScheduleStateMapper;
import com.example.platform.scene.model.SceneScheduleStateEntity;
import com.example.platform.scene.service.SceneScheduleLeaseServiceImpl;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SceneScheduleLeaseServiceImplTest {
    @Test
    void shouldInsertLeaseStateWhenSceneHasNoState() {
        FakeSceneScheduleStateMapper mapper = new FakeSceneScheduleStateMapper();
        SceneScheduleLeaseServiceImpl service = new SceneScheduleLeaseServiceImpl(mapper);
        LocalDateTime plannedFireAt = LocalDateTime.of(2026, 6, 18, 10, 0);

        boolean acquired = service.tryAcquire(11L, plannedFireAt);

        assertThat(acquired).isTrue();
        SceneScheduleStateEntity inserted = mapper.inserted;
        assertThat(inserted.getSceneId()).isEqualTo(11L);
        assertThat(inserted.getLastPlannedFireAt()).isEqualTo(plannedFireAt);
        assertThat(inserted.getLeaseOwner()).isEqualTo("local-scheduler");
        assertThat(inserted.getLeaseUntil()).isAfter(LocalDateTime.now());
        assertThat(mapper.updateCount).isZero();
    }

    @Test
    void shouldReturnFalseWhenPlannedFireAtAlreadyAcquired() {
        LocalDateTime plannedFireAt = LocalDateTime.of(2026, 6, 18, 10, 0);
        SceneScheduleStateEntity state = new SceneScheduleStateEntity();
        state.setSceneId(11L);
        state.setLastPlannedFireAt(plannedFireAt);
        FakeSceneScheduleStateMapper mapper = new FakeSceneScheduleStateMapper(state);
        SceneScheduleLeaseServiceImpl service = new SceneScheduleLeaseServiceImpl(mapper);

        boolean acquired = service.tryAcquire(11L, plannedFireAt);

        assertThat(acquired).isFalse();
        assertThat(mapper.insertCount).isZero();
        assertThat(mapper.updateCount).isZero();
    }

    @Test
    void shouldUpdateExistingLeaseStateWhenPlannedFireAtChanges() {
        LocalDateTime previousFireAt = LocalDateTime.of(2026, 6, 18, 9, 55);
        LocalDateTime plannedFireAt = LocalDateTime.of(2026, 6, 18, 10, 0);
        SceneScheduleStateEntity state = new SceneScheduleStateEntity();
        state.setSceneId(11L);
        state.setLastPlannedFireAt(previousFireAt);
        FakeSceneScheduleStateMapper mapper = new FakeSceneScheduleStateMapper(state);
        SceneScheduleLeaseServiceImpl service = new SceneScheduleLeaseServiceImpl(mapper);

        boolean acquired = service.tryAcquire(11L, plannedFireAt);

        assertThat(acquired).isTrue();
        assertThat(state.getLastPlannedFireAt()).isEqualTo(plannedFireAt);
        assertThat(state.getLeaseOwner()).isEqualTo("local-scheduler");
        assertThat(state.getLeaseUntil()).isAfter(LocalDateTime.now());
        assertThat(mapper.updated).isSameAs(state);
        assertThat(mapper.insertCount).isZero();
    }

    private static final class FakeSceneScheduleStateMapper implements SceneScheduleStateMapper {
        private SceneScheduleStateEntity existing;
        private SceneScheduleStateEntity inserted;
        private SceneScheduleStateEntity updated;
        private int insertCount;
        private int updateCount;

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
            inserted = entity;
            existing = entity;
            insertCount++;
            return 1;
        }

        @Override
        public int update(SceneScheduleStateEntity entity) {
            updated = entity;
            existing = entity;
            updateCount++;
            return 1;
        }
    }
}
