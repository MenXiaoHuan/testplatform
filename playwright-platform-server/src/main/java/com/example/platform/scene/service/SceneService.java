package com.example.platform.scene.service;

import com.example.platform.common.PageResponse;
import com.example.platform.scene.dto.SceneCardResponse;
import com.example.platform.scene.model.SceneEntity;

public interface SceneService {
    SceneEntity create(SceneEntity entity);
    PageResponse<SceneCardResponse> listCards(Long spaceId, int page, int size);
    SceneEntity get(Long spaceId, Long id);
    SceneEntity update(Long spaceId, Long id, SceneEntity entity);
    void delete(Long spaceId, Long id);
    void deleteAllByRepoId(Long repoId);
}
