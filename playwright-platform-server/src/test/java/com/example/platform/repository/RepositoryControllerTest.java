package com.example.platform.repository;

import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.service.RepositoryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = com.example.platform.repository.controller.RepositoryController.class)
class RepositoryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RepositoryService repositoryService;

    @Test
    void shouldCreateAndListRepository() throws Exception {
        TestRepositoryEntity entity = new TestRepositoryEntity();
        entity.setId(1L);
        entity.setName("demo-repo");
        entity.setGitUrl("git@demo/repo.git");
        entity.setDefaultBranch("main");
        entity.setPackageManager("npm");
        entity.setInstallCommand("npm install");
        entity.setRunCommandTemplate("node ./scripts/run-e2e.cjs");
        entity.setTestRoot("tests");
        entity.setReportRelativePath("reports/allure-report");
        entity.setResultsIndexRelativePath("test-results/.playwright-results.json");
        entity.setArtifactRootRelativePath(".playwright-artifacts");
        entity.setNodeVersion("21");
        entity.setEnabled(true);

        Mockito.when(repositoryService.create(Mockito.any(TestRepositoryEntity.class))).thenReturn(entity);
        Mockito.when(repositoryService.list()).thenReturn(List.of(entity));

        mockMvc.perform(post("/api/repos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "demo-repo",
                      "gitUrl": "git@demo/repo.git",
                      "defaultBranch": "main",
                      "packageManager": "npm",
                      "installCommand": "npm install",
                      "runCommandTemplate": "node ./scripts/run-e2e.cjs",
                      "testRoot": "tests",
                      "reportRelativePath": "reports/allure-report",
                      "resultsIndexRelativePath": "test-results/.playwright-results.json",
                      "artifactRootRelativePath": ".playwright-artifacts",
                      "nodeVersion": "21",
                      "enabled": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("demo-repo"))
            .andExpect(jsonPath("$.resultsIndexRelativePath").value("test-results/.playwright-results.json"))
            .andExpect(jsonPath("$.artifactRootRelativePath").value(".playwright-artifacts"));

        mockMvc.perform(get("/api/repos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("demo-repo"))
            .andExpect(jsonPath("$[0].resultsIndexRelativePath").value("test-results/.playwright-results.json"))
            .andExpect(jsonPath("$[0].artifactRootRelativePath").value(".playwright-artifacts"));
    }

    @Test
    void shouldGetAndUpdateRepository() throws Exception {
        TestRepositoryEntity entity = new TestRepositoryEntity();
        entity.setId(1L);
        entity.setName("demo-repo");
        entity.setGitUrl("git@demo/repo.git");
        entity.setDefaultBranch("main");
        entity.setPackageManager("npm");
        entity.setInstallCommand("npm install");
        entity.setRunCommandTemplate("node ./scripts/run-e2e.cjs");
        entity.setTestRoot("tests");
        entity.setReportRelativePath("reports/allure-report");
        entity.setResultsIndexRelativePath("test-results/.playwright-results.json");
        entity.setArtifactRootRelativePath(".playwright-artifacts");
        entity.setNodeVersion("21");
        entity.setEnabled(true);

        TestRepositoryEntity updated = new TestRepositoryEntity();
        updated.setId(1L);
        updated.setName("demo-repo-updated");
        updated.setGitUrl("git@demo/repo.git");
        updated.setDefaultBranch("release");
        updated.setPackageManager("npm");
        updated.setInstallCommand("npm ci");
        updated.setRunCommandTemplate("node ./scripts/run-e2e.cjs --grep smoke");
        updated.setTestRoot("tests");
        updated.setReportRelativePath("reports/allure-report");
        updated.setResultsIndexRelativePath("test-results/results.json");
        updated.setArtifactRootRelativePath(".playwright-output");
        updated.setNodeVersion("21");
        updated.setEnabled(false);

        Mockito.when(repositoryService.get(1L)).thenReturn(entity);
        Mockito.when(repositoryService.update(Mockito.eq(1L), Mockito.any(TestRepositoryEntity.class))).thenReturn(updated);

        mockMvc.perform(get("/api/repos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("demo-repo"))
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(put("/api/repos/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "demo-repo-updated",
                      "gitUrl": "git@demo/repo.git",
                      "defaultBranch": "release",
                      "packageManager": "npm",
                      "installCommand": "npm ci",
                      "runCommandTemplate": "node ./scripts/run-e2e.cjs --grep smoke",
                      "testRoot": "tests",
                      "reportRelativePath": "reports/allure-report",
                      "resultsIndexRelativePath": "test-results/results.json",
                      "artifactRootRelativePath": ".playwright-output",
                      "nodeVersion": "21",
                      "enabled": false
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("demo-repo-updated"))
                .andExpect(jsonPath("$.defaultBranch").value("release"))
                .andExpect(jsonPath("$.resultsIndexRelativePath").value("test-results/results.json"))
                .andExpect(jsonPath("$.artifactRootRelativePath").value(".playwright-output"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void shouldDeleteRepository() throws Exception {
        mockMvc.perform(delete("/api/repos/1"))
                .andExpect(status().isNoContent());

        Mockito.verify(repositoryService).delete(1L);
    }

    @Test
    void shouldReturnStructuredErrorWhenRepositoryIsMissing() throws Exception {
        Mockito.when(repositoryService.get(99L))
                .thenThrow(new IllegalArgumentException("Repository not found: 99"));

        mockMvc.perform(get("/api/repos/99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Repository not found: 99"));
    }
}
