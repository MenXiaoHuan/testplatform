package com.example.platform.scene;

import com.example.platform.common.PageResponse;
import com.example.platform.audit.mapper.PlatformAuditLogMapper;
import com.example.platform.auth.config.AuthProperties;
import com.example.platform.auth.crypto.AuthKeyProvider;
import com.example.platform.auth.mapper.PlatformUserMapper;
import com.example.platform.auth.mapper.UserSessionMapper;
import com.example.platform.auth.model.AuthSession;
import com.example.platform.auth.service.AuthService;
import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.scene.controller.ScheduleEventController;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.mapper.SceneScheduleStateMapper;
import com.example.platform.scene.mapper.ScheduleEventMapper;
import com.example.platform.space.mapper.SpaceAccessRequestMapper;
import com.example.platform.space.mapper.SpaceMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import com.example.platform.space.service.SpaceAuthorizationService;
import com.example.platform.scene.dto.ScheduleEventIssueResponse;
import com.example.platform.scene.dto.ScheduleEventRetryRequest;
import com.example.platform.scene.model.ScheduleEventEntity;
import com.example.platform.scene.service.ScheduleEventAdminService;
import com.example.platform.task.dto.TaskRunResponse;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ScheduleEventController.class)
class ScheduleEventControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleEventAdminService adminService;
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
    void shouldListIssueEvents() throws Exception {
        ScheduleEventIssueResponse event = new ScheduleEventIssueResponse(
                7L,
                11L,
                null,  // sceneName
                LocalDateTime.of(2026, 7, 2, 12, 0),
                "FAILED",
                "CRON",
                null,
                null,
                null,
                1,
                LocalDateTime.of(2026, 7, 2, 12, 45),
                LocalDateTime.of(2026, 7, 2, 12, 44),
                "cron:0 */5 * * * *",
                "system busy",
                null,  // taskId
                LocalDateTime.of(2026, 7, 2, 12, 40),
                LocalDateTime.of(2026, 7, 2, 12, 44));
        Mockito.when(adminService.listEventsV2(7L, 11L, null, null, null, 1, 20))
                .thenReturn(PageResponse.of(List.of(event), 1, 1, 20));

        mockMvc.perform(authenticated(get("/api/spaces/7/schedule-events")
                        .param("status", "FAILED", "ABANDONED")
                        .param("sceneId", "11")
                        .param("page", "1")
                        .param("limit", "20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.items[0].id").value(7L))
                .andExpect(jsonPath("$.data.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.items[0].retryCount").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void shouldRetryIssueEvent() throws Exception {
        TaskRunResponse task = new TaskRunResponse(
                101L, 11L, 21L, "QUEUED", "SCHEDULED", "cron:0 */5 * * * *", "scheduler",
                null, "main", null, null, null, null, "centralized-runner", "QUEUED", null, null,
                false, null, null, "main", "chromium", null, null, "tests", "npm run test:e2e", null);
        Mockito.when(adminService.retryEvent(Mockito.eq(7L), Mockito.eq(7L), Mockito.any(ScheduleEventRetryRequest.class))).thenReturn(task);

        mockMvc.perform(authenticated(post("/api/spaces/7/schedule-events/7/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "operatorName": "alice",
                              "operatorId": "u-1001",
                              "comment": "manual retry after fix"
                            }
                            """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(101L))
                .andExpect(jsonPath("$.data.sceneId").value(11L));
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
