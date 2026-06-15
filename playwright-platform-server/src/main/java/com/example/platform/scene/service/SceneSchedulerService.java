package com.example.platform.scene.service;

import java.time.LocalDateTime;

public interface SceneSchedulerService {
    void triggerDueScenes(LocalDateTime now);
}
