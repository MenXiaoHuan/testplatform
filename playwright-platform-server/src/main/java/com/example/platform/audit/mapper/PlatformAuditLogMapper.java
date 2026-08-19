package com.example.platform.audit.mapper;

import com.example.platform.audit.model.PlatformAuditLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/**
 * 平台审计日志 Mapper 接口 —— 提供审计日志的数据库持久化操作。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #insert(PlatformAuditLogEntity)} —— 插入一条审计日志记录，自动回填主键 ID</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link PlatformAuditLogEntity} —— 审计日志实体，承载入库数据</li>
 *   <li>MyBatis {@code @Mapper} —— 标记为 MyBatis 数据访问接口</li>
 * </ul>
 */
@Mapper
public interface PlatformAuditLogMapper {

    /**
     * 插入一条审计日志记录。
     *
     * @param entity 审计日志实体
     * @return 受影响的行数（成功为 1）
     */
    @Insert("""
            insert into platform_audit_log (
                entity_type, entity_id, action, operator_name, detail_json
            ) values (
                #{entityType}, #{entityId}, #{action}, #{operatorName}, #{detailJson}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PlatformAuditLogEntity entity);
}
