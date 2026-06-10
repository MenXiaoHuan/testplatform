package com.example.platform.task.controller;

import com.example.platform.task.dto.CaseResultResponse;
import com.example.platform.task.dto.TaskDetailResponse;
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
    public TaskEntity runScene(@PathVariable Long sceneId) {
        return taskService.createAndRun(sceneId);
    }

    @GetMapping("/api/tasks")
    public List<TaskEntity> listTasks() {
        return taskService.list();
    }

    @GetMapping("/api/tasks/{taskId}")
    public TaskDetailResponse getTask(@PathVariable Long taskId) {
        TaskEntity task = taskService.get(taskId);
        List<ArtifactEntity> artifacts = taskService.listArtifacts(taskId);
        return TaskDetailResponse.from(task, artifacts.size());
    }

    @GetMapping("/api/tasks/{taskId}/report")
    public Map<String, Object> getTaskReport(@PathVariable Long taskId) {
        return Map.of(
                "taskId", taskId,
                "reportUrl", taskService.getReportUrl(taskId));
    }

    @GetMapping("/api/tasks/{taskId}/artifacts")
    public List<ArtifactEntity> listTaskArtifacts(@PathVariable Long taskId) {
        return taskService.listArtifacts(taskId);
    }

    @GetMapping("/api/tasks/{taskId}/cases")
    public List<CaseResultResponse> listTaskCases(@PathVariable Long taskId) {
        return taskService.listCaseResults(taskId).stream()
                .map(caseResult -> CaseResultResponse.from(
                        caseResult,
                        taskService.listArtifactsByCaseResult(caseResult.getId()).size()))
                .toList();
    }

    @GetMapping("/api/tasks/{taskId}/cases/{caseResultId}/artifacts")
    public List<ArtifactEntity> listCaseArtifacts(@PathVariable Long taskId, @PathVariable Long caseResultId) {
        return taskService.listArtifactsByCaseResult(caseResultId);
    }
}
