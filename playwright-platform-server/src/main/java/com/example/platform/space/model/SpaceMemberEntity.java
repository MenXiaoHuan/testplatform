package com.example.platform.space.model;

import java.time.LocalDateTime;

/**
 * 空间成员实体类，对应数据库中的 space_member 表。
 * 表示用户与空间之间的成员关系，包含角色和状态信息。
 *
 * <p>核心职责：
 * <ul>
 *   <li>存储空间ID和用户ID的关联关系</li>
 *   <li>记录成员角色（如 ADMIN、OPERATOR、VIEWER）</li>
 *   <li>记录成员状态（如 ACTIVE、INACTIVE）</li>
 *   <li>追踪加入时间和创建/更新时间</li>
 * </ul>
 *
 * <p>依赖说明：无外部依赖。
 */
public class SpaceMemberEntity {
    private Long id;
    private Long spaceId;
    private Long userId;
    private String role;
    private String status;
    private LocalDateTime joinedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}