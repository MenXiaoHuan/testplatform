package com.example.platform.space.dto;

import com.example.platform.space.model.SpaceEntity;

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
