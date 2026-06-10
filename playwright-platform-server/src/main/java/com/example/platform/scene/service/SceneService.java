package com.example.platform.scene.service;

import com.example.platform.scene.model.SceneEntity;
import java.util.List;

public interface SceneService {
    SceneEntity create(SceneEntity entity);
    List<SceneEntity> list();
    SceneEntity get(Long id);
    SceneEntity update(Long id, SceneEntity entity);
    void delete(Long id);
}
