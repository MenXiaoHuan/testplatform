package com.example.platform.scene.controller;

import com.example.platform.common.ApiResponse;
import com.example.platform.common.PageResponse;
import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.scene.dto.ScheduleEventIssueResponse;
import com.example.platform.scene.dto.ScheduleEventRetryRequest;
import com.example.platform.scene.service.ScheduleEventAdminService;
import com.example.platform.space.service.SpaceAuthorizationService;
import com.example.platform.task.dto.TaskRunResponse;
import java.util.Arrays;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spaces/{spaceId}/schedule-events")
public class ScheduleEventController {
    private final ScheduleEventAdminService adminService;
    private final SpaceAuthorizationService spaceAuthorizationService;

    public ScheduleEventController(
            ScheduleEventAdminService adminService,
            SpaceAuthorizationService spaceAuthorizationService) {
        this.adminService = adminService;
        this.spaceAuthorizationService = spaceAuthorizationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ScheduleEventIssueResponse>> listIssueEvents(
            @PathVariable Long spaceId,
            @RequestParam(name = "status", defaultValue = "FAILED,ABANDONED") String statusCsv,
            @RequestParam(name = "sceneId", required = false) Long sceneId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(adminService.listIssueEvents(
                Arrays.stream(statusCsv.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .toList(),
                spaceId,
                sceneId,
                page,
                limit));
    }

    @PostMapping("/{eventId}/retry")
    public ApiResponse<TaskRunResponse> retryEvent(
            @PathVariable Long spaceId,
            @PathVariable Long eventId,
            @RequestBody(required = false) ScheduleEventRetryRequest request) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(adminService.retryEvent(spaceId, eventId, request));
    }
}
