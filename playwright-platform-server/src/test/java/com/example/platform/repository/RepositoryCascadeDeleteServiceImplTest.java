package com.example.platform.repository;

import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.repository.service.RepositoryCascadeDeleteServiceImpl;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.service.SceneCascadeDeleteService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class RepositoryCascadeDeleteServiceImplTest {
    @Test
    void shouldDeleteRepositoryGraphThroughSceneCascadeDeletion() {
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        SceneMapper sceneMapper = Mockito.mock(SceneMapper.class);
        SceneCascadeDeleteService sceneCascadeDeleteService = Mockito.mock(SceneCascadeDeleteService.class);

        TestRepositoryEntity repository = new TestRepositoryEntity();
        repository.setId(1L);

        SceneEntity scene = new SceneEntity();
        scene.setId(11L);
        scene.setRepoId(1L);

        Mockito.when(repositoryMapper.findById(1L)).thenReturn(Optional.of(repository));
        Mockito.when(sceneMapper.findAllByRepoId(1L)).thenReturn(List.of(scene));

        RepositoryCascadeDeleteServiceImpl service = new RepositoryCascadeDeleteServiceImpl(
                repositoryMapper,
                sceneMapper,
                sceneCascadeDeleteService);

        service.deleteRepositoryGraph(1L);

        InOrder inOrder = Mockito.inOrder(sceneCascadeDeleteService, repositoryMapper);
        inOrder.verify(sceneCascadeDeleteService).deleteSceneGraph(11L);
        inOrder.verify(repositoryMapper).deleteById(1L);
    }

    @Test
    void shouldSkipDeleteWhenRepositoryIsMissing() {
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        SceneMapper sceneMapper = Mockito.mock(SceneMapper.class);
        SceneCascadeDeleteService sceneCascadeDeleteService = Mockito.mock(SceneCascadeDeleteService.class);
        Mockito.when(repositoryMapper.findById(404L)).thenReturn(Optional.empty());

        RepositoryCascadeDeleteServiceImpl service = new RepositoryCascadeDeleteServiceImpl(
                repositoryMapper,
                sceneMapper,
                sceneCascadeDeleteService);

        service.deleteRepositoryGraph(404L);

        Mockito.verify(sceneMapper, Mockito.never()).findAllByRepoId(Mockito.anyLong());
        Mockito.verify(sceneCascadeDeleteService, Mockito.never()).deleteSceneGraph(Mockito.anyLong());
        Mockito.verify(repositoryMapper, Mockito.never()).deleteById(Mockito.anyLong());
    }
}
