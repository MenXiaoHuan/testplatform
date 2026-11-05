package com.example.platform.patrol.mapper;

import com.example.platform.patrol.model.AiPromptConfigEntity;
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
public interface AiPromptConfigMapper {

    String COLUMNS = "id, space_id, label, content, updated_by, created_at, updated_at";

    @Insert("insert into ai_prompt_config (space_id, label, content, updated_by, created_at) "
            + "values (#{spaceId}, #{label}, #{content}, #{updatedBy}, now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiPromptConfigEntity entity);

    @Update("update ai_prompt_config set content = #{content}, updated_by = #{updatedBy} "
            + "where id = #{id} and space_id = #{spaceId}")
    int update(@Param("id") Long id, @Param("spaceId") Long spaceId,
               @Param("content") String content, @Param("updatedBy") String updatedBy);

    @Delete("delete from ai_prompt_config where id = #{id} and space_id = #{spaceId}")
    int deleteByIdAndSpaceId(@Param("id") Long id, @Param("spaceId") Long spaceId);

    @Select("select " + COLUMNS
            + " from ai_prompt_config where id = #{id} and space_id = #{spaceId}")
    @Results(id = "AiPromptConfigResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "spaceId", column = "space_id"),
            @Result(property = "label", column = "label"),
            @Result(property = "content", column = "content"),
            @Result(property = "updatedBy", column = "updated_by"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<AiPromptConfigEntity> findByIdAndSpaceId(@Param("id") Long id, @Param("spaceId") Long spaceId);

    @Select("select " + COLUMNS
            + " from ai_prompt_config where space_id = #{spaceId} order by id asc")
    @ResultMap("AiPromptConfigResultMap")
    List<AiPromptConfigEntity> findBySpaceId(@Param("spaceId") Long spaceId);

    @Select("select count(1) from ai_prompt_config where space_id = #{spaceId} and label = #{label}")
    int countBySpaceIdAndLabel(@Param("spaceId") Long spaceId, @Param("label") String label);
}
