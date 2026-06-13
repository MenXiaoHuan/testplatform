package com.example.platform.task.service;

import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.model.SceneEntity;

public interface TaskCommandBuilder {
    String buildRunCommand(TestRepositoryEntity repository, SceneEntity scene);
}
