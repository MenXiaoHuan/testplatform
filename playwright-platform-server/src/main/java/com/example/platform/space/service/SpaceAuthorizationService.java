package com.example.platform.space.service;

import com.example.platform.auth.context.AuthContext;

/**
 * 空间授权服务接口，定义空间访问权限校验方法。
 *
 * <p>核心职责：
 * <ul>
 *   <li>校验用户是否有读取空间的权限</li>
 *   <li>校验用户是否有操作空间的权限</li>
 *   <li>校验用户是否有空间管理员权限</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link AuthContext} - 认证上下文</li>
 * </ul>
 */
public interface SpaceAuthorizationService {
    
    /**
     * 校验用户是否有读取指定空间的权限
     *
     * @param spaceId 空间ID
     * @param actor 当前操作用户的认证上下文
     */
    void requireReadableSpace(Long spaceId, AuthContext actor);

    /**
     * 校验用户是否有操作指定空间的权限（OPERATOR 或 ADMIN 角色）
     *
     * @param spaceId 空间ID
     * @param actor 当前操作用户的认证上下文
     */
    void requireOperableSpace(Long spaceId, AuthContext actor);

    /**
     * 校验用户是否有指定空间的管理员权限
     *
     * @param spaceId 空间ID
     * @param actor 当前操作用户的认证上下文
     */
    void requireAdminSpace(Long spaceId, AuthContext actor);
}