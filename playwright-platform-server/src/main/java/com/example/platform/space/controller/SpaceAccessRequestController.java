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

/**
 * 空间访问请求控制器，管理用户申请加入空间的流程。
 *
 * <p>核心职责：
 * <ul>
 *   <li>提交访问申请</li>
 *   <li>获取指定空间的访问申请列表（需管理员权限）</li>
 *   <li>批准访问申请（需管理员权限）</li>
 *   <li>拒绝访问申请（需管理员权限）</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link SpaceAccessRequestService} - 访问申请业务逻辑服务</li>
 *   <li>{@link SpaceAuthorizationService} - 空间权限校验服务</li>
 *   <li>{@link AuthContextHolder} - 获取当前认证用户上下文</li>
 * </ul>
 */
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

    /**
     * 提交访问空间的申请
     *
     * @param spaceId 目标空间ID
     * @param request 申请请求体（包含申请角色和理由）
     * @return 操作结果
     */
    @PostMapping
    public ApiResponse<Void> submit(
            @PathVariable Long spaceId,
            @RequestBody SubmitSpaceAccessRequestRequest request) {
        spaceAccessRequestService.submitRequest(AuthContextHolder.require(), spaceId, request);
        return ApiResponse.ok(null);
    }

    /**
     * 获取指定空间的所有访问申请列表
     * 仅空间管理员可访问此接口
     *
     * @param spaceId 空间ID
     * @return 访问申请响应列表
     */
    @GetMapping
    public ApiResponse<List<SpaceAccessRequestResponse>> list(@PathVariable Long spaceId) {
        spaceAuthorizationService.requireAdminSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(spaceAccessRequestService.listBySpace(spaceId, AuthContextHolder.require()));
    }

    /**
     * 批准访问申请
     * 仅空间管理员可执行此操作
     *
     * @param spaceId 空间ID
     * @param requestId 申请ID
     * @param request 审批请求体（包含审批意见）
     * @return 操作结果
     */
    @PostMapping("/{requestId}/approve")
    public ApiResponse<Void> approve(
            @PathVariable Long spaceId,
            @PathVariable Long requestId,
            @RequestBody ReviewSpaceAccessRequestRequest request) {
        spaceAuthorizationService.requireAdminSpace(spaceId, AuthContextHolder.require());
        spaceAccessRequestService.approveRequest(AuthContextHolder.require(), spaceId, requestId, request);
        return ApiResponse.ok(null);
    }

    /**
     * 拒绝访问申请
     * 仅空间管理员可执行此操作
     *
     * @param spaceId 空间ID
     * @param requestId 申请ID
     * @param request 审批请求体（包含拒绝理由）
     * @return 操作结果
     */
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