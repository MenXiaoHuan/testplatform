package com.example.platform.patrol.dto;

import com.example.platform.patrol.model.AiModelConfigEntity;

/** 场景编辑时的模型下拉选项。 */
public record AiModelOptionResponse(
        Long id,
        String name,
        String modelId,
        boolean supportsVision
) {
    public static AiModelOptionResponse from(AiModelConfigEntity entity) {
        return new AiModelOptionResponse(
                entity.getId(),
                entity.getName(),
                entity.getModelId(),
                Boolean.TRUE.equals(entity.getSupportsVision()));
    }
}
