package com.example.platform.repository.service;

import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.model.TestRepositoryJpaRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RepositoryServiceImpl implements RepositoryService {
    private final TestRepositoryJpaRepository repository;

    public RepositoryServiceImpl(TestRepositoryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public TestRepositoryEntity create(TestRepositoryEntity entity) {
        return repository.save(entity);
    }

    @Override
    public List<TestRepositoryEntity> list() {
        return repository.findAll();
    }

    @Override
    public TestRepositoryEntity get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + id));
    }

    @Override
    public TestRepositoryEntity update(Long id, TestRepositoryEntity entity) {
        TestRepositoryEntity existing = get(id);
        existing.setName(entity.getName());
        existing.setGitUrl(entity.getGitUrl());
        existing.setDefaultBranch(entity.getDefaultBranch());
        existing.setPackageManager(entity.getPackageManager());
        existing.setInstallCommand(entity.getInstallCommand());
        existing.setRunCommandTemplate(entity.getRunCommandTemplate());
        existing.setTestRoot(entity.getTestRoot());
        existing.setReportRelativePath(entity.getReportRelativePath());
        existing.setResultsIndexRelativePath(entity.getResultsIndexRelativePath());
        existing.setArtifactRootRelativePath(entity.getArtifactRootRelativePath());
        existing.setNodeVersion(entity.getNodeVersion());
        existing.setEnabled(entity.getEnabled());
        return repository.save(existing);
    }

    @Override
    public void delete(Long id) {
        repository.delete(get(id));
    }
}
