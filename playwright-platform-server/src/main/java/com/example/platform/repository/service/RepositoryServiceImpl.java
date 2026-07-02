package com.example.platform.repository.service;

import com.example.platform.cache.DetailCacheService;
import com.example.platform.common.PageResponse;
import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles repository CRUD with short write transactions and detail-cache invalidation.
 */
@Service
public class RepositoryServiceImpl implements RepositoryService {
    private final TestRepositoryMapper repository;
    private final RepositoryCascadeDeleteService repositoryCascadeDeleteService;
    private final DetailCacheService detailCacheService;

    @Autowired
    public RepositoryServiceImpl(
            TestRepositoryMapper repository,
            RepositoryCascadeDeleteService repositoryCascadeDeleteService,
            DetailCacheService detailCacheService) {
        this.repository = repository;
        this.repositoryCascadeDeleteService = repositoryCascadeDeleteService;
        this.detailCacheService = detailCacheService;
    }

    public RepositoryServiceImpl(
            TestRepositoryMapper repository,
            RepositoryCascadeDeleteService repositoryCascadeDeleteService) {
        this(repository, repositoryCascadeDeleteService, null);
    }

    @Override
    @Transactional
    public TestRepositoryEntity create(TestRepositoryEntity entity) {
        if (entity.getSpaceId() == null || entity.getSpaceId() <= 0) {
            throw new IllegalArgumentException("Repository space is required");
        }
        String normalizedName = normalizeName(entity.getName());
        validateUniqueName(normalizedName, null);
        entity.setName(normalizedName);
        repository.insert(entity);
        invalidateDetail(entity.getSpaceId(), entity.getId());
        return entity;
    }

    @Override
    public PageResponse<TestRepositoryEntity> list(Long spaceId, int page, int size) {
        validateSpaceId(spaceId);
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = (normalizedPage - 1) * normalizedSize;
        return PageResponse.of(
                repository.findPageBySpaceId(spaceId, normalizedSize, offset),
                repository.countBySpaceId(spaceId),
                normalizedPage,
                normalizedSize);
    }

    @Override
    public TestRepositoryEntity get(Long spaceId, Long id) {
        validateSpaceId(spaceId);
        return getOptional(spaceId, id)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + id));
    }

    @Override
    @Transactional
    public TestRepositoryEntity update(Long spaceId, Long id, TestRepositoryEntity entity) {
        validateSpaceId(spaceId);
        if (entity.getSpaceId() != null && !spaceId.equals(entity.getSpaceId())) {
            throw new IllegalArgumentException("Repository space mismatch");
        }
        TestRepositoryEntity existing = get(spaceId, id);
        String normalizedName = normalizeName(entity.getName());
        validateUniqueName(normalizedName, id);
        existing.setSpaceId(spaceId);
        existing.setName(normalizedName);
        existing.setGitUrl(entity.getGitUrl());
        existing.setDefaultBranch(entity.getDefaultBranch());
        existing.setWorkingDirectory(entity.getWorkingDirectory());
        existing.setInstallCommand(entity.getInstallCommand());
        existing.setRunCommandTemplate(entity.getRunCommandTemplate());
        existing.setTestRoot(entity.getTestRoot());
        existing.setResultsIndexRelativePath(entity.getResultsIndexRelativePath());
        existing.setArtifactRootRelativePath(entity.getArtifactRootRelativePath());
        existing.setEnabled(entity.getEnabled());
        repository.update(existing);
        invalidateDetail(spaceId, id);
        return existing;
    }

    @Override
    @Transactional
    public void delete(Long spaceId, Long id) {
        validateSpaceId(spaceId);
        get(spaceId, id);
        repositoryCascadeDeleteService.deleteRepositoryGraph(id);
        invalidateDetail(spaceId, id);
    }

    private Optional<TestRepositoryEntity> getOptional(Long spaceId, Long id) {
        if (detailCacheService == null) {
            return repository.findByIdAndSpaceId(id, spaceId);
        }
        return detailCacheService.getOrLoad(detailCacheKey(spaceId), id, TestRepositoryEntity.class,
                () -> repository.findByIdAndSpaceId(id, spaceId));
    }

    private void invalidateDetail(Long spaceId, Long id) {
        if (detailCacheService != null && id != null) {
            detailCacheService.invalidate(detailCacheKey(spaceId), id);
        }
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private void validateSpaceId(Long spaceId) {
        if (spaceId == null || spaceId <= 0) {
            throw new IllegalArgumentException("Space not found");
        }
    }

    private String detailCacheKey(Long spaceId) {
        return "repository:%d".formatted(spaceId);
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("请输入仓库名称");
        }
        return normalized;
    }

    private void validateUniqueName(String name, Long currentId) {
        boolean duplicated = currentId == null
                ? repository.existsByNameIgnoreCase(name)
                : repository.existsByNameIgnoreCaseAndIdNot(name, currentId);
        if (duplicated) {
            throw new IllegalStateException("仓库名称已存在，请更换后重试");
        }
    }
}
