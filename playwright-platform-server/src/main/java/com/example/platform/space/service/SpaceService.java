package com.example.platform.space.service;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.space.dto.CreateSpaceRequest;
import com.example.platform.space.dto.SpacePlazaResponse;
import com.example.platform.space.dto.SpaceSummaryResponse;
import java.util.List;

/**
 * 空间业务逻辑接口，定义空间的核心操作方法。
 *
 * <p>核心职责：
 * <ul>
 *   <li>创建新空间</li>
 *   <li>更新空间信息</li>
 *   <li>删除空间</li>
 *   <li>列出当前用户参与的空间</li>
 *   <li>获取空间广场列表</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link AuthContext} - 认证上下文</li>
 *   <li>{@link CreateSpaceRequest} - 创建空间请求 DTO</li>
 *   <li>{@link SpaceSummaryResponse} - 空间摘要响应 DTO</li>
 *   <li>{@link SpacePlazaResponse} - 空间广场响应 DTO</li>
 * </ul>
 */
public interface SpaceService {
    
    /**
     * 创建新空间
     *
     * @param actor 当前操作用户的认证上下文
     * @param request 创建空间请求体
     * @return 创建的空间摘要
     */
    SpaceSummaryResponse createSpace(AuthContext actor, CreateSpaceRequest request);

    /**
     * 更新指定空间的信息
     *
     * @param actor 当前操作用户的认证上下文
     * @param spaceId 空间ID
     * @param request 更新请求体
     * @return 更新后的空间摘要
     */
    SpaceSummaryResponse updateSpace(AuthContext actor, Long spaceId, CreateSpaceRequest request);

    /**
     * 删除指定空间
     *
     * @param actor 当前操作用户的认证上下文
     * @param spaceId 空间ID
     */
    void deleteSpace(AuthContext actor, Long spaceId);

    /**
     * 列出当前用户参与的所有空间
     *
     * @param actor 当前操作用户的认证上下文
     * @return 空间摘要列表
     */
    List<SpaceSummaryResponse> listMySpaces(AuthContext actor);

    /**
     * 获取空间广场列表，包含所有空间及其访问状态
     *
     * @param actor 当前操作用户的认证上下文
     * @return 空间广场响应列表
     */
    List<SpacePlazaResponse> listSpacePlaza(AuthContext actor);
}