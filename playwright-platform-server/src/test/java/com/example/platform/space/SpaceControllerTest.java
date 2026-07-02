package com.example.platform.space;

import com.example.platform.audit.mapper.PlatformAuditLogMapper;
import com.example.platform.auth.config.AuthProperties;
import com.example.platform.auth.crypto.AuthKeyProvider;
import com.example.platform.auth.model.AuthSession;
import com.example.platform.auth.service.AuthService;
import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.mapper.SceneScheduleStateMapper;
import com.example.platform.scene.mapper.ScheduleEventMapper;
import com.example.platform.space.controller.SpaceController;
import com.example.platform.space.dto.SpaceSummaryResponse;
import com.example.platform.space.mapper.SpaceAccessRequestMapper;
import com.example.platform.space.mapper.SpaceMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import com.example.platform.space.service.SpaceService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SpaceController.class)
class SpaceControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private SpaceService spaceService;
    @MockitoBean private AuthService authService;
    @MockitoBean private AuthKeyProvider authKeyProvider;
    @MockitoBean private AuthProperties authProperties;
    @MockitoBean private PlatformAuditLogMapper platformAuditLogMapper;
    @MockitoBean private TestRepositoryMapper testRepositoryMapper;
    @MockitoBean private SceneMapper sceneMapper;
    @MockitoBean private SceneScheduleStateMapper sceneScheduleStateMapper;
    @MockitoBean private ScheduleEventMapper scheduleEventMapper;
    @MockitoBean private SpaceMapper spaceMapper;
    @MockitoBean private SpaceMemberMapper spaceMemberMapper;
    @MockitoBean private SpaceAccessRequestMapper spaceAccessRequestMapper;
    @MockitoBean private TaskMapper taskMapper;
    @MockitoBean private ArtifactMapper artifactMapper;
    @MockitoBean private CaseResultMapper caseResultMapper;
    @MockitoBean private TaskStageLogMapper taskStageLogMapper;

    @Test
    void shouldListMySpaces() throws Exception {
        mockAuthenticatedSession();
        Mockito.when(spaceService.listMySpaces(Mockito.any()))
                .thenReturn(List.of(new SpaceSummaryResponse(7L, "默认空间", "desc")));

        mockMvc.perform(get("/api/spaces").cookie(new jakarta.servlet.http.Cookie("platform_session", "session-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].id").value(7L))
                .andExpect(jsonPath("$.data[0].name").value("默认空间"));
    }

    @Test
    void shouldCreateSpace() throws Exception {
        mockAuthenticatedSession();
        Mockito.when(spaceService.createSpace(Mockito.any(), Mockito.any()))
                .thenReturn(new SpaceSummaryResponse(8L, "测试空间", "新的空间"));

        mockMvc.perform(post("/api/spaces")
                        .cookie(new jakarta.servlet.http.Cookie("platform_session", "session-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "测试空间",
                                  "description": "新的空间"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8L))
                .andExpect(jsonPath("$.data.name").value("测试空间"));
    }

    private void mockAuthenticatedSession() {
        Mockito.when(authProperties.getCookieName()).thenReturn("platform_session");
        Mockito.when(authService.findSession("session-1"))
                .thenReturn(java.util.Optional.of(new AuthSession(
                        "session-1",
                        1L,
                        "admin",
                        "平台管理员",
                        "avatars/admin.png",
                        null,
                        LocalDateTime.now().plusDays(14))));
    }
}
