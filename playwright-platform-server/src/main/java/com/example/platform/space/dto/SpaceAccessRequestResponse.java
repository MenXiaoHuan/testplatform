package com.example.platform.space.dto;

import java.time.LocalDateTime;

/**
 * 空间访问申请响应 DTO，用于展示访问申请的详细信息。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装申请ID、空间ID和申请人信息</li>
 *   <li>封装申请角色、理由和审批状态</li>
 *   <li>封装审批结果、审批人和审批时间</li>
 *   <li>封装创建时间和更新时间</li>
 * </ul>
 *
 * <p>依赖说明：无外部依赖。
 */
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