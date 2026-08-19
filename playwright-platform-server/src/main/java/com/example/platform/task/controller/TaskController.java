package com.example.platform.task.controller;

import com.example.platform.common.ApiResponse;
import com.example.platform.common.PageResponse;
import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.space.service.SpaceAuthorizationService;
import com.example.platform.task.dto.CaseResultResponse;
import com.example.platform.task.dto.SceneTaskListResponse;
import com.example.platform.task.dto.TaskDetailResponse;
import com.example.platform.task.dto.TaskDiagnosticsResponse;
import com.example.platform.task.dto.TaskTraceShareResponse;
import com.example.platform.task.dto.TaskRunResponse;
import com.example.platform.task.dto.TaskStageLogResponse;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.service.TaskService;
import com.example.platform.task.service.TaskTraceShareService;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 任务控制器 —— 暴露任务执行、取消、详情、制品、用例、日志等 API。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #runScene()} —— 启动场景任务执行</li>
 *   <li>{@link #cancelTask()} —— 取消正在执行的任务</li>
 *   <li>{@link #listTasks()} —— 分页查询任务列表</li>
 *   <li>{@link #getTask()} —— 获取任务详情</li>
 *   <li>{@link #listTaskArtifacts()} —— 列出任务制品</li>
 *   <li>{@link #listTaskCases()} —— 列出任务用例结果</li>
 *   <li>{@link #downloadArtifact()} —— 下载制品文件</li>
 *   <li>{@link #downloadTaskLog()} —— 下载阶段日志</li>
 *   <li>{@link #createTraceShare()} —— 创建追踪分享链接</li>
 *   <li>{@link #downloadSharedTrace()} —— 通过分享令牌下载追踪</li>
 * </ul>
 *
 * <p>依赖：{@link TaskService}、{@link TaskTraceShareService}、{@link SpaceAuthorizationService}
 *
 * <p>说明：控制器保持轻量，写事务、缓存读取和长任务派发委托给 {@link TaskService}。
 */
@RestController
public class TaskController {
    private final TaskService taskService;
    private final TaskTraceShareService taskTraceShareService;
    private final SpaceAuthorizationService spaceAuthorizationService;

    public TaskController(
            TaskService taskService,
            TaskTraceShareService taskTraceShareService,
            SpaceAuthorizationService spaceAuthorizationService) {
        this.taskService = taskService;
        this.taskTraceShareService = taskTraceShareService;
        this.spaceAuthorizationService = spaceAuthorizationService;
    }

    @PostMapping("/api/spaces/{spaceId}/scenes/{sceneId}/run")
    public ApiResponse<TaskRunResponse> runScene(@PathVariable Long spaceId, @PathVariable Long sceneId) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(TaskRunResponse.from(taskService.createAndStart(spaceId, sceneId)));
    }

    @PostMapping("/api/spaces/{spaceId}/tasks/{taskId}/cancel")
    public ApiResponse<Void> cancelTask(@PathVariable Long spaceId, @PathVariable Long taskId) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        taskService.cancelTask(spaceId, taskId, "system-user");
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/spaces/{spaceId}/tasks")
    public ApiResponse<PageResponse<SceneTaskListResponse>> listTasks(
            @PathVariable Long spaceId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(taskService.list(spaceId, page, size));
    }

    @GetMapping("/api/spaces/{spaceId}/scenes/{sceneId}/tasks")
    public ApiResponse<PageResponse<SceneTaskListResponse>> listSceneTasks(
            @PathVariable Long spaceId,
            @PathVariable Long sceneId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(taskService.listByScene(spaceId, sceneId, page, size));
    }

    @GetMapping("/api/spaces/{spaceId}/tasks/{taskId}")
    public ApiResponse<TaskDetailResponse> getTask(@PathVariable Long spaceId, @PathVariable Long taskId) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(taskService.getDetail(spaceId, taskId));
    }

    @GetMapping("/api/spaces/{spaceId}/tasks/{taskId}/diagnostics")
    public ApiResponse<TaskDiagnosticsResponse> getTaskDiagnostics(@PathVariable Long spaceId, @PathVariable Long taskId) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(taskService.getDiagnostics(spaceId, taskId));
    }

    @GetMapping("/api/spaces/{spaceId}/tasks/{taskId}/artifacts")
    public ApiResponse<List<ArtifactEntity>> listTaskArtifacts(@PathVariable Long spaceId, @PathVariable Long taskId) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(taskService.listArtifacts(spaceId, taskId));
    }

    @GetMapping("/api/spaces/{spaceId}/tasks/{taskId}/cases")
    public ApiResponse<List<CaseResultResponse>> listTaskCases(@PathVariable Long spaceId, @PathVariable Long taskId) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(taskService.listCaseResultResponses(spaceId, taskId));
    }

    @GetMapping("/api/spaces/{spaceId}/tasks/{taskId}/cases/{caseResultId}/artifacts")
    public ApiResponse<List<ArtifactEntity>> listCaseArtifacts(@PathVariable Long spaceId, @PathVariable Long taskId, @PathVariable Long caseResultId) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(taskService.listArtifactsByCaseResult(spaceId, taskId, caseResultId));
    }

    @CrossOrigin(origins = "https://trace.playwright.dev")
    @GetMapping("/api/spaces/{spaceId}/tasks/{taskId}/artifacts/{artifactId}/download")
    public ResponseEntity<Resource> downloadArtifact(@PathVariable Long spaceId, @PathVariable Long taskId, @PathVariable Long artifactId) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return taskService.downloadArtifact(spaceId, taskId, artifactId);
    }

    @PostMapping("/api/spaces/{spaceId}/tasks/{taskId}/artifacts/{artifactId}/trace-share")
    public ApiResponse<TaskTraceShareResponse> createTraceShare(
            @PathVariable Long spaceId,
            @PathVariable Long taskId,
            @PathVariable Long artifactId) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(taskTraceShareService.createTraceShare(spaceId, taskId, artifactId));
    }

    @GetMapping("/api/spaces/{spaceId}/tasks/{taskId}/logs")
    public ApiResponse<List<TaskStageLogResponse>> listTaskLogs(@PathVariable Long spaceId, @PathVariable Long taskId) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(taskService.listStageLogs(spaceId, taskId));
    }

    @CrossOrigin(origins = "https://trace.playwright.dev")
    @GetMapping("/api/spaces/{spaceId}/tasks/{taskId}/logs/{logId}/download")
    public ResponseEntity<Resource> downloadTaskLog(@PathVariable Long spaceId, @PathVariable Long taskId, @PathVariable Long logId) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return taskService.downloadStageLog(spaceId, taskId, logId);
    }

    @CrossOrigin(origins = "https://trace.playwright.dev")
    @GetMapping("/api/public/traces/download")
    public ResponseEntity<Resource> downloadSharedTrace(@RequestParam String token) {
        return taskTraceShareService.downloadSharedTrace(token);
    }
}
