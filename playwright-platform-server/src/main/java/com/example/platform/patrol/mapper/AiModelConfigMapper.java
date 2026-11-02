package com.example.platform.patrol.mapper;

import com.example.platform.patrol.model.AiModelConfigEntity;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiModelConfigMapper {

    String COLUMNS = """
            id, space_id, name, provider_type, base_url, api_key_encrypted, model_id,
            supports_vision, supports_tools, supports_json, max_tokens, temperature, enabled,
            last_test_status, last_test_message, last_tested_at, created_by, created_at, updated_at
            """;

    @Insert("""
            insert into ai_model_config (
                space_id, name, provider_type, base_url, api_key_encrypted, model_id,
                supports_vision, supports_tools, supports_json, max_tokens, temperature, enabled,
                created_by, created_at
            ) values (
                #{spaceId}, #{name}, #{providerType}, #{baseUrl}, #{apiKeyEncrypted}, #{modelId},
                #{supportsVision}, #{supportsTools}, #{supportsJson}, #{maxTokens}, #{temperature}, #{enabled},
                #{createdBy}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiModelConfigEntity entity);

    @Update("""
            update ai_model_config
            set name = #{name},
                provider_type = #{providerType},
                base_url = #{baseUrl},
                api_key_encrypted = #{apiKeyEncrypted},
                model_id = #{modelId},
                supports_vision = #{supportsVision},
                supports_tools = #{supportsTools},
                supports_json = #{supportsJson},
                max_tokens = #{maxTokens},
                temperature = #{temperature},
                enabled = #{enabled}
            where id = #{id}
            """)
    int update(AiModelConfigEntity entity);

    @Update("""
            update ai_model_config
            set last_test_status = #{status}, last_test_message = #{message}, last_tested_at = now()
            where id = #{id}
            """)
    int updateTestResult(@Param("id") Long id, @Param("status") String status, @Param("message") String message);

    @Select("select " + COLUMNS + " from ai_model_config where id = #{id}")
    @Results(id = "AiModelConfigResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "spaceId", column = "space_id"),
            @Result(property = "name", column = "name"),
            @Result(property = "providerType", column = "provider_type"),
            @Result(property = "baseUrl", column = "base_url"),
            @Result(property = "apiKeyEncrypted", column = "api_key_encrypted"),
            @Result(property = "modelId", column = "model_id"),
            @Result(property = "supportsVision", column = "supports_vision"),
            @Result(property = "supportsTools", column = "supports_tools"),
            @Result(property = "supportsJson", column = "supports_json"),
            @Result(property = "maxTokens", column = "max_tokens"),
            @Result(property = "temperature", column = "temperature"),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "lastTestStatus", column = "last_test_status"),
            @Result(property = "lastTestMessage", column = "last_test_message"),
            @Result(property = "lastTestedAt", column = "last_tested_at"),
            @Result(property = "createdBy", column = "created_by"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<AiModelConfigEntity> findById(@Param("id") Long id);

    @Select("""
            select\s""" + COLUMNS + """
            from ai_model_config
            where id = #{id} and space_id = #{spaceId}
            """)
    @ResultMap("AiModelConfigResultMap")
    Optional<AiModelConfigEntity> findByIdAndSpaceId(@Param("id") Long id, @Param("spaceId") Long spaceId);

    /** 空间可见配置：仅本空间新建的模型配置。 */
    @Select("""
            select\s""" + COLUMNS + """
            from ai_model_config
            where space_id = #{spaceId}
            order by id asc
            """)
    @ResultMap("AiModelConfigResultMap")
    List<AiModelConfigEntity> findVisibleBySpaceId(@Param("spaceId") Long spaceId);

    @Select("""
            select count(1) > 0
            from ai_model_config
            where name = #{name}
              and space_id <=> #{spaceId}
              and (#{excludeId} is null or id <> #{excludeId})
            """)
    boolean existsByName(@Param("spaceId") Long spaceId, @Param("name") String name,
                         @Param("excludeId") Long excludeId);

    @Delete("delete from ai_model_config where id = #{id} and space_id = #{spaceId}")
    int deleteByIdAndSpaceId(@Param("id") Long id, @Param("spaceId") Long spaceId);
}
