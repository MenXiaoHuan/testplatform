package com.example.platform.space.dto;

import com.example.platform.space.model.SpaceEntity;

public record SpaceSummaryResponse(
        Long id,
        String name,
        String description) {
    public static SpaceSummaryResponse from(SpaceEntity entity) {
        return new SpaceSummaryResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription());
    }
}
