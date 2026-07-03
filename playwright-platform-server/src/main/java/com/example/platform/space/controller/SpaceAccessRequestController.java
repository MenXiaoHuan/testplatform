package com.example.platform.space.controller;

import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.common.ApiResponse;
import com.example.platform.space.dto.SpaceAccessRequestResponse;
import com.example.platform.space.dto.ReviewSpaceAccessRequestRequest;
import com.example.platform.space.dto.SubmitSpaceAccessRequestRequest;
import com.example.platform.space.service.SpaceAccessRequestService;
import com.example.platform.space.service.SpaceAuthorizationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spaces/{spaceId}/access-requests")
public class SpaceAccessRequestController {
    private final SpaceAccessRequestService spaceAccessRequestService;
    private final SpaceAuthorizationService spaceAuthorizationService;

    public SpaceAccessRequestController(
            SpaceAccessRequestService spaceAccessRequestService,
            SpaceAuthorizationService spaceAuthorizationService) {
        this.spaceAccessRequestService = spaceAccessRequestService;
        this.spaceAuthorizationService = spaceAuthorizationService;
    }

    @PostMapping
    public ApiResponse<Void> submit(
            @PathVariable Long spaceId,
            @RequestBody SubmitSpaceAccessRequestRequest request) {
        spaceAccessRequestService.submitRequest(AuthContextHolder.require(), spaceId, request);
        return ApiResponse.ok(null);
    }

    @GetMapping
    public ApiResponse<List<SpaceAccessRequestResponse>> list(@PathVariable Long spaceId) {
        spaceAuthorizationService.requireAdminSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(spaceAccessRequestService.listBySpace(spaceId, AuthContextHolder.require()));
    }

    @PostMapping("/{requestId}/approve")
    public ApiResponse<Void> approve(
            @PathVariable Long spaceId,
            @PathVariable Long requestId,
            @RequestBody ReviewSpaceAccessRequestRequest request) {
        spaceAuthorizationService.requireAdminSpace(spaceId, AuthContextHolder.require());
        spaceAccessRequestService.approveRequest(AuthContextHolder.require(), spaceId, requestId, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{requestId}/reject")
    public ApiResponse<Void> reject(
            @PathVariable Long spaceId,
            @PathVariable Long requestId,
            @RequestBody ReviewSpaceAccessRequestRequest request) {
        spaceAuthorizationService.requireAdminSpace(spaceId, AuthContextHolder.require());
        spaceAccessRequestService.rejectRequest(AuthContextHolder.require(), spaceId, requestId, request);
        return ApiResponse.ok(null);
    }
}
