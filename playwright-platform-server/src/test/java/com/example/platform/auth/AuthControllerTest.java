package com.example.platform.auth;

import com.example.platform.auth.config.AuthProperties;
import com.example.platform.auth.controller.AuthController;
import com.example.platform.auth.crypto.AuthKeyProvider;
import com.example.platform.auth.mapper.PlatformUserMapper;
import com.example.platform.auth.mapper.UserSessionMapper;
import com.example.platform.auth.service.AuthService;
import com.example.platform.audit.mapper.PlatformAuditLogMapper;
import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.mapper.SceneScheduleStateMapper;
import com.example.platform.scene.mapper.ScheduleEventMapper;
import com.example.platform.space.mapper.SpaceAccessRequestMapper;
import com.example.platform.space.mapper.SpaceMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.mapper.ArtifactMapper;
import com.example.platform.task.mapper.CaseResultMapper;
import com.example.platform.task.mapper.TaskMapper;
import com.example.platform.task.mapper.TaskStageLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @MockitoBean
    private ObjectStorageService objectStorageService;
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
        Mockito.when(objectStorageService.createPresignedGetUrl(Mockito.anyString(), Mockito.eq("avatars/admin.png")))
                .thenReturn("http://localhost:10000/qa-report/avatars/admin.png?X-Amz-Signature=demo");
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
                .andExpect(jsonPath("$.data.avatarObjectKey").value("http://localhost:10000/qa-report/avatars/admin.png?X-Amz-Signature=demo"))
                .andExpect(jsonPath("$.data.lastSpaceId").value(8L));
    }

    @Test
    void shouldRegisterAndSetCookie() throws Exception {
        Mockito.when(authProperties.getCookieName()).thenReturn("platform_session");
        Mockito.when(authProperties.getSlidingDays()).thenReturn(14);
        Mockito.when(authService.register("zhangsan", "张三", "ciphertext"))
                .thenReturn(new AuthService.LoginResult("session-2", 2L, "zhangsan", "张三", null, 12L));

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "zhangsan",
                                  "nickname": "张三",
                                  "encryptedPassword": "ciphertext"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("platform_session"))
                .andExpect(jsonPath("$.data.username").value("zhangsan"))
                .andExpect(jsonPath("$.data.nickname").value("张三"))
                .andExpect(jsonPath("$.data.avatarObjectKey").isEmpty())
                .andExpect(jsonPath("$.data.lastSpaceId").value(12L));
    }

    @Test
    void shouldReturnCurrentUserFromSessionCookie() throws Exception {
        Mockito.when(authProperties.getCookieName()).thenReturn("platform_session");
        Mockito.when(objectStorageService.createPresignedGetUrl(Mockito.anyString(), Mockito.eq("avatars/admin.png")))
                .thenReturn("http://localhost:10000/qa-report/avatars/admin.png?X-Amz-Signature=demo");
        Mockito.when(authService.currentUser("session-1"))
                .thenReturn(java.util.Optional.of(new AuthService.LoginUser(1L, "admin", "平台管理员", "avatars/admin.png", 8L)));

        mockMvc.perform(get("/api/auth/me").cookie(new jakarta.servlet.http.Cookie("platform_session", "session-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.nickname").value("平台管理员"))
                .andExpect(jsonPath("$.data.avatarObjectKey").value("http://localhost:10000/qa-report/avatars/admin.png?X-Amz-Signature=demo"));
    }

    @Test
    void shouldUpdateNicknameFromSessionCookie() throws Exception {
        Mockito.when(authProperties.getCookieName()).thenReturn("platform_session");
        Mockito.when(authService.updateNickname("session-1", "新昵称"))
                .thenReturn(java.util.Optional.of(new AuthService.LoginUser(1L, "admin", "新昵称", "avatars/admin.png", 8L)));
        Mockito.when(objectStorageService.createPresignedGetUrl(Mockito.anyString(), Mockito.eq("avatars/admin.png")))
                .thenReturn("http://localhost:10000/qa-report/avatars/admin.png?X-Amz-Signature=demo");

        mockMvc.perform(put("/api/auth/profile")
                        .cookie(new jakarta.servlet.http.Cookie("platform_session", "session-1"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "nickname": "新昵称"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.avatarObjectKey").value("http://localhost:10000/qa-report/avatars/admin.png?X-Amz-Signature=demo"));
    }

    @Test
    void shouldUploadAvatarThroughBackendAndReturnProfile() throws Exception {
        Mockito.when(authProperties.getCookieName()).thenReturn("platform_session");
        Mockito.when(authService.currentUser("session-1"))
                .thenReturn(java.util.Optional.of(new AuthService.LoginUser(1L, "admin", "平台管理员", "avatars/admin.png", 8L)));
        Mockito.when(objectStorageService.uploadFile(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn("http://localhost:10000/qa-report/avatars/users/1/avatar.png");
        Mockito.when(objectStorageService.createPresignedGetUrl(Mockito.anyString(), Mockito.contains("avatars/users/1/")))
                .thenReturn("http://localhost:10000/qa-report/avatars/users/1/avatar.png?X-Amz-Signature=demo");
        Mockito.when(authService.updateAvatar(Mockito.eq("session-1"), Mockito.contains("avatars/users/1/")))
                .thenReturn(java.util.Optional.of(new AuthService.LoginUser(
                        1L,
                        "admin",
                        "平台管理员",
                        "avatars/users/1/avatar.png",
                        8L)));

        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "avatar".getBytes());

        mockMvc.perform(multipart("/api/auth/avatar")
                        .file(file)
                        .cookie(new jakarta.servlet.http.Cookie("platform_session", "session-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.avatarObjectKey").value("http://localhost:10000/qa-report/avatars/users/1/avatar.png?X-Amz-Signature=demo"));
    }
}
