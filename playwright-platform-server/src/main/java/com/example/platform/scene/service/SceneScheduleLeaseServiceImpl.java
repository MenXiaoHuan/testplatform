package com.example.platform.scene.service;

import com.example.platform.scene.mapper.SceneScheduleStateMapper;
import com.example.platform.scene.model.SceneScheduleStateEntity;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SceneScheduleLeaseServiceImpl implements SceneScheduleLeaseService {
    private final SceneScheduleStateMapper mapper;
    private final SchedulerInstanceIdProvider instanceIdProvider;

    public SceneScheduleLeaseServiceImpl(
            SceneScheduleStateMapper mapper,
            SchedulerInstanceIdProvider instanceIdProvider) {
        this.mapper = mapper;
        this.instanceIdProvider = instanceIdProvider;
    }

    @Override
    @Transactional
    public boolean tryAcquire(Long sceneId, LocalDateTime plannedFireAt) {
        Optional<SceneScheduleStateEntity> existing = mapper.findBySceneId(sceneId);
        if (existing.isEmpty()) {
            SceneScheduleStateEntity created = new SceneScheduleStateEntity();
            created.setSceneId(sceneId);
            try {
                mapper.insert(created);
            } catch (DuplicateKeyException ignored) {
            }
        }
        String instanceId = instanceIdProvider.getInstanceId();
        LocalDateTime leaseUntil = LocalDateTime.now().plusMinutes(2);
        return mapper.tryAcquire(sceneId, plannedFireAt, instanceId, leaseUntil) > 0;
    }

    @Override
    @Transactional
    public void markTriggered(Long sceneId, LocalDateTime plannedFireAt, Long taskId, LocalDateTime triggeredAt) {
        mapper.markTriggered(sceneId, plannedFireAt, taskId, triggeredAt);
    }
}
