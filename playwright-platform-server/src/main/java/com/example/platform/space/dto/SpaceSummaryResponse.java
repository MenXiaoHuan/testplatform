package com.example.platform.space.dto;

import com.example.platform.space.model.SpaceEntity;

/**
 * 空间摘要响应 DTO，用于在列表中展示空间的简要信息。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装空间ID、名称和描述</li>
 *   <li>提供从实体对象的静态工厂方法</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link SpaceEntity} - 空间实体类</li>
 * </ul>
 */
public record SpaceSummaryResponse(
        Long id,
        String name,
        String description) {
    
    /**
     * 从实体对象创建空间摘要响应
     *
     * @param entity 空间实体
     * @return 空间摘要响应对象
     */
    public static SpaceSummaryResponse from(SpaceEntity entity) {
        return new SpaceSummaryResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription());
    }
}