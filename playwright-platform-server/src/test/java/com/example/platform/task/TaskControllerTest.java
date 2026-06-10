package com.example.platform.task;

import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.CaseResultEntity;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.service.TaskService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = com.example.platform.task.controller.TaskController.class)
class TaskControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService taskService;

    @Test
    void shouldExposeTaskDetailReportAndArtifacts() throws Exception {
        TaskEntity task = new TaskEntity();
        task.setId(1L);
        task.setStatus("SUCCESS");
        task.setReportUrl("http://localhost:9000/report/1/index.html");

        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setId(10L);
        artifact.setTaskId(1L);
        artifact.setArtifactType("REPORT_FILE");
        artifact.setBucket("qa-report");
        artifact.setObjectKey("runs/1/artifacts/index.html");
        artifact.setUrl("http://localhost:9000/qa-report/runs/1/artifacts/index.html?X-Amz-Signature=demo");

        Mockito.when(taskService.get(1L)).thenReturn(task);
        Mockito.when(taskService.getReportUrl(1L)).thenReturn("http://localhost:9000/report/1/index.html");
        Mockito.when(taskService.listArtifacts(1L)).thenReturn(List.of(artifact));

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.artifactCount").value(1))
                .andExpect(jsonPath("$.hasArtifacts").value(true))
                .andExpect(jsonPath("$.reportReady").value(true))
                .andExpect(jsonPath("$.reportUrl").value("http://localhost:9000/report/1/index.html"));

        mockMvc.perform(get("/api/tasks/1/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(1))
                .andExpect(jsonPath("$.reportUrl").value("http://localhost:9000/report/1/index.html"));

        mockMvc.perform(get("/api/tasks/1/artifacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].artifactType").value("REPORT_FILE"))
                .andExpect(jsonPath("$[0].taskId").value(1))
                .andExpect(jsonPath("$[0].objectKey").value("runs/1/artifacts/index.html"))
                .andExpect(jsonPath("$[0].url").value("http://localhost:9000/qa-report/runs/1/artifacts/index.html?X-Amz-Signature=demo"));
    }

    @Test
    void shouldListTaskCaseResults() throws Exception {
        CaseResultEntity caseResult = new CaseResultEntity();
        caseResult.setId(1L);
        caseResult.setTaskId(101L);
        caseResult.setHistoryId("chromium::checkout::should pay successfully");
        caseResult.setFullName("checkout :: should pay successfully");
        caseResult.setSuiteName("checkout");
        caseResult.setStoryName("should pay successfully");
        caseResult.setStatus("PASSED");
        caseResult.setDurationMs(321L);
        caseResult.setProjectName("chromium");

        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setId(11L);
        artifact.setCaseResultId(1L);

        Mockito.when(taskService.listCaseResults(101L)).thenReturn(List.of(caseResult));
        Mockito.when(taskService.listArtifactsByCaseResult(1L)).thenReturn(List.of(artifact));

        mockMvc.perform(get("/api/tasks/101/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].fullName").value("checkout :: should pay successfully"))
                .andExpect(jsonPath("$[0].status").value("PASSED"))
                .andExpect(jsonPath("$[0].artifactCount").value(1));
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
                .andExpect(jsonPath("$[0].artifactType").value("TRACE"))
                .andExpect(jsonPath("$[0].caseResultId").value(1))
                .andExpect(jsonPath("$[0].url").value("http://minio/presigned/trace"));
    }
}
