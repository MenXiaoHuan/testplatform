package com.example.platform.task.controller;

import com.example.platform.common.ApiResponse;
import com.example.platform.common.PageResponse;
import com.example.platform.task.dto.CaseResultResponse;
import com.example.platform.task.dto.SceneTaskListResponse;
import com.example.platform.task.dto.TaskDetailResponse;
import com.example.platform.task.dto.TaskReportSummaryResponse;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.service.TaskService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/api/scenes/{sceneId}/run")
    public ApiResponse<TaskEntity> runScene(@PathVariable Long sceneId) {
        return ApiResponse.ok(taskService.createAndStart(sceneId));
    }

    @GetMapping("/api/tasks")
    public ApiResponse<PageResponse<SceneTaskListResponse>> listTasks(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(taskService.list(page, size));
    }

    @GetMapping("/api/scenes/{sceneId}/tasks")
    public ApiResponse<PageResponse<SceneTaskListResponse>> listSceneTasks(
            @PathVariable Long sceneId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(taskService.listByScene(sceneId, page, size));
    }

    @GetMapping("/api/tasks/{taskId}")
    public ApiResponse<TaskDetailResponse> getTask(@PathVariable Long taskId) {
        return ApiResponse.ok(taskService.getDetail(taskId));
    }

    @GetMapping("/api/tasks/{taskId}/report")
    public ApiResponse<Map<String, Object>> getTaskReport(@PathVariable Long taskId) {
        return ApiResponse.ok(Map.of(
                "taskId", taskId,
                "reportUrl", taskService.getReportUrl(taskId)));
    }

    @GetMapping("/api/tasks/{taskId}/report-summary")
    public ApiResponse<TaskReportSummaryResponse> getTaskReportSummary(@PathVariable Long taskId) {
        return ApiResponse.ok(taskService.getReportSummary(taskId));
    }

    @GetMapping("/api/tasks/{taskId}/artifacts")
    public ApiResponse<List<ArtifactEntity>> listTaskArtifacts(@PathVariable Long taskId) {
        return ApiResponse.ok(taskService.listArtifacts(taskId));
    }

    @GetMapping("/api/tasks/{taskId}/cases")
    public ApiResponse<List<CaseResultResponse>> listTaskCases(@PathVariable Long taskId) {
        return ApiResponse.ok(taskService.listCaseResultResponses(taskId));
    }

    @GetMapping("/api/tasks/{taskId}/cases/{caseResultId}/artifacts")
    public ApiResponse<List<ArtifactEntity>> listCaseArtifacts(@PathVariable Long taskId, @PathVariable Long caseResultId) {
        return ApiResponse.ok(taskService.listArtifactsByCaseResult(caseResultId));
    }
}
