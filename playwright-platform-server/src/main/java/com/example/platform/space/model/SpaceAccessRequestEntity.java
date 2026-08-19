package com.example.platform.space.model;

import java.time.LocalDateTime;

/**
 * 空间访问申请实体类，对应数据库中的 space_access_request 表。
 * 表示用户申请加入某个空间的请求记录，包含审批流程相关信息。
 *
 * <p>核心职责：
 * <ul>
 *   <li>存储申请人和目标空间的关联信息</li>
 *   <li>记录申请的角色和理由</li>
 *   <li>记录审批状态（如 PENDING、APPROVED、REJECTED）</li>
 *   <li>记录审批结果、审批人和审批时间</li>
 *   <li>追踪创建和更新时间</li>
 * </ul>
 *
 * <p>依赖说明：无外部依赖。
 */
public class SpaceAccessRequestEntity {
    private Long id;
    private Long spaceId;
    private Long applicantUserId;
    private String requestedRole;
    private String reason;
    private String status;
    private String reviewComment;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public Long getApplicantUserId() { return applicantUserId; }
    public void setApplicantUserId(Long applicantUserId) { this.applicantUserId = applicantUserId; }
    public String getRequestedRole() { return requestedRole; }
    public void setRequestedRole(String requestedRole) { this.requestedRole = requestedRole; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}