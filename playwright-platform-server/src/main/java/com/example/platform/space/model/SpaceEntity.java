package com.example.platform.space.model;

import java.time.LocalDateTime;

/**
 * 空间实体类，对应数据库中的 space 表。
 * 表示一个测试平台的空间，用于组织测试场景和测试仓库。
 *
 * <p>核心职责：
 * <ul>
 *   <li>存储空间基础信息（名称、描述）</li>
 *   <li>记录空间所有者和创建者信息</li>
 *   <li>追踪创建和更新时间</li>
 * </ul>
 *
 * <p>依赖说明：无外部依赖。
 */
public class SpaceEntity {
    private Long id;
    private String name;
    private String description;
    private Long ownerUserId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}