package com.example.platform.scene.service;

import com.example.platform.common.PageResponse;
import com.example.platform.scene.dto.SceneCardResponse;
import com.example.platform.scene.model.SceneEntity;

public interface SceneService {
    SceneEntity create(SceneEntity entity);
    PageResponse<SceneCardResponse> listCards(int page, int size);
    SceneEntity get(Long id);
    SceneEntity update(Long id, SceneEntity entity);
    void delete(Long id);
    void deleteAllByRepoId(Long repoId);
}
