package com.example.platform.auth;

import com.example.platform.auth.config.AuthProperties;
import com.example.platform.auth.controller.AuthController;
import com.example.platform.auth.crypto.AuthKeyProvider;
import com.example.platform.auth.service.AuthService;
import com.example.platform.audit.mapper.PlatformAuditLogMapper;
import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.mapper.SceneScheduleStateMapper;
import com.example.platform.scene.mapper.ScheduleEventMapper;
import com.example.platform.space.mapper.SpaceAccessRequestMapper;
import com.example.platform.space.mapper.SpaceMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import com.example.platform.task.mapper.ArtifactMapper;
import com.example.platform.task.mapper.CaseResultMapper;
import com.example.platform.task.mapper.TaskMapper;
import com.example.platform.task.mapper.TaskStageLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AuthKeyProvider authKeyProvider;

    @MockitoBean
    private AuthProperties authProperties;

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
    void shouldReturnPublicKey() throws Exception {
        Mockito.when(authProperties.getCookieName()).thenReturn("platform_session");
        Mockito.when(authKeyProvider.publicKeyPem()).thenReturn("-----BEGIN PUBLIC KEY-----demo-----END PUBLIC KEY-----");

        mockMvc.perform(get("/api/auth/public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.algorithm").value("RSA"))
                .andExpect(jsonPath("$.data.publicKeyPem").value("-----BEGIN PUBLIC KEY-----demo-----END PUBLIC KEY-----"));
    }

    @Test
    void shouldLoginAndSetCookie() throws Exception {
        Mockito.when(authProperties.getCookieName()).thenReturn("platform_session");
        Mockito.when(authProperties.getSlidingDays()).thenReturn(14);
        Mockito.when(authService.login("admin", "ciphertext"))
                .thenReturn(new AuthService.LoginResult("session-1", 1L, "admin", "平台管理员", "avatars/admin.png", 8L));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "admin",
                                  "encryptedPassword": "ciphertext"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("platform_session"))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.nickname").value("平台管理员"))
                .andExpect(jsonPath("$.data.lastSpaceId").value(8L));
    }

    @Test
    void shouldReturnCurrentUserFromSessionCookie() throws Exception {
        Mockito.when(authProperties.getCookieName()).thenReturn("platform_session");
        Mockito.when(authService.currentUser("session-1"))
                .thenReturn(java.util.Optional.of(new AuthService.LoginUser(1L, "admin", "平台管理员", "avatars/admin.png", 8L)));

        mockMvc.perform(get("/api/auth/me").cookie(new jakarta.servlet.http.Cookie("platform_session", "session-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.nickname").value("平台管理员"))
                .andExpect(jsonPath("$.data.avatarObjectKey").value("avatars/admin.png"));
    }
}
