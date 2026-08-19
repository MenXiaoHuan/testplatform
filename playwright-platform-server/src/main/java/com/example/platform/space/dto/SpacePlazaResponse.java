package com.example.platform.space.dto;

import com.example.platform.space.model.SpaceEntity;

/**
 * 空间广场响应 DTO，用于在空间广场页面展示空间信息。
 * 包含空间基本信息、所有者信息以及当前用户的访问状态。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装空间基础信息（ID、名称、描述）</li>
 *   <li>封装空间所有者用户信息</li>
 *   <li>封装当前用户对该空间的访问权限状态</li>
 *   <li>提供从实体对象的静态工厂方法</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link SpaceEntity} - 空间实体类</li>
 * </ul>
 */
public record SpacePlazaResponse(
        Long id,
        String name,
        String description,
        Long ownerUserId,
        String ownerUsername,
        String ownerNickname,
        String ownerAvatarUrl,
        boolean accessible,
        boolean manageable,
        String currentRole,
        String pendingRequestedRole) {
    
    /**
     * 从实体对象创建空间广场响应
     *
     * @param entity 空间实体
     * @param ownerUserId 所有者用户ID
     * @param ownerUsername 所有者用户名
     * @param ownerNickname 所有者昵称
     * @param ownerAvatarUrl 所有者头像URL
     * @param accessible 当前用户是否可访问
     * @param manageable 当前用户是否可管理
     * @param currentRole 当前用户角色
     * @param pendingRequestedRole 当前用户待审批的申请角色
     * @return 空间广场响应对象
     */
    public static SpacePlazaResponse from(
            SpaceEntity entity,
            Long ownerUserId,
            String ownerUsername,
            String ownerNickname,
            String ownerAvatarUrl,
            boolean accessible,
            boolean manageable,
            String currentRole,
            String pendingRequestedRole) {
        return new SpacePlazaResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                ownerUserId,
                ownerUsername,
                ownerNickname,
                ownerAvatarUrl,
                accessible,
                manageable,
                currentRole,
                pendingRequestedRole);
    }
}