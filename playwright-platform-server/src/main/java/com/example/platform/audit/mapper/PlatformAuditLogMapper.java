package com.example.platform.audit.mapper;

import com.example.platform.audit.model.PlatformAuditLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface PlatformAuditLogMapper {
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
