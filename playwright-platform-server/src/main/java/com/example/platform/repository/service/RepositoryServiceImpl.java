package com.example.platform.repository.service;

import com.example.platform.common.PageResponse;
import com.example.platform.repository.model.TestRepositoryJpaRepository;
import com.example.platform.repository.model.TestRepositoryEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class RepositoryServiceImpl implements RepositoryService {
    private final TestRepositoryJpaRepository repository;
    private final RepositoryCascadeDeleteService repositoryCascadeDeleteService;

    public RepositoryServiceImpl(
            TestRepositoryJpaRepository repository,
            RepositoryCascadeDeleteService repositoryCascadeDeleteService) {
        this.repository = repository;
        this.repositoryCascadeDeleteService = repositoryCascadeDeleteService;
    }

    @Override
    public TestRepositoryEntity create(TestRepositoryEntity entity) {
        String normalizedName = normalizeName(entity.getName());
        validateUniqueName(normalizedName, null);
        entity.setName(normalizedName);
        return repository.save(entity);
    }

    @Override
    public PageResponse<TestRepositoryEntity> list(int page, int size) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        return PageResponse.from(
                repository.findAll(PageRequest.of(
                        normalizedPage - 1,
                        normalizedSize,
                        Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")))),
                normalizedPage,
                normalizedSize);
    }

    @Override
    public TestRepositoryEntity get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + id));
    }

    @Override
    public TestRepositoryEntity update(Long id, TestRepositoryEntity entity) {
        TestRepositoryEntity existing = get(id);
        String normalizedName = normalizeName(entity.getName());
        validateUniqueName(normalizedName, id);
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
        return repository.save(existing);
    }

    @Override
    public void delete(Long id) {
        repositoryCascadeDeleteService.deleteRepositoryGraph(id);
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
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
