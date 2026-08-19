package com.example.platform.space.controller;

import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.common.ApiResponse;
import com.example.platform.space.dto.CreateSpaceRequest;
import com.example.platform.space.dto.SpacePlazaResponse;
import com.example.platform.space.dto.SpaceSummaryResponse;
import com.example.platform.space.service.SpaceService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 空间管理控制器，提供空间的增删改查 REST API 接口。
 *
 * <p>核心职责：
 * <ul>
 *   <li>查询当前用户参与的空间列表</li>
 *   <li>获取空间广场（所有公开空间）列表</li>
 *   <li>创建新空间</li>
 *   <li>更新空间信息</li>
 *   <li>删除空间</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link SpaceService} - 空间业务逻辑服务</li>
 *   <li>{@link AuthContextHolder} - 获取当前认证用户上下文</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/spaces")
public class SpaceController {
    private final SpaceService spaceService;

    public SpaceController(SpaceService spaceService) {
        this.spaceService = spaceService;
    }

    /**
     * 列出当前用户参与的所有空间
     *
     * @return 空间摘要列表
     */
    @GetMapping
    public ApiResponse<List<SpaceSummaryResponse>> listMySpaces() {
        return ApiResponse.ok(spaceService.listMySpaces(AuthContextHolder.require()));
    }

    /**
     * 获取空间广场列表（所有空间）
     *
     * @return 空间广场响应列表
     */
    @GetMapping("/plaza")
    public ApiResponse<List<SpacePlazaResponse>> listSpacePlaza() {
        return ApiResponse.ok(spaceService.listSpacePlaza(AuthContextHolder.require()));
    }

    /**
     * 创建新空间
     *
     * @param request 创建空间请求体
     * @return 创建的空间摘要
     */
    @PostMapping
    public ApiResponse<SpaceSummaryResponse> create(@RequestBody CreateSpaceRequest request) {
        return ApiResponse.ok(spaceService.createSpace(AuthContextHolder.require(), request));
    }

    /**
     * 更新指定空间信息
     *
     * @param spaceId 空间ID
     * @param request 更新请求体
     * @return 更新后的空间摘要
     */
    @PutMapping("/{spaceId}")
    public ApiResponse<SpaceSummaryResponse> update(@PathVariable Long spaceId, @RequestBody CreateSpaceRequest request) {
        return ApiResponse.ok(spaceService.updateSpace(AuthContextHolder.require(), spaceId, request));
    }

    /**
     * 删除指定空间
     *
     * @param spaceId 空间ID
     * @return 操作结果
     */
    @DeleteMapping("/{spaceId}")
    public ApiResponse<Void> delete(@PathVariable Long spaceId) {
        spaceService.deleteSpace(AuthContextHolder.require(), spaceId);
        return ApiResponse.ok(null);
    }
}