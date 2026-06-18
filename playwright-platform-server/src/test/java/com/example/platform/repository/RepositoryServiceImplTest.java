package com.example.platform.repository;

import com.example.platform.cache.DetailCacheService;
import com.example.platform.common.PageResponse;
import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.service.RepositoryCascadeDeleteService;
import com.example.platform.repository.service.RepositoryServiceImpl;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryServiceImplTest {
    @Test
    void shouldCreateRepositoryWithoutLegacyRuntimeFields() {
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        RepositoryCascadeDeleteService repositoryCascadeDeleteService = Mockito.mock(RepositoryCascadeDeleteService.class);
        DetailCacheService detailCacheService = Mockito.mock(DetailCacheService.class);

        TestRepositoryEntity payload = new TestRepositoryEntity();
        payload.setName("playwright-framework");
        payload.setGitUrl("https://github.com/demo/testframe.git");
        payload.setDefaultBranch("main");
        payload.setWorkingDirectory("playwright_framework");
        payload.setInstallCommand("npm install");
        payload.setRunCommandTemplate("npm run test:e2e --");
        payload.setTestRoot("tests");
        payload.setResultsIndexRelativePath("test-results/.playwright-results.json");
        payload.setArtifactRootRelativePath(".playwright-artifacts");
        payload.setEnabled(true);

        Mockito.when(repositoryMapper.insert(Mockito.any(TestRepositoryEntity.class))).thenAnswer(invocation -> {
            TestRepositoryEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });

        RepositoryServiceImpl service = new RepositoryServiceImpl(
                repositoryMapper,
                repositoryCascadeDeleteService,
                detailCacheService);

        TestRepositoryEntity result = service.create(payload);

        ArgumentCaptor<TestRepositoryEntity> captor = ArgumentCaptor.forClass(TestRepositoryEntity.class);
        Mockito.verify(repositoryMapper).insert(captor.capture());
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getWorkingDirectory()).isEqualTo("playwright_framework");
        assertThat(captor.getValue().getWorkingDirectory()).isEqualTo("playwright_framework");
        Mockito.verify(detailCacheService).invalidate("repository", 1L);
    }

    @Test
    void shouldUpdateRepositoryWithoutLegacyRuntimeFields() {
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        RepositoryCascadeDeleteService repositoryCascadeDeleteService = Mockito.mock(RepositoryCascadeDeleteService.class);
        DetailCacheService detailCacheService = Mockito.mock(DetailCacheService.class);

        TestRepositoryEntity existing = new TestRepositoryEntity();
        existing.setId(1L);
        existing.setName("demo");
        existing.setGitUrl("https://github.com/demo/testframe.git");
        existing.setDefaultBranch("main");
        existing.setInstallCommand("npm install");
        existing.setRunCommandTemplate("npm run test:e2e --");
        existing.setTestRoot("tests");
        existing.setResultsIndexRelativePath("test-results/.playwright-results.json");
        existing.setArtifactRootRelativePath(".playwright-artifacts");
        existing.setEnabled(true);

        TestRepositoryEntity payload = new TestRepositoryEntity();
        payload.setName("demo-updated");
        payload.setGitUrl("https://github.com/demo/testframe.git");
        payload.setDefaultBranch("release");
        payload.setWorkingDirectory("playwright_framework");
        payload.setInstallCommand("npm ci");
        payload.setRunCommandTemplate("npm run test:e2e --");
        payload.setTestRoot("tests");
        payload.setResultsIndexRelativePath("test-results/.playwright-results.json");
        payload.setArtifactRootRelativePath(".playwright-artifacts");
        payload.setEnabled(false);

        Mockito.when(repositoryMapper.findById(1L)).thenReturn(Optional.of(existing));
        Mockito.when(repositoryMapper.update(Mockito.any(TestRepositoryEntity.class))).thenReturn(1);
        Mockito.when(detailCacheService.getOrLoad(
                        Mockito.eq("repository"),
                        Mockito.eq(1L),
                        Mockito.eq(TestRepositoryEntity.class),
                        Mockito.any()))
                .thenAnswer(invocation -> invocation.<Supplier<Optional<TestRepositoryEntity>>>getArgument(3).get());

        RepositoryServiceImpl service = new RepositoryServiceImpl(
                repositoryMapper,
                repositoryCascadeDeleteService,
                detailCacheService);

        TestRepositoryEntity result = service.update(1L, payload);

        Mockito.verify(repositoryMapper).update(existing);
        assertThat(result.getWorkingDirectory()).isEqualTo("playwright_framework");
        assertThat(result.getDefaultBranch()).isEqualTo("release");
        Mockito.verify(detailCacheService).invalidate("repository", 1L);
    }

    @Test
    void shouldRejectDuplicateRepositoryNameWhenCreating() {
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        RepositoryCascadeDeleteService repositoryCascadeDeleteService = Mockito.mock(RepositoryCascadeDeleteService.class);
        RepositoryServiceImpl service = new RepositoryServiceImpl(repositoryMapper, repositoryCascadeDeleteService);

        TestRepositoryEntity payload = new TestRepositoryEntity();
        payload.setName("demo-repo");

        Mockito.when(repositoryMapper.existsByNameIgnoreCase("demo-repo")).thenReturn(true);

        assertThatThrownBy(() -> service.create(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("仓库名称已存在，请更换后重试");
        Mockito.verify(repositoryMapper, Mockito.never()).insert(Mockito.any(TestRepositoryEntity.class));
    }

    @Test
    void shouldRejectDuplicateRepositoryNameWhenUpdating() {
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        RepositoryCascadeDeleteService repositoryCascadeDeleteService = Mockito.mock(RepositoryCascadeDeleteService.class);
        RepositoryServiceImpl service = new RepositoryServiceImpl(repositoryMapper, repositoryCascadeDeleteService);

        TestRepositoryEntity existing = new TestRepositoryEntity();
        existing.setId(1L);
        existing.setName("demo-repo");

        TestRepositoryEntity payload = new TestRepositoryEntity();
        payload.setName("demo-repo");

        Mockito.when(repositoryMapper.findById(1L)).thenReturn(Optional.of(existing));
        Mockito.when(repositoryMapper.existsByNameIgnoreCaseAndIdNot("demo-repo", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("仓库名称已存在，请更换后重试");
        Mockito.verify(repositoryMapper, Mockito.never()).update(Mockito.any(TestRepositoryEntity.class));
    }

    @Test
    void shouldNormalizePaginationWhenListingRepositories() {
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        RepositoryServiceImpl service = new RepositoryServiceImpl(
                repositoryMapper,
                Mockito.mock(RepositoryCascadeDeleteService.class));
        TestRepositoryEntity entity = new TestRepositoryEntity();
        entity.setId(1L);
        Mockito.when(repositoryMapper.countAll()).thenReturn(1L);
        Mockito.when(repositoryMapper.findPage(100, 0)).thenReturn(List.of(entity));

        PageResponse<TestRepositoryEntity> response = service.list(0, 200);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(100);
        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.items()).containsExactly(entity);
        Mockito.verify(repositoryMapper).findPage(100, 0);
    }

    @Test
    void shouldThrowWhenRepositoryNotFound() {
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        RepositoryServiceImpl service = new RepositoryServiceImpl(
                repositoryMapper,
                Mockito.mock(RepositoryCascadeDeleteService.class));
        Mockito.when(repositoryMapper.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Repository not found: 404");
    }

    @Test
    void shouldReturnRepositoryFromDetailCacheHit() {
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        DetailCacheService detailCacheService = Mockito.mock(DetailCacheService.class);
        RepositoryServiceImpl service = new RepositoryServiceImpl(
                repositoryMapper,
                Mockito.mock(RepositoryCascadeDeleteService.class),
                detailCacheService);
        TestRepositoryEntity cached = new TestRepositoryEntity();
        cached.setId(1L);
        cached.setName("cached");
        Mockito.when(detailCacheService.getOrLoad(
                        Mockito.eq("repository"),
                        Mockito.eq(1L),
                        Mockito.eq(TestRepositoryEntity.class),
                        Mockito.any()))
                .thenReturn(Optional.of(cached));

        TestRepositoryEntity result = service.get(1L);

        assertThat(result).isSameAs(cached);
        Mockito.verify(repositoryMapper, Mockito.never()).findById(Mockito.anyLong());
    }

    @Test
    void shouldThrowWhenRepositoryEmptyValueIsCached() {
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        DetailCacheService detailCacheService = Mockito.mock(DetailCacheService.class);
        RepositoryServiceImpl service = new RepositoryServiceImpl(
                repositoryMapper,
                Mockito.mock(RepositoryCascadeDeleteService.class),
                detailCacheService);
        Mockito.when(detailCacheService.getOrLoad(
                        Mockito.eq("repository"),
                        Mockito.eq(404L),
                        Mockito.eq(TestRepositoryEntity.class),
                        Mockito.any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Repository not found: 404");
        Mockito.verify(repositoryMapper, Mockito.never()).findById(Mockito.anyLong());
    }

    @Test
    void shouldInvalidateRepositoryDetailCacheWhenDeleting() {
        TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
        RepositoryCascadeDeleteService repositoryCascadeDeleteService = Mockito.mock(RepositoryCascadeDeleteService.class);
        DetailCacheService detailCacheService = Mockito.mock(DetailCacheService.class);
        RepositoryServiceImpl service = new RepositoryServiceImpl(
                repositoryMapper,
                repositoryCascadeDeleteService,
                detailCacheService);

        service.delete(7L);

        Mockito.verify(repositoryCascadeDeleteService).deleteRepositoryGraph(7L);
        Mockito.verify(detailCacheService).invalidate("repository", 7L);
    }
}
