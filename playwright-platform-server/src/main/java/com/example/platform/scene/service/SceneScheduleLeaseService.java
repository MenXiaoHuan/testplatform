package com.example.platform.scene.service;

import java.time.LocalDateTime;

public interface SceneScheduleLeaseService {
    boolean tryAcquire(Long sceneId, LocalDateTime plannedFireAt);
}
