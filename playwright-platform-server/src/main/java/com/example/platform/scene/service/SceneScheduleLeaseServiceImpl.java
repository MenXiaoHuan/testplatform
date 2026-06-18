package com.example.platform.scene.service;

import com.example.platform.scene.mapper.SceneScheduleStateMapper;
import com.example.platform.scene.model.SceneScheduleStateEntity;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SceneScheduleLeaseServiceImpl implements SceneScheduleLeaseService {
    private final SceneScheduleStateMapper mapper;

    public SceneScheduleLeaseServiceImpl(SceneScheduleStateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public boolean tryAcquire(Long sceneId, LocalDateTime plannedFireAt) {
        Optional<SceneScheduleStateEntity> existing = mapper.findBySceneId(sceneId);
        SceneScheduleStateEntity state = existing.orElseGet(() -> {
            SceneScheduleStateEntity created = new SceneScheduleStateEntity();
            created.setSceneId(sceneId);
            return created;
        });
        if (plannedFireAt != null && plannedFireAt.equals(state.getLastPlannedFireAt())) {
            return false;
        }
        state.setLastPlannedFireAt(plannedFireAt);
        state.setLeaseOwner("local-scheduler");
        state.setLeaseUntil(LocalDateTime.now().plusMinutes(2));
        if (existing.isPresent()) {
            mapper.update(state);
        } else {
            mapper.insert(state);
        }
        return true;
    }
}
