package com.example.platform.patrol.controller;

import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.common.ApiResponse;
import com.example.platform.common.PageResponse;
import com.example.platform.patrol.dto.PatrolApprovalRequest;
import com.example.platform.patrol.dto.PatrolCaseResponse;
import com.example.platform.patrol.dto.PatrolModelLogResponse;
import com.example.platform.patrol.dto.PatrolReportResponse;
import com.example.platform.patrol.dto.PatrolTaskDetailResponse;
import com.example.platform.patrol.dto.PatrolTaskResponse;
import com.example.platform.patrol.service.PatrolTaskService;
import com.example.platform.space.service.SpaceAuthorizationService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 巡页任务：查询、审批、取消、删除与证据读取。 */
@RestController
@RequestMapping("/api/spaces/{spaceId}/patrol-tasks")
public class PatrolTaskController {
    private final PatrolTaskService taskService;
    private final SpaceAuthorizationService spaceAuthorizationService;

    public PatrolTaskController(PatrolTaskService taskService,
                                SpaceAuthorizationService spaceAuthorizationService) {
        this.taskService = taskService;
        this.spaceAuthorizationService = spaceAuthorizationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PatrolTaskResponse>> list(
            @PathVariable Long spaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(taskService.list(spaceId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<PatrolTaskResponse> get(@PathVariable Long spaceId, @PathVariable Long id) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(taskService.get(spaceId, id));
    }

    @GetMapping("/{id}/detail")
    public ApiResponse<PatrolTaskDetailResponse> detail(@PathVariable Long spaceId, @PathVariable Long id) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(taskService.detail(spaceId, id));
    }

    @GetMapping("/{id}/cases")
    public ApiResponse<List<PatrolCaseResponse>> cases(@PathVariable Long spaceId, @PathVariable Long id) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(taskService.listCases(spaceId, id));
    }

    @GetMapping("/{id}/model-logs")
    public ApiResponse<List<PatrolModelLogResponse>> modelLogs(@PathVariable Long spaceId, @PathVariable Long id) {
        var actor = AuthContextHolder.require();
        if (!actor.superAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "super admin required");
        }
        spaceAuthorizationService.requireReadableSpace(spaceId, actor);
        return ApiResponse.ok(taskService.modelLogs(spaceId, id));
    }

    @GetMapping("/{id}/report")
    public ApiResponse<PatrolReportResponse> report(@PathVariable Long spaceId, @PathVariable Long id) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(taskService.report(spaceId, id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<PatrolTaskResponse> approve(@PathVariable Long spaceId, @PathVariable Long id,
                                                   @RequestBody PatrolApprovalRequest request) {
        var actor = AuthContextHolder.require();
        spaceAuthorizationService.requireOperableSpace(spaceId, actor);
        return ApiResponse.ok(taskService.approve(spaceId, id, request, actor.username()));
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long spaceId, @PathVariable Long id) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        taskService.cancel(spaceId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long spaceId, @PathVariable Long id) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        taskService.delete(spaceId, id);
    }

    @GetMapping("/{id}/evidence")
    public ResponseEntity<InputStreamResource> evidence(@PathVariable Long spaceId,
                                                        @PathVariable Long id,
                                                        @RequestParam String key) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        PatrolTaskService.Evidence evidence = taskService.loadEvidence(spaceId, id, key);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(evidence.filename(), StandardCharsets.UTF_8)
                                .build().toString())
                .contentType(MediaType.parseMediaType(evidence.contentType()))
                .body(new InputStreamResource(evidence.stream()));
    }
}
