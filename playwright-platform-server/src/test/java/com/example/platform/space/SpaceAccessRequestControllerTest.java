package com.example.platform.space;

import com.example.platform.audit.mapper.PlatformAuditLogMapper;
import com.example.platform.auth.config.AuthProperties;
import com.example.platform.auth.crypto.AuthKeyProvider;
import com.example.platform.auth.mapper.PlatformUserMapper;
import com.example.platform.auth.mapper.UserSessionMapper;
import com.example.platform.auth.model.AuthSession;
import com.example.platform.auth.service.AuthService;
import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.mapper.SceneScheduleStateMapper;
import com.example.platform.scene.mapper.ScheduleEventMapper;
import com.example.platform.space.controller.SpaceAccessRequestController;
import com.example.platform.space.dto.SpaceAccessRequestResponse;
import com.example.platform.space.mapper.SpaceAccessRequestMapper;
import com.example.platform.space.mapper.SpaceMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import com.example.platform.space.service.SpaceAccessRequestService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SpaceAccessRequestController.class)
class SpaceAccessRequestControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private SpaceAccessRequestService spaceAccessRequestService;
    @MockitoBean private AuthService authService;
    @MockitoBean private AuthKeyProvider authKeyProvider;
    @MockitoBean private AuthProperties authProperties;
    @MockitoBean private PlatformUserMapper platformUserMapper;
    @MockitoBean private UserSessionMapper userSessionMapper;
    @MockitoBean private PlatformAuditLogMapper platformAuditLogMapper;
    @MockitoBean private TestRepositoryMapper testRepositoryMapper;
    @MockitoBean private SceneMapper sceneMapper;
    @MockitoBean private SceneScheduleStateMapper sceneScheduleStateMapper;
    @MockitoBean private ScheduleEventMapper scheduleEventMapper;
    @MockitoBean private SpaceMapper spaceMapper;
    @MockitoBean private SpaceMemberMapper spaceMemberMapper;
    @MockitoBean private SpaceAccessRequestMapper spaceAccessRequestMapper;
    @MockitoBean private SpaceAuthorizationService spaceAuthorizationService;
    @MockitoBean private TaskMapper taskMapper;
    @MockitoBean private ArtifactMapper artifactMapper;
    @MockitoBean private CaseResultMapper caseResultMapper;
    @MockitoBean private TaskStageLogMapper taskStageLogMapper;

    @Test
    void shouldSubmitAndListRequests() throws Exception {
        mockAuthenticatedSession();
        SpaceAccessRequestResponse request = new SpaceAccessRequestResponse(
                21L,
                7L,
                2L,
                "tester",
                "测试用户",
                "http://localhost:10000/qa-report/avatars/tester.png?X-Amz-Signature=demo",
                "OPERATOR",
                "需要处理调度异常",
                "PENDING",
                null,
                null,
                null,
                null,
                null);
        Mockito.when(spaceAccessRequestService.listBySpace(Mockito.eq(7L), Mockito.any()))
                .thenReturn(List.of(request));

        mockMvc.perform(post("/api/spaces/7/access-requests")
                        .cookie(new jakarta.servlet.http.Cookie("platform_session", "session-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedRole": "OPERATOR",
                                  "reason": "需要处理调度异常"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(get("/api/spaces/7/access-requests")
                        .cookie(new jakarta.servlet.http.Cookie("platform_session", "session-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(21L))
                .andExpect(jsonPath("$.data[0].applicantUsername").value("tester"))
                .andExpect(jsonPath("$.data[0].applicantNickname").value("测试用户"))
                .andExpect(jsonPath("$.data[0].applicantAvatarUrl").value("http://localhost:10000/qa-report/avatars/tester.png?X-Amz-Signature=demo"))
                .andExpect(jsonPath("$.data[0].requestedRole").value("OPERATOR"));
    }

    @Test
    void shouldApproveAndRejectRequest() throws Exception {
        mockAuthenticatedSession();

        mockMvc.perform(post("/api/spaces/7/access-requests/21/approve")
                        .cookie(new jakarta.servlet.http.Cookie("platform_session", "session-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewComment": "同意"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(post("/api/spaces/7/access-requests/21/reject")
                        .cookie(new jakarta.servlet.http.Cookie("platform_session", "session-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewComment": "当前阶段不需要"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
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
                        7L,
                        LocalDateTime.now().plusDays(14),
                        false)));
    }
}
