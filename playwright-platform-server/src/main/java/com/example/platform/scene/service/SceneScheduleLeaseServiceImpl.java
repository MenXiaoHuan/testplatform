package com.example.platform.scene.service;

import com.example.platform.scene.model.SceneScheduleStateEntity;
import com.example.platform.scene.model.SceneScheduleStateJpaRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class SceneScheduleLeaseServiceImpl implements SceneScheduleLeaseService {
    private final SceneScheduleStateJpaRepository repository;

    public SceneScheduleLeaseServiceImpl(SceneScheduleStateJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean tryAcquire(Long sceneId, LocalDateTime plannedFireAt) {
        SceneScheduleStateEntity state = repository.findById(sceneId)
                .orElseGet(() -> {
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
        repository.save(state);
        return true;
    }
}
