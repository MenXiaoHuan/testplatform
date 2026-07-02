package com.example.platform.repository.service;

import com.example.platform.common.PageResponse;
import com.example.platform.repository.model.TestRepositoryEntity;

public interface RepositoryService {
    TestRepositoryEntity create(TestRepositoryEntity entity);
    PageResponse<TestRepositoryEntity> list(Long spaceId, int page, int size);
    TestRepositoryEntity get(Long spaceId, Long id);
    TestRepositoryEntity update(Long spaceId, Long id, TestRepositoryEntity entity);
    void delete(Long spaceId, Long id);
}
