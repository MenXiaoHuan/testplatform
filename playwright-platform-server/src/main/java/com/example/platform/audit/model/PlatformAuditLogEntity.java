package com.example.platform.audit.model;

import java.time.LocalDateTime;

/**
 * 平台审计日志实体 —— 映射 {@code platform_audit_log} 表，记录系统内所有关键操作的审计信息。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装审计日志的所有字段：实体类型、实体 ID、操作动作、操作人、详细信息</li>
 *   <li>提供标准的 getter/setter 方法供 Mapper 层读写</li>
 * </ul>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@link #id} —— 主键，自增</li>
 *   <li>{@link #entityType} —— 被操作的实体类型（如 scene、space、task）</li>
 *   <li>{@link #entityId} —— 被操作的实体 ID</li>
 *   <li>{@link #action} —— 执行的操作动作（如 CREATE、UPDATE、DELETE）</li>
 *   <li>{@link #operatorName} —— 操作人名称</li>
 *   <li>{@link #detailJson} —— 操作详情，JSON 格式存储</li>
 *   <li>{@link #createdAt} —— 记录创建时间，由数据库自动生成</li>
 * </ul>
 */
public class PlatformAuditLogEntity {

    /** 主键，自增 */
    private Long id;

    /** 被操作的实体类型（如 scene、space、task） */
    private String entityType;

    /** 被操作的实体 ID */
    private Long entityId;

    /** 执行的操作动作（如 CREATE、UPDATE、DELETE） */
    private String action;

    /** 操作人名称 */
    private String operatorName;

    /** 操作详情，JSON 格式存储 */
    private String detailJson;

    /** 记录创建时间，由数据库自动生成 */
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
