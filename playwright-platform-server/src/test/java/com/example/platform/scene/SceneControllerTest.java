package com.example.platform.scene;

import com.example.platform.common.PageResponse;
import com.example.platform.scene.dto.SceneCardResponse;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.service.SceneService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.nullValue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = com.example.platform.scene.controller.SceneController.class)
class SceneControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SceneService sceneService;

    @Test
    void shouldCreateAndListScene() throws Exception {
        SceneEntity entity = new SceneEntity();
        entity.setId(1L);
        entity.setRepoId(1L);
        entity.setName("login-smoke");
        entity.setBranch("main");
        entity.setTestSelectorType("file");
        entity.setTestSelectorValue("tests/login.spec.ts");
        entity.setRunCommand("node ./scripts/run-e2e.cjs --target tests/login.spec.ts");

        SceneCardResponse card = new SceneCardResponse(
                1L,
                1L,
                "checkout smoke",
                "nightly checkout coverage",
                "main",
                true,
                "0 0/30 * * * ?",
                "SUCCESS",
                LocalDateTime.of(2026, 6, 10, 9, 30),
                2);

        Mockito.when(sceneService.create(Mockito.any(SceneEntity.class))).thenReturn(entity);
        Mockito.when(sceneService.listCards(1, 10)).thenReturn(new PageResponse<>(List.of(card), 1, 1, 10, 1, false, false));

        mockMvc.perform(post("/api/scenes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "repoId": 1,
                      "name": "login-smoke",
                      "branch": "main",
                      "testSelectorType": "file",
                      "testSelectorValue": "tests/login.spec.ts",
                      "runCommand": "node ./scripts/run-e2e.cjs --target tests/login.spec.ts"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.name").value("login-smoke"))
            .andExpect(jsonPath("$.msg").value("success"));

        mockMvc.perform(get("/api/scenes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.items[0].name").value("checkout smoke"))
            .andExpect(jsonPath("$.data.items[0].lastTaskStatus").value("SUCCESS"))
            .andExpect(jsonPath("$.data.items[0].environmentVariableCount").value(2))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.msg").value("success"));
    }

    @Test
    void shouldCreateAndUpdateSceneWithScheduleFields() throws Exception {
        SceneEntity created = new SceneEntity();
        created.setId(2L);
        created.setRepoId(1L);
        created.setName("nightly smoke");
        created.setDescription("nightly checkout coverage");
        created.setBranch("main");
        created.setTestSelectorType("grep");
        created.setTestSelectorValue("@smoke");
        created.setRunCommand("PLAYWRIGHT_PLATFORM_MODE=true npm run test:e2e");
        created.setScheduleEnabled(true);
        created.setCronExpression("0 0 2 * * ?");

        SceneEntity updated = new SceneEntity();
        updated.setId(2L);
        updated.setRepoId(1L);
        updated.setName("nightly smoke");
        updated.setDescription("nightly checkout coverage");
        updated.setBranch("release");
        updated.setTestSelectorType("grep");
        updated.setTestSelectorValue("@smoke");
        updated.setRunCommand("PLAYWRIGHT_PLATFORM_MODE=true npm run test:e2e");
        updated.setScheduleEnabled(true);
        updated.setCronExpression("0 15 2 * * ?");

        Mockito.when(sceneService.create(Mockito.any(SceneEntity.class))).thenReturn(created);
        Mockito.when(sceneService.update(Mockito.eq(2L), Mockito.any(SceneEntity.class))).thenReturn(updated);

        mockMvc.perform(post("/api/scenes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "repoId": 1,
                      "name": "nightly smoke",
                      "description": "nightly checkout coverage",
                      "branch": "main",
                      "testSelectorType": "grep",
                      "testSelectorValue": "@smoke",
                      "runCommand": "PLAYWRIGHT_PLATFORM_MODE=true npm run test:e2e",
                      "scheduleEnabled": true,
                      "cronExpression": "0 0 2 * * ?"
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.scheduleEnabled").value(true))
                .andExpect(jsonPath("$.data.cronExpression").value("0 0 2 * * ?"))
                .andExpect(jsonPath("$.msg").value("success"));

        mockMvc.perform(put("/api/scenes/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "repoId": 1,
                      "name": "nightly smoke",
                      "description": "nightly checkout coverage",
                      "branch": "release",
                      "testSelectorType": "grep",
                      "testSelectorValue": "@smoke",
                      "runCommand": "PLAYWRIGHT_PLATFORM_MODE=true npm run test:e2e",
                      "scheduleEnabled": true,
                      "cronExpression": "0 15 2 * * ?"
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.scheduleEnabled").value(true))
                .andExpect(jsonPath("$.data.cronExpression").value("0 15 2 * * ?"))
                .andExpect(jsonPath("$.msg").value("success"));
    }

    @Test
    void shouldCreateSceneWithMatchValueCompatibility() throws Exception {
        SceneEntity entity = new SceneEntity();
        entity.setId(3L);
        entity.setRepoId(1L);
        entity.setName("all-tests");
        entity.setDescription("");
        entity.setBranch("main");
        entity.setTestSelectorType("file");
        entity.setTestSelectorValue("login.spec.ts");
        entity.setBrowser("chromium");
        entity.setRunCommand("node ./scripts/run-e2e.cjs");

        Mockito.when(sceneService.create(Mockito.any(SceneEntity.class))).thenReturn(entity);

        mockMvc.perform(post("/api/scenes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "repoId": 1,
                      "name": "all-tests",
                      "description": "",
                      "browser": "chromium",
                      "envJson": "",
                      "matchValue": "login.spec.ts",
                      "scheduleEnabled": false,
                      "cronExpression": ""
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.name").value("all-tests"))
            .andExpect(jsonPath("$.data.matchValue").value("login.spec.ts"))
            .andExpect(jsonPath("$.msg").value("success"));
    }

    @Test
    void shouldGetAndUpdateScene() throws Exception {
        SceneEntity entity = new SceneEntity();
        entity.setId(1L);
        entity.setRepoId(1L);
        entity.setName("login-smoke");
        entity.setBranch("main");
        entity.setTestSelectorType("file");
        entity.setTestSelectorValue("tests/login.spec.ts");
        entity.setRunCommand("node ./scripts/run-e2e.cjs --target tests/login.spec.ts");

        SceneEntity updated = new SceneEntity();
        updated.setId(1L);
        updated.setRepoId(1L);
        updated.setName("login-regression");
        updated.setBranch("release");
        updated.setTestSelectorType("grep");
        updated.setTestSelectorValue("@smoke");
        updated.setRunCommand("node ./scripts/run-e2e.cjs --grep @smoke");

        Mockito.when(sceneService.get(1L)).thenReturn(entity);
        Mockito.when(sceneService.update(Mockito.eq(1L), Mockito.any(SceneEntity.class))).thenReturn(updated);

        mockMvc.perform(get("/api/scenes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.name").value("login-smoke"))
                .andExpect(jsonPath("$.data.enabled").doesNotExist())
                .andExpect(jsonPath("$.msg").value("success"));

        mockMvc.perform(put("/api/scenes/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "repoId": 1,
                      "name": "login-regression",
                      "branch": "release",
                      "testSelectorType": "grep",
                      "testSelectorValue": "@smoke",
                      "runCommand": "node ./scripts/run-e2e.cjs --grep @smoke"
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.name").value("login-regression"))
                .andExpect(jsonPath("$.data.branch").value("release"))
                .andExpect(jsonPath("$.data.enabled").doesNotExist())
                .andExpect(jsonPath("$.msg").value("success"));
    }

    @Test
    void shouldDeleteScene() throws Exception {
        mockMvc.perform(delete("/api/scenes/1"))
                .andExpect(status().isNoContent());

        Mockito.verify(sceneService).delete(1L);
    }

    @Test
    void shouldReturnBadRequestForSceneBusinessErrors() throws Exception {
        Mockito.when(sceneService.create(Mockito.any(SceneEntity.class)))
                .thenThrow(new IllegalArgumentException("所属仓库已停用，请先启用仓库"));

        mockMvc.perform(post("/api/scenes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "repoId": 1,
                      "name": "nightly smoke"
                    }
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.msg").value("所属仓库已停用，请先启用仓库"));
    }

    @Test
    void shouldReturnUnifiedErrorResponseForUnexpectedSceneFailure() throws Exception {
        Mockito.when(sceneService.get(3L)).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/api/scenes/3"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.msg").value("boom"));
    }
}
