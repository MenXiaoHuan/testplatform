package com.example.platform.repository;

import com.example.platform.common.PageResponse;
import com.example.platform.audit.mapper.PlatformAuditLogMapper;
import com.example.platform.auth.config.AuthProperties;
import com.example.platform.auth.crypto.AuthKeyProvider;
import com.example.platform.auth.mapper.PlatformUserMapper;
import com.example.platform.auth.mapper.UserSessionMapper;
import com.example.platform.auth.model.AuthSession;
import com.example.platform.auth.service.AuthService;
import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.service.RepositoryService;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.mapper.ScheduleEventMapper;
import com.example.platform.scene.mapper.SceneScheduleStateMapper;
import com.example.platform.space.mapper.SpaceAccessRequestMapper;
import com.example.platform.space.mapper.SpaceMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import com.example.platform.space.service.SpaceAuthorizationService;
import com.example.platform.task.mapper.ArtifactMapper;
import com.example.platform.task.mapper.CaseResultMapper;
import com.example.platform.task.mapper.TaskMapper;
import com.example.platform.task.mapper.TaskStageLogMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.hamcrest.Matchers.nullValue;

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

    @MockitoBean
    private RepositoryService repositoryService;
    @MockitoBean private AuthService authService;
    @MockitoBean private AuthKeyProvider authKeyProvider;
    @MockitoBean private AuthProperties authProperties;
    @MockitoBean private PlatformUserMapper platformUserMapper;
    @MockitoBean private UserSessionMapper userSessionMapper;
    @MockitoBean private PlatformAuditLogMapper platformAuditLogMapper;


    @MockitoBean
    private TestRepositoryMapper testRepositoryMapper;

    @MockitoBean
    private SceneMapper sceneMapper;

    @MockitoBean
    private SceneScheduleStateMapper sceneScheduleStateMapper;

    @MockitoBean
    private ScheduleEventMapper scheduleEventMapper;
    @MockitoBean private SpaceMapper spaceMapper;
    @MockitoBean private SpaceMemberMapper spaceMemberMapper;
    @MockitoBean private SpaceAccessRequestMapper spaceAccessRequestMapper;
    @MockitoBean private SpaceAuthorizationService spaceAuthorizationService;

    @MockitoBean
    private TaskMapper taskMapper;

    @MockitoBean
    private ArtifactMapper artifactMapper;

    @MockitoBean
    private CaseResultMapper caseResultMapper;

    @MockitoBean
    private TaskStageLogMapper taskStageLogMapper;

    @Test
    void shouldCreateAndListRepository() throws Exception {
        TestRepositoryEntity entity = new TestRepositoryEntity();
        entity.setId(1L);
        entity.setName("demo-repo");
        entity.setGitUrl("git@demo/repo.git");
        entity.setDefaultBranch("main");
        entity.setWorkingDirectory("playwright_framework");
        entity.setInstallCommand("npm install");
        entity.setRunCommandTemplate("node ./scripts/run-e2e.cjs");
        entity.setTestRoot("tests");
        entity.setResultsIndexRelativePath("test-results/.playwright-results.json");
        entity.setArtifactRootRelativePath(".playwright-artifacts");
        entity.setEnabled(true);

        Mockito.when(repositoryService.create(Mockito.any(TestRepositoryEntity.class))).thenReturn(entity);
        Mockito.when(repositoryService.list(7L, 1, 10)).thenReturn(new PageResponse<>(List.of(entity), 1, 1, 10, 1, false, false));

        mockMvc.perform(authenticated(post("/api/spaces/7/repos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "demo-repo",
                      "gitUrl": "git@demo/repo.git",
                      "defaultBranch": "main",
                      "workingDirectory": "playwright_framework",
                      "installCommand": "npm install",
                      "runCommandTemplate": "node ./scripts/run-e2e.cjs",
                      "testRoot": "tests",
                      "resultsIndexRelativePath": "test-results/.playwright-results.json",
                      "artifactRootRelativePath": ".playwright-artifacts",
                      "enabled": true
                    }
                    """)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.name").value("demo-repo"))
            .andExpect(jsonPath("$.data.workingDirectory").value("playwright_framework"))
            .andExpect(jsonPath("$.data.resultsIndexRelativePath").value("test-results/.playwright-results.json"))
            .andExpect(jsonPath("$.data.artifactRootRelativePath").value(".playwright-artifacts"))
            .andExpect(jsonPath("$.msg").value("success"));

        mockMvc.perform(authenticated(get("/api/spaces/7/repos")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.items[0].name").value("demo-repo"))
            .andExpect(jsonPath("$.data.items[0].workingDirectory").value("playwright_framework"))
            .andExpect(jsonPath("$.data.items[0].resultsIndexRelativePath").value("test-results/.playwright-results.json"))
            .andExpect(jsonPath("$.data.items[0].artifactRootRelativePath").value(".playwright-artifacts"))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.msg").value("success"));
    }

    @Test
    void shouldGetAndUpdateRepository() throws Exception {
        TestRepositoryEntity entity = new TestRepositoryEntity();
        entity.setId(1L);
        entity.setName("demo-repo");
        entity.setGitUrl("git@demo/repo.git");
        entity.setDefaultBranch("main");
        entity.setWorkingDirectory("playwright_framework");
        entity.setInstallCommand("npm install");
        entity.setRunCommandTemplate("node ./scripts/run-e2e.cjs");
        entity.setTestRoot("tests");
        entity.setResultsIndexRelativePath("test-results/.playwright-results.json");
        entity.setArtifactRootRelativePath(".playwright-artifacts");
        entity.setEnabled(true);

        TestRepositoryEntity updated = new TestRepositoryEntity();
        updated.setId(1L);
        updated.setName("demo-repo-updated");
        updated.setGitUrl("git@demo/repo.git");
        updated.setDefaultBranch("release");
        updated.setWorkingDirectory("");
        updated.setInstallCommand("npm ci");
        updated.setRunCommandTemplate("node ./scripts/run-e2e.cjs --grep smoke");
        updated.setTestRoot("tests");
        updated.setResultsIndexRelativePath("test-results/results.json");
        updated.setArtifactRootRelativePath(".playwright-output");
        updated.setEnabled(false);

        Mockito.when(repositoryService.get(7L, 1L)).thenReturn(entity);
        Mockito.when(repositoryService.update(Mockito.eq(7L), Mockito.eq(1L), Mockito.any(TestRepositoryEntity.class))).thenReturn(updated);

        mockMvc.perform(authenticated(get("/api/spaces/7/repos/1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.name").value("demo-repo"))
                .andExpect(jsonPath("$.data.workingDirectory").value("playwright_framework"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.msg").value("success"));

        mockMvc.perform(authenticated(put("/api/spaces/7/repos/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "demo-repo-updated",
                      "gitUrl": "git@demo/repo.git",
                      "defaultBranch": "release",
                      "workingDirectory": "",
                      "installCommand": "npm ci",
                      "runCommandTemplate": "node ./scripts/run-e2e.cjs --grep smoke",
                      "testRoot": "tests",
                      "resultsIndexRelativePath": "test-results/results.json",
                      "artifactRootRelativePath": ".playwright-output",
                      "enabled": false
                    }
                    """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.name").value("demo-repo-updated"))
                .andExpect(jsonPath("$.data.defaultBranch").value("release"))
                .andExpect(jsonPath("$.data.workingDirectory").value(""))
                .andExpect(jsonPath("$.data.resultsIndexRelativePath").value("test-results/results.json"))
                .andExpect(jsonPath("$.data.artifactRootRelativePath").value(".playwright-output"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.msg").value("success"));
    }

    @Test
    void shouldCreateRepositoryWithoutLegacyRuntimeFields() throws Exception {
        TestRepositoryEntity entity = new TestRepositoryEntity();
        entity.setId(2L);
        entity.setName("playwright-framework");
        entity.setGitUrl("https://github.com/demo/testframe.git");
        entity.setDefaultBranch("main");
        entity.setWorkingDirectory("playwright_framework");
        entity.setInstallCommand("npm install");
        entity.setRunCommandTemplate("npm run test:e2e --");
        entity.setTestRoot("tests");
        entity.setResultsIndexRelativePath("test-results/.playwright-results.json");
        entity.setArtifactRootRelativePath(".playwright-artifacts");
        entity.setEnabled(true);

        Mockito.when(repositoryService.create(Mockito.any(TestRepositoryEntity.class))).thenReturn(entity);

        mockMvc.perform(authenticated(post("/api/spaces/7/repos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "playwright-framework",
                      "gitUrl": "https://github.com/demo/testframe.git",
                      "defaultBranch": "main",
                      "workingDirectory": "playwright_framework",
                      "installCommand": "npm install",
                      "runCommandTemplate": "npm run test:e2e --",
                      "testRoot": "tests",
                      "resultsIndexRelativePath": "test-results/.playwright-results.json",
                      "artifactRootRelativePath": ".playwright-artifacts",
                      "enabled": true
                    }
                    """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.workingDirectory").value("playwright_framework"))
                .andExpect(jsonPath("$.msg").value("success"));
    }

    @Test
    void shouldDeleteRepository() throws Exception {
        mockMvc.perform(authenticated(delete("/api/spaces/7/repos/1")))
                .andExpect(status().isNoContent());

        Mockito.verify(repositoryService).delete(7L, 1L);
    }

    @Test
    void shouldReturnConflictWhenCascadeDeletionFails() throws Exception {
        Mockito.doThrow(new IllegalStateException("Failed to delete object from storage"))
                .when(repositoryService)
                .delete(7L, 1L);

        mockMvc.perform(authenticated(delete("/api/spaces/7/repos/1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.msg").value("Failed to delete object from storage"));
    }

    @Test
    void shouldReturnStructuredErrorWhenRepositoryIsMissing() throws Exception {
        Mockito.when(repositoryService.get(7L, 99L))
                .thenThrow(new IllegalArgumentException("Repository not found: 99"));

        mockMvc.perform(authenticated(get("/api/spaces/7/repos/99")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.msg").value("Repository not found: 99"));
    }

    @Test
    void shouldReturnConflictWhenRepositoryNameAlreadyExists() throws Exception {
        Mockito.when(repositoryService.create(Mockito.any(TestRepositoryEntity.class)))
                .thenThrow(new IllegalStateException("仓库名称已存在，请更换后重试"));

        mockMvc.perform(authenticated(post("/api/spaces/7/repos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "demo-repo",
                      "gitUrl": "git@demo/repo.git",
                      "defaultBranch": "main",
                      "installCommand": "npm install",
                      "runCommandTemplate": "npx playwright test",
                      "testRoot": "tests",
                      "resultsIndexRelativePath": "test-results/.playwright-results.json",
                      "artifactRootRelativePath": ".playwright-artifacts",
                      "enabled": true
                    }
                    """)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.msg").value("仓库名称已存在，请更换后重试"));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        Mockito.when(authProperties.getCookieName()).thenReturn("platform_session");
        Mockito.when(authService.findSession("session-1"))
                .thenReturn(java.util.Optional.of(new AuthSession(
                        "session-1",
                        1L,
                        "admin",
                        "平台管理员",
                        "avatars/admin.png",
                        7L,
                        LocalDateTime.now().plusDays(14),
                        false)));
        return builder.cookie(new jakarta.servlet.http.Cookie("platform_session", "session-1"));
    }
}
