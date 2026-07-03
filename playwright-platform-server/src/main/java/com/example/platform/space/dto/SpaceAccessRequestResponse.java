package com.example.platform.space.dto;

import java.time.LocalDateTime;

public record SpaceAccessRequestResponse(
        Long id,
        Long spaceId,
        Long applicantUserId,
        String applicantUsername,
        String applicantNickname,
        String applicantAvatarUrl,
        String requestedRole,
        String reason,
        String status,
        String reviewComment,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
