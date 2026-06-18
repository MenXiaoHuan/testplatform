package com.example.platform.repository.service;

import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.service.SceneCascadeDeleteService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepositoryCascadeDeleteServiceImpl implements RepositoryCascadeDeleteService {
    private final TestRepositoryMapper repositoryMapper;
    private final SceneMapper sceneMapper;
    private final SceneCascadeDeleteService sceneCascadeDeleteService;

    public RepositoryCascadeDeleteServiceImpl(
            TestRepositoryMapper repositoryMapper,
            SceneMapper sceneMapper,
            SceneCascadeDeleteService sceneCascadeDeleteService) {
        this.repositoryMapper = repositoryMapper;
        this.sceneMapper = sceneMapper;
        this.sceneCascadeDeleteService = sceneCascadeDeleteService;
    }

    @Override
    @Transactional
    public void deleteRepositoryGraph(Long repoId) {
        Optional<TestRepositoryEntity> repository = repositoryMapper.findById(repoId);
        if (repository.isEmpty()) {
            return;
        }
        List<SceneEntity> scenes = sceneMapper.findAllByRepoId(repoId);
        for (SceneEntity scene : scenes) {
            sceneCascadeDeleteService.deleteSceneGraph(scene.getId());
        }
        repositoryMapper.deleteById(repoId);
    }
}
