package com.example.platform.repository.service;

import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.model.TestRepositoryJpaRepository;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.model.SceneJpaRepository;
import com.example.platform.scene.service.SceneCascadeDeleteService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepositoryCascadeDeleteServiceImpl implements RepositoryCascadeDeleteService {
    private final TestRepositoryJpaRepository repositoryRepository;
    private final SceneJpaRepository sceneRepository;
    private final SceneCascadeDeleteService sceneCascadeDeleteService;

    public RepositoryCascadeDeleteServiceImpl(
            TestRepositoryJpaRepository repositoryRepository,
            SceneJpaRepository sceneRepository,
            SceneCascadeDeleteService sceneCascadeDeleteService) {
        this.repositoryRepository = repositoryRepository;
        this.sceneRepository = sceneRepository;
        this.sceneCascadeDeleteService = sceneCascadeDeleteService;
    }

    @Override
    @Transactional
    public void deleteRepositoryGraph(Long repoId) {
        TestRepositoryEntity repository = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repoId));
        List<SceneEntity> scenes = sceneRepository.findAllByRepoId(repoId);
        for (SceneEntity scene : scenes) {
            sceneCascadeDeleteService.deleteSceneGraph(scene.getId());
        }
        repositoryRepository.delete(repository);
    }
}
