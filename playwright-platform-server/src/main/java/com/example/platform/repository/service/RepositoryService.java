package com.example.platform.repository.service;

import com.example.platform.common.PageResponse;
import com.example.platform.repository.model.TestRepositoryEntity;

public interface RepositoryService {
    TestRepositoryEntity create(TestRepositoryEntity entity);
    PageResponse<TestRepositoryEntity> list(int page, int size);
    TestRepositoryEntity get(Long id);
    TestRepositoryEntity update(Long id, TestRepositoryEntity entity);
    void delete(Long id);
}
