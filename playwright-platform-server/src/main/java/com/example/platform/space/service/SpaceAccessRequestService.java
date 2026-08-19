package com.example.platform.space.service;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.space.dto.SpaceAccessRequestResponse;
import com.example.platform.space.dto.ReviewSpaceAccessRequestRequest;
import com.example.platform.space.dto.SubmitSpaceAccessRequestRequest;
import java.util.List;

/**
 * 空间访问申请服务接口，定义访问申请的管理方法。
 *
 * <p>核心职责：
 * <ul>
 *   <li>提交访问申请</li>
 *   <li>列出指定空间的访问申请</li>
 *   <li>批准访问申请</li>
 *   <li>拒绝访问申请</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link AuthContext} - 认证上下文</li>
 *   <li>{@link SpaceAccessRequestResponse} - 访问申请响应 DTO</li>
 *   <li>{@link SubmitSpaceAccessRequestRequest} - 提交申请请求 DTO</li>
 *   <li>{@link ReviewSpaceAccessRequestRequest} - 审批请求 DTO</li>
 * </ul>
 */
public interface SpaceAccessRequestService {
    
    /**
     * 提交访问空间的申请
     *
     * @param actor 当前操作用户的认证上下文
     * @param spaceId 目标空间ID
     * @param request 提交申请请求体
     */
    void submitRequest(AuthContext actor, Long spaceId, SubmitSpaceAccessRequestRequest request);

    /**
     * 列出指定空间的所有访问申请
     *
     * @param spaceId 空间ID
     * @param actor 当前操作用户的认证上下文
     * @return 访问申请响应列表
     */
    List<SpaceAccessRequestResponse> listBySpace(Long spaceId, AuthContext actor);

    /**
     * 批准访问申请
     *
     * @param actor 当前操作用户的认证上下文
     * @param spaceId 空间ID
     * @param requestId 申请ID
     * @param request 审批请求体
     */
    void approveRequest(AuthContext actor, Long spaceId, Long requestId, ReviewSpaceAccessRequestRequest request);

    /**
     * 拒绝访问申请
     *
     * @param actor 当前操作用户的认证上下文
     * @param spaceId 空间ID
     * @param requestId 申请ID
     * @param request 审批请求体
     */
    void rejectRequest(AuthContext actor, Long spaceId, Long requestId, ReviewSpaceAccessRequestRequest request);
}