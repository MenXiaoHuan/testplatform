package com.example.platform.repository;

import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.model.TestRepositoryJpaRepository;
import com.example.platform.repository.service.RepositoryCascadeDeleteService;
import com.example.platform.repository.service.RepositoryServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryServiceImplTest {
    @Test
    void shouldCreateRepositoryWithoutLegacyRuntimeFields() {
        TestRepositoryJpaRepository repositoryJpaRepository = Mockito.mock(TestRepositoryJpaRepository.class);
        RepositoryCascadeDeleteService repositoryCascadeDeleteService = Mockito.mock(RepositoryCascadeDeleteService.class);

        TestRepositoryEntity payload = new TestRepositoryEntity();
        payload.setName("playwright-framework");
        payload.setGitUrl("https://github.com/demo/testframe.git");
        payload.setDefaultBranch("main");
        payload.setWorkingDirectory("playwright_framework");
        payload.setInstallCommand("npm install");
        payload.setRunCommandTemplate("npm run test:e2e --");
        payload.setTestRoot("tests");
        payload.setReportRelativePath("reports/allure-report");
        payload.setResultsIndexRelativePath("test-results/.playwright-results.json");
        payload.setArtifactRootRelativePath(".playwright-artifacts");
        payload.setEnabled(true);

        Mockito.when(repositoryJpaRepository.save(Mockito.any(TestRepositoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RepositoryServiceImpl service = new RepositoryServiceImpl(repositoryJpaRepository, repositoryCascadeDeleteService);

        TestRepositoryEntity result = service.create(payload);

        ArgumentCaptor<TestRepositoryEntity> captor = ArgumentCaptor.forClass(TestRepositoryEntity.class);
        Mockito.verify(repositoryJpaRepository).save(captor.capture());
        assertThat(result.getWorkingDirectory()).isEqualTo("playwright_framework");
        assertThat(captor.getValue().getWorkingDirectory()).isEqualTo("playwright_framework");
    }

    @Test
    void shouldUpdateRepositoryWithoutLegacyRuntimeFields() {
        TestRepositoryJpaRepository repositoryJpaRepository = Mockito.mock(TestRepositoryJpaRepository.class);
        RepositoryCascadeDeleteService repositoryCascadeDeleteService = Mockito.mock(RepositoryCascadeDeleteService.class);

        TestRepositoryEntity existing = new TestRepositoryEntity();
        existing.setId(1L);
        existing.setName("demo");
        existing.setGitUrl("https://github.com/demo/testframe.git");
        existing.setDefaultBranch("main");
        existing.setInstallCommand("npm install");
        existing.setRunCommandTemplate("npm run test:e2e --");
        existing.setTestRoot("tests");
        existing.setReportRelativePath("reports/allure-report");
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
        payload.setReportRelativePath("reports/allure-report");
        payload.setResultsIndexRelativePath("test-results/.playwright-results.json");
        payload.setArtifactRootRelativePath(".playwright-artifacts");
        payload.setEnabled(false);

        Mockito.when(repositoryJpaRepository.findById(1L)).thenReturn(Optional.of(existing));
        Mockito.when(repositoryJpaRepository.save(Mockito.any(TestRepositoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RepositoryServiceImpl service = new RepositoryServiceImpl(repositoryJpaRepository, repositoryCascadeDeleteService);

        TestRepositoryEntity result = service.update(1L, payload);

        assertThat(result.getWorkingDirectory()).isEqualTo("playwright_framework");
        assertThat(result.getDefaultBranch()).isEqualTo("release");
    }
}
