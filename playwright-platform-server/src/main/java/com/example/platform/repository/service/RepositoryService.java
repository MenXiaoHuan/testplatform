package com.example.platform.repository.service;

import com.example.platform.repository.model.TestRepositoryEntity;
import java.util.List;

public interface RepositoryService {
    TestRepositoryEntity create(TestRepositoryEntity entity);
    List<TestRepositoryEntity> list();
    TestRepositoryEntity get(Long id);
    TestRepositoryEntity update(Long id, TestRepositoryEntity entity);
    void delete(Long id);
}
