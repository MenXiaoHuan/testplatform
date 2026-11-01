package com.example.platform.admin.mapper;

import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 平台运行时设置数据访问接口（键值表 platform_setting）。 */
@Mapper
public interface PlatformSettingMapper {

    @Select("""
            select setting_value
            from platform_setting
            where setting_key = #{settingKey}
            limit 1
            """)
    Optional<String> findValueByKey(@Param("settingKey") String settingKey);

    @Insert("""
            insert into platform_setting (setting_key, setting_value, updated_by)
            values (#{settingKey}, #{settingValue}, #{updatedBy})
            on duplicate key update
                setting_value = values(setting_value),
                updated_by = values(updated_by)
            """)
    int upsert(
            @Param("settingKey") String settingKey,
            @Param("settingValue") String settingValue,
            @Param("updatedBy") Long updatedBy);
}
