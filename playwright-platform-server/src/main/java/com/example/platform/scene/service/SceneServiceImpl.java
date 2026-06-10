package com.example.platform.scene.service;

import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.model.SceneJpaRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SceneServiceImpl implements SceneService {
    private final SceneJpaRepository repository;

    public SceneServiceImpl(SceneJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public SceneEntity create(SceneEntity entity) {
        return repository.save(entity);
    }

    @Override
    public List<SceneEntity> list() {
        return repository.findAll();
    }

    @Override
    public SceneEntity get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + id));
    }

    @Override
    public SceneEntity update(Long id, SceneEntity entity) {
        SceneEntity existing = get(id);
        existing.setRepoId(entity.getRepoId());
        existing.setName(entity.getName());
        existing.setBranch(entity.getBranch());
        existing.setTestSelectorType(entity.getTestSelectorType());
        existing.setTestSelectorValue(entity.getTestSelectorValue());
        existing.setProjectName(entity.getProjectName());
        existing.setBrowser(entity.getBrowser());
        existing.setEnvJson(entity.getEnvJson());
        existing.setRunCommand(entity.getRunCommand());
        existing.setEnabled(entity.getEnabled());
        return repository.save(existing);
    }

    @Override
    public void delete(Long id) {
        repository.delete(get(id));
    }
}
