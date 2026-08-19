package com.example.platform.space.dto;

import java.time.LocalDateTime;

/**
 * 空间访问申请投影类，用于 MyBatis 查询结果映射。
 * 包含申请人的用户名、昵称、头像等关联信息。
 *
 * <p>核心职责：
 * <ul>
 *   <li>映射空间访问申请及其关联用户信息的查询结果</li>
 *   <li>提供 getter/setter 方法供 MyBatis 和业务层使用</li>
 * </ul>
 *
 * <p>依赖说明：无外部依赖。
 */
public class SpaceAccessRequestProjection {
    private Long id;
    private Long spaceId;
    private Long applicantUserId;
    private String applicantUsername;
    private String applicantNickname;
    private String applicantAvatarObjectKey;
    private String requestedRole;
    private String reason;
    private String status;
    private String reviewComment;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SpaceAccessRequestProjection() {
    }

    public SpaceAccessRequestProjection(
            Long id,
            Long spaceId,
            Long applicantUserId,
            String applicantUsername,
            String applicantNickname,
            String applicantAvatarObjectKey,
            String requestedRole,
            String reason,
            String status,
            String reviewComment,
            Long reviewedBy,
            LocalDateTime reviewedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.spaceId = spaceId;
        this.applicantUserId = applicantUserId;
        this.applicantUsername = applicantUsername;
        this.applicantNickname = applicantNickname;
        this.applicantAvatarObjectKey = applicantAvatarObjectKey;
        this.requestedRole = requestedRole;
        this.reason = reason;
        this.status = status;
        this.reviewComment = reviewComment;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public Long getApplicantUserId() { return applicantUserId; }
    public void setApplicantUserId(Long applicantUserId) { this.applicantUserId = applicantUserId; }
    public String getApplicantUsername() { return applicantUsername; }
    public void setApplicantUsername(String applicantUsername) { this.applicantUsername = applicantUsername; }
    public String getApplicantNickname() { return applicantNickname; }
    public void setApplicantNickname(String applicantNickname) { this.applicantNickname = applicantNickname; }
    public String getApplicantAvatarObjectKey() { return applicantAvatarObjectKey; }
    public void setApplicantAvatarObjectKey(String applicantAvatarObjectKey) { this.applicantAvatarObjectKey = applicantAvatarObjectKey; }
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