package com.example.platform.scene.service;

import com.example.platform.scene.mapper.SceneScheduleStateMapper;
import com.example.platform.scene.model.SceneScheduleStateEntity;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 场景调度租约服务实现类 —— 基于乐观锁实现多实例调度的并发控制。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #tryAcquire} —— 尝试获取场景调度租约（2 分钟有效期），确保同一场景不会被多实例重复触发</li>
 *   <li>{@link #markTriggered} —— 标记场景已触发</li>
 * </ul>
 *
 * <p>依赖：{@link SceneScheduleStateMapper}、{@link SchedulerInstanceIdProvider}。
 */
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

    /** 尝试获取场景调度租约（2 分钟有效期），确保同一场景不会被多实例重复触发。 */
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

    /** 标记场景已触发，更新触发时间与关联任务 ID。 */
    @Override
    @Transactional
    public void markTriggered(Long sceneId, LocalDateTime plannedFireAt, Long taskId, LocalDateTime triggeredAt) {
        mapper.markTriggered(sceneId, plannedFireAt, taskId, triggeredAt);
    }
}
