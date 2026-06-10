package com.example.platform.scene;

import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.service.SceneService;
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
        entity.setEnabled(true);

        Mockito.when(sceneService.create(Mockito.any(SceneEntity.class))).thenReturn(entity);
        Mockito.when(sceneService.list()).thenReturn(List.of(entity));

        mockMvc.perform(post("/api/scenes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "repoId": 1,
                      "name": "login-smoke",
                      "branch": "main",
                      "testSelectorType": "file",
                      "testSelectorValue": "tests/login.spec.ts",
                      "runCommand": "node ./scripts/run-e2e.cjs --target tests/login.spec.ts",
                      "enabled": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("login-smoke"));

        mockMvc.perform(get("/api/scenes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("login-smoke"));
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
        entity.setEnabled(true);

        SceneEntity updated = new SceneEntity();
        updated.setId(1L);
        updated.setRepoId(1L);
        updated.setName("login-regression");
        updated.setBranch("release");
        updated.setTestSelectorType("grep");
        updated.setTestSelectorValue("@smoke");
        updated.setRunCommand("node ./scripts/run-e2e.cjs --grep @smoke");
        updated.setEnabled(false);

        Mockito.when(sceneService.get(1L)).thenReturn(entity);
        Mockito.when(sceneService.update(Mockito.eq(1L), Mockito.any(SceneEntity.class))).thenReturn(updated);

        mockMvc.perform(get("/api/scenes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("login-smoke"))
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(put("/api/scenes/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "repoId": 1,
                      "name": "login-regression",
                      "branch": "release",
                      "testSelectorType": "grep",
                      "testSelectorValue": "@smoke",
                      "runCommand": "node ./scripts/run-e2e.cjs --grep @smoke",
                      "enabled": false
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("login-regression"))
                .andExpect(jsonPath("$.branch").value("release"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void shouldDeleteScene() throws Exception {
        mockMvc.perform(delete("/api/scenes/1"))
                .andExpect(status().isNoContent());

        Mockito.verify(sceneService).delete(1L);
    }
}
