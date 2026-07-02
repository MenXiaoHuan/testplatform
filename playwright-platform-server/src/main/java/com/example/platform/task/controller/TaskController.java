package com.example.platform.task.controller;

import com.example.platform.common.ApiResponse;
import com.example.platform.common.PageResponse;
import com.example.platform.task.dto.CaseResultResponse;
import com.example.platform.task.dto.SceneTaskListResponse;
import com.example.platform.task.dto.TaskDetailResponse;
import com.example.platform.task.dto.TaskDiagnosticsResponse;
import com.example.platform.task.dto.TaskRunResponse;
import com.example.platform.task.dto.TaskStageLogResponse;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.service.TaskService;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Exposes task execution, cancellation, detail, artifact, case, and log APIs.
 *
 * <p>The controller stays thin: write transactions, cache reads, and long-running
 * task dispatch are delegated to {@link TaskService}.
 */
@RestController
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/api/spaces/{spaceId}/scenes/{sceneId}/run")
    public ApiResponse<TaskRunResponse> runScene(@PathVariable Long spaceId, @PathVariable Long sceneId) {
        return ApiResponse.ok(TaskRunResponse.from(taskService.createAndStart(sceneId)));
    }

    @PostMapping("/api/spaces/{spaceId}/tasks/{taskId}/cancel")
    public ApiResponse<Void> cancelTask(@PathVariable Long spaceId, @PathVariable Long taskId) {
        taskService.cancelTask(taskId, "system-user");
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/spaces/{spaceId}/tasks")
    public ApiResponse<PageResponse<SceneTaskListResponse>> listTasks(
            @PathVariable Long spaceId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(taskService.list(page, size));
    }

    @GetMapping("/api/spaces/{spaceId}/scenes/{sceneId}/tasks")
    public ApiResponse<PageResponse<SceneTaskListResponse>> listSceneTasks(
            @PathVariable Long spaceId,
            @PathVariable Long sceneId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(taskService.listByScene(sceneId, page, size));
    }

    @GetMapping("/api/spaces/{spaceId}/tasks/{taskId}")
    public ApiResponse<TaskDetailResponse> getTask(@PathVariable Long spaceId, @PathVariable Long taskId) {
        return ApiResponse.ok(taskService.getDetail(taskId));
    }

    @GetMapping("/api/spaces/{spaceId}/tasks/{taskId}/diagnostics")
    public ApiResponse<TaskDiagnosticsResponse> getTaskDiagnostics(@PathVariable Long spaceId, @PathVariable Long taskId) {
        return ApiResponse.ok(taskService.getDiagnostics(taskId));
    }

    @GetMapping("/api/spaces/{spaceId}/tasks/{taskId}/artifacts")
    public ApiResponse<List<ArtifactEntity>> listTaskArtifacts(@PathVariable Long spaceId, @PathVariable Long taskId) {
        return ApiResponse.ok(taskService.listArtifacts(taskId));
    }

    @GetMapping("/api/spaces/{spaceId}/tasks/{taskId}/cases")
    public ApiResponse<List<CaseResultResponse>> listTaskCases(@PathVariable Long spaceId, @PathVariable Long taskId) {
        return ApiResponse.ok(taskService.listCaseResultResponses(taskId));
    }

    @GetMapping("/api/spaces/{spaceId}/tasks/{taskId}/cases/{caseResultId}/artifacts")
    public ApiResponse<List<ArtifactEntity>> listCaseArtifacts(@PathVariable Long spaceId, @PathVariable Long taskId, @PathVariable Long caseResultId) {
        return ApiResponse.ok(taskService.listArtifactsByCaseResult(caseResultId));
    }

    @CrossOrigin(origins = "https://trace.playwright.dev")
    @GetMapping("/api/spaces/{spaceId}/tasks/{taskId}/artifacts/{artifactId}/download")
    public ResponseEntity<Resource> downloadArtifact(@PathVariable Long spaceId, @PathVariable Long taskId, @PathVariable Long artifactId) {
        return taskService.downloadArtifact(taskId, artifactId);
    }

    @GetMapping("/api/spaces/{spaceId}/tasks/{taskId}/logs")
    public ApiResponse<List<TaskStageLogResponse>> listTaskLogs(@PathVariable Long spaceId, @PathVariable Long taskId) {
        return ApiResponse.ok(taskService.listStageLogs(taskId));
    }

    @CrossOrigin(origins = "https://trace.playwright.dev")
    @GetMapping("/api/spaces/{spaceId}/tasks/{taskId}/logs/{logId}/download")
    public ResponseEntity<Resource> downloadTaskLog(@PathVariable Long spaceId, @PathVariable Long taskId, @PathVariable Long logId) {
        return taskService.downloadStageLog(taskId, logId);
    }
}
