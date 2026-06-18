package com.example.platform.task;

import com.example.platform.common.PageResponse;
import com.example.platform.audit.mapper.PlatformAuditLogMapper;
import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.mapper.SceneScheduleStateMapper;
import com.example.platform.task.dto.CaseResultResponse;
import com.example.platform.task.dto.SceneTaskListResponse;
import com.example.platform.task.dto.TaskDetailResponse;
import com.example.platform.task.dto.TaskStageLogResponse;
import com.example.platform.task.mapper.ArtifactMapper;
import com.example.platform.task.mapper.CaseResultMapper;
import com.example.platform.task.mapper.TaskMapper;
import com.example.platform.task.mapper.TaskStageLogMapper;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.service.TaskService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = com.example.platform.task.controller.TaskController.class)
class TaskControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService taskService;

    @MockBean
    private PlatformAuditLogMapper platformAuditLogMapper;

    @MockBean
    private TestRepositoryMapper testRepositoryMapper;

    @MockBean
    private SceneMapper sceneMapper;

    @MockBean
    private SceneScheduleStateMapper sceneScheduleStateMapper;

    @MockBean
    private TaskMapper taskMapper;

    @MockBean
    private ArtifactMapper artifactMapper;

    @MockBean
    private CaseResultMapper caseResultMapper;

    @MockBean
    private TaskStageLogMapper taskStageLogMapper;

    @Test
    void shouldExposeTaskDetailAndArtifacts() throws Exception {
        TaskDetailResponse detail = new TaskDetailResponse(
                1L,
                11L,
                7L,
                "登录场景",
                "智能面试平台端到端测试",
                "SUCCESS",
                true,
                "MANUAL",
                "demo",
                "manual-run",
                "main",
                null,
                "FINISHED",
                "SUCCESS",
                false,
                null,
                null,
                null,
                1234L,
                "centralized-runner",
                "http://localhost:9000/logs/1.txt",
                "main",
                "chromium",
                "{\"BASE_URL\":\"https://example.com\"}",
                "login.spec.ts",
                "tests",
                "node ./scripts/run-e2e.cjs --project chromium --target tests/login.spec.ts",
                1,
                1,
                true);

        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setId(10L);
        artifact.setTaskId(1L);
        artifact.setArtifactType("TRACE");
        artifact.setBucket("qa-report");
        artifact.setObjectKey("runs/1/artifacts/trace.zip");
        artifact.setUrl("http://localhost:9000/qa-report/runs/1/artifacts/trace.zip?X-Amz-Signature=demo");

        Mockito.when(taskService.getDetail(1L)).thenReturn(detail);
        Mockito.when(taskService.listArtifacts(1L)).thenReturn(List.of(artifact));

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.currentStage").value("FINISHED"))
                .andExpect(jsonPath("$.data.resultCode").value("SUCCESS"))
                .andExpect(jsonPath("$.data.triggerReason").value("manual-run"))
                .andExpect(jsonPath("$.data.cancelRequestedBy").value(nullValue()))
                .andExpect(jsonPath("$.data.artifactCount").value(1))
                .andExpect(jsonPath("$.data.hasArtifacts").value(true))
                .andExpect(jsonPath("$.data.detailAvailable").value(true))
                .andExpect(jsonPath("$.data.sceneName").value("登录场景"))
                .andExpect(jsonPath("$.data.repositoryName").value("智能面试平台端到端测试"))
                .andExpect(jsonPath("$.data.environmentVariableCount").value(1))
                .andExpect(jsonPath("$.data.resolvedBrowser").value("chromium"))
                .andExpect(jsonPath("$.data.resolvedMatchValue").value("login.spec.ts"))
                .andExpect(jsonPath("$.data.resolvedRunCommand").value("node ./scripts/run-e2e.cjs --project chromium --target tests/login.spec.ts"))
                .andExpect(jsonPath("$.msg").value("success"));

        mockMvc.perform(get("/api/tasks/1/artifacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].artifactType").value("TRACE"))
                .andExpect(jsonPath("$.data[0].taskId").value(1))
                .andExpect(jsonPath("$.data[0].objectKey").value("runs/1/artifacts/trace.zip"))
                .andExpect(jsonPath("$.data[0].url").value("http://localhost:9000/qa-report/runs/1/artifacts/trace.zip?X-Amz-Signature=demo"))
                .andExpect(jsonPath("$.msg").value("success"));
    }

    @Test
    void shouldExposeCancelAndStageLogsEndpoints() throws Exception {
        TaskStageLogResponse stageLog = new TaskStageLogResponse(
                1L,
                "TESTING",
                "COMBINED",
                "line1",
                2,
                "http://minio/presigned/testing-log");

        Mockito.when(taskService.listStageLogs(101L)).thenReturn(List.of(stageLog));

        mockMvc.perform(post("/api/tasks/101/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.msg").value("success"));

        Mockito.verify(taskService).cancelTask(101L, "system-user");

        mockMvc.perform(get("/api/tasks/101/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].stage").value("TESTING"))
                .andExpect(jsonPath("$.data[0].downloadUrl").value("http://minio/presigned/testing-log"))
                .andExpect(jsonPath("$.msg").value("success"));
    }

    @Test
    void shouldListTaskCaseResults() throws Exception {
        CaseResultResponse caseResult = new CaseResultResponse(
                1L,
                101L,
                "chromium::checkout::should pay successfully",
                "checkout :: should pay successfully",
                "checkout",
                "should pay successfully",
                "PASSED",
                321L,
                "chromium",
                null,
                "http://minio/presigned/video",
                "http://minio/presigned/trace",
                List.of("http://minio/presigned/screenshot"),
                "http://minio/presigned/log",
                1,
                List.of());

        Mockito.when(taskService.listCaseResultResponses(101L)).thenReturn(List.of(caseResult));

        mockMvc.perform(get("/api/tasks/101/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].fullName").value("checkout :: should pay successfully"))
                .andExpect(jsonPath("$.data[0].status").value("PASSED"))
                .andExpect(jsonPath("$.data[0].artifactCount").value(1))
                .andExpect(jsonPath("$.data[0].videoUrl").value("http://minio/presigned/video"))
                .andExpect(jsonPath("$.data[0].traceUrl").value("http://minio/presigned/trace"))
                .andExpect(jsonPath("$.data[0].screenshotUrls[0]").value("http://minio/presigned/screenshot"))
                .andExpect(jsonPath("$.data[0].logUrl").value("http://minio/presigned/log"))
                .andExpect(jsonPath("$.msg").value("success"));
    }

    @Test
    void shouldListArtifactsByCaseResult() throws Exception {
        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setId(11L);
        artifact.setTaskId(101L);
        artifact.setCaseResultId(1L);
        artifact.setArtifactType("TRACE");
        artifact.setObjectKey("runs/101/artifacts/1/.playwright-artifacts/checkout/trace.zip");
        artifact.setUrl("http://minio/presigned/trace");

        Mockito.when(taskService.listArtifactsByCaseResult(1L)).thenReturn(List.of(artifact));

        mockMvc.perform(get("/api/tasks/101/cases/1/artifacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].artifactType").value("TRACE"))
                .andExpect(jsonPath("$.data[0].caseResultId").value(1))
                .andExpect(jsonPath("$.data[0].url").value("http://minio/presigned/trace"))
                .andExpect(jsonPath("$.msg").value("success"));
    }

    @Test
    void shouldListSceneTasksNewestFirst() throws Exception {
        SceneTaskListResponse newestTask = new SceneTaskListResponse(
                101L,
                11L,
                "SUCCESS",
                true,
                "MANUAL",
                "FINISHED",
                "SUCCESS",
                false,
                "main",
                LocalDateTime.of(2026, 6, 10, 10, 4),
                LocalDateTime.of(2026, 6, 10, 10, 4, 5),
                1520L,
                LocalDateTime.of(2026, 6, 10, 10, 5),
                "centralized-runner",
                2,
                1,
                0);

        Mockito.when(taskService.listByScene(11L, 1, 10))
                .thenReturn(new PageResponse<>(List.of(newestTask), 1, 1, 10, 1, false, false));

        mockMvc.perform(get("/api/scenes/11/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.items[0].sceneId").value(11))
                .andExpect(jsonPath("$.data.items[0].id").value(101))
                .andExpect(jsonPath("$.data.items[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].detailAvailable").value(true))
                .andExpect(jsonPath("$.data.items[0].passedCount").value(2))
                .andExpect(jsonPath("$.data.items[0].failedCount").value(1))
                .andExpect(jsonPath("$.data.items[0].skippedCount").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.msg").value("success"));
    }

    @Test
    void shouldListGlobalTasks() throws Exception {
        SceneTaskListResponse task = new SceneTaskListResponse(
                101L,
                11L,
                "FAILED",
                true,
                "MANUAL",
                "FINISHED",
                "TEST_FAILED",
                false,
                "main",
                null,
                null,
                1520L,
                null,
                "centralized-runner",
                2,
                1,
                0);

        Mockito.when(taskService.list(1, 10))
                .thenReturn(new PageResponse<>(List.of(task), 1, 1, 10, 1, false, false));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.items[0].id").value(101))
                .andExpect(jsonPath("$.data.items[0].detailAvailable").value(true))
                .andExpect(jsonPath("$.data.items[0].currentStage").value("FINISHED"))
                .andExpect(jsonPath("$.data.items[0].resultCode").value("TEST_FAILED"))
                .andExpect(jsonPath("$.data.items[0].passedCount").value(2))
                .andExpect(jsonPath("$.data.items[0].failedCount").value(1))
                .andExpect(jsonPath("$.data.items[0].skippedCount").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.msg").value("success"));
    }

    @Test
    void shouldCreateQueuedTaskImmediatelyWhenRunScene() throws Exception {
        TaskEntity createdTask = new TaskEntity();
        createdTask.setId(301L);
        createdTask.setSceneId(11L);
        createdTask.setRepoId(7L);
        createdTask.setStatus("QUEUED");
        createdTask.setCurrentStage("QUEUED");
        createdTask.setTriggerType("MANUAL");
        createdTask.setBranch("main");
        createdTask.setRunnerName("centralized-runner");

        Mockito.when(taskService.createAndStart(11L)).thenReturn(createdTask);

        mockMvc.perform(post("/api/scenes/11/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(301))
                .andExpect(jsonPath("$.data.sceneId").value(11))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.currentStage").value("QUEUED"))
                .andExpect(jsonPath("$.data.runnerName").value("centralized-runner"))
                .andExpect(jsonPath("$.msg").value("success"));
    }

    @Test
    void shouldReturnConflictMessageWhenSceneAlreadyHasActiveTask() throws Exception {
        Mockito.when(taskService.createAndStart(11L))
                .thenThrow(new IllegalStateException("当前场景已有执行中的任务，请稍后再试"));

        mockMvc.perform(post("/api/scenes/11/run"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.msg").value("当前场景已有执行中的任务，请稍后再试"));
    }

    @Test
    void shouldReturnUnifiedErrorResponseForResponseStatusException() throws Exception {
        Mockito.when(taskService.listByScene(99L, 1, 10))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Scene not found: 99"));

        mockMvc.perform(get("/api/scenes/99/tasks"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.msg").value("Scene not found: 99"));
    }
}
