package com.example.platform.scene.controller;

import com.example.platform.common.ApiResponse;
import com.example.platform.common.PageResponse;
import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.scene.dto.ScheduleEventIssueResponse;
import com.example.platform.scene.dto.ScheduleEventRetryRequest;
import com.example.platform.scene.service.ScheduleEventAdminService;
import com.example.platform.space.service.SpaceAuthorizationService;
import com.example.platform.task.dto.TaskRunResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 调度事件控制器 —— 对外暴露调度事件管理与重试的 HTTP 接口。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@code GET /api/spaces/{spaceId}/schedule-events} —— 分页查询调度事件列表（支持 scheduleType / sceneId / sceneName / traceId 筛选）</li>
 *   <li>{@code POST /api/spaces/{spaceId}/schedule-events/{eventId}/retry} —— 手动重试失败/终止的调度事件</li>
 * </ul>
 *
 * <p>依赖：{@link ScheduleEventAdminService}（调度事件管理服务）、
 * {@link SpaceAuthorizationService}（空间权限校验）。
 */
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

    /** 分页查询调度事件列表，支持按调度类型、场景 ID、场景名称、traceId 筛选。 */
    @GetMapping
    public ApiResponse<PageResponse<ScheduleEventIssueResponse>> listIssueEvents(
            @PathVariable Long spaceId,
            @RequestParam(name = "scheduleType", required = false) String scheduleType,
            @RequestParam(name = "sceneId", required = false) Long sceneId,
            @RequestParam(name = "sceneName", required = false) String sceneNameLike,
            @RequestParam(name = "traceId", required = false) String traceId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(adminService.listEventsV2(
                spaceId, sceneId, scheduleType, sceneNameLike, traceId, page, limit));
    }

    /** 手动重试指定调度事件，仅允许重试状态为 FAILED 或 ABANDONED 的事件。 */
    @PostMapping("/{eventId}/retry")
    public ApiResponse<TaskRunResponse> retryEvent(
            @PathVariable Long spaceId,
            @PathVariable Long eventId,
            @RequestBody(required = false) ScheduleEventRetryRequest request) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(adminService.retryEvent(spaceId, eventId, request));
    }
}
