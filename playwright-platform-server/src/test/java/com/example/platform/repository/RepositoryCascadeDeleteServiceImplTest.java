package com.example.platform.repository;

import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.model.TestRepositoryJpaRepository;
import com.example.platform.repository.service.RepositoryCascadeDeleteServiceImpl;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.model.SceneJpaRepository;
import com.example.platform.scene.service.SceneCascadeDeleteService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RepositoryCascadeDeleteServiceImplTest {
    @Test
    void shouldDeleteRepositoryGraphThroughSceneCascadeDeletion() {
        TestRepositoryJpaRepository repositoryRepository = Mockito.mock(TestRepositoryJpaRepository.class);
        SceneJpaRepository sceneRepository = Mockito.mock(SceneJpaRepository.class);
        SceneCascadeDeleteService sceneCascadeDeleteService = Mockito.mock(SceneCascadeDeleteService.class);

        TestRepositoryEntity repository = new TestRepositoryEntity();
        repository.setId(1L);

        SceneEntity scene = new SceneEntity();
        scene.setId(11L);
        scene.setRepoId(1L);

        Mockito.when(repositoryRepository.findById(1L)).thenReturn(Optional.of(repository));
        Mockito.when(sceneRepository.findAllByRepoId(1L)).thenReturn(List.of(scene));

        RepositoryCascadeDeleteServiceImpl service = new RepositoryCascadeDeleteServiceImpl(
                repositoryRepository,
                sceneRepository,
                sceneCascadeDeleteService);

        service.deleteRepositoryGraph(1L);

        Mockito.verify(sceneCascadeDeleteService).deleteSceneGraph(11L);
        Mockito.verify(repositoryRepository).delete(repository);
    }
}
