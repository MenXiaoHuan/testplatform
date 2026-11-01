package com.example.platform.evaluation.prompt;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

@Mapper
public interface EvalPromptVersionMapper {

    @Insert("""
            insert into eval_prompt_version (space_id, prompt_key, name, description, prompt_type, template, variables, version_label, change_log, status, superseded_by, created_by)
            values (#{spaceId}, #{promptKey}, #{name}, #{description}, #{promptType}, #{template}, #{variables}, #{versionLabel}, #{changeLog}, #{status}, #{supersededBy}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EvalPromptVersionEntity entity);

    @Select("""
            select id, space_id, prompt_key, name, description, prompt_type, template, variables, version_label, change_log,
                   status, superseded_by, created_by, created_at, updated_at
            from eval_prompt_version where id = #{id}
            """)
    @Results(id = "PromptResultMap", value = {
        @Result(property = "id", column = "id", id = true),
        @Result(property = "spaceId", column = "space_id"),
        @Result(property = "promptKey", column = "prompt_key"),
        @Result(property = "name", column = "name"),
        @Result(property = "description", column = "description"),
        @Result(property = "promptType", column = "prompt_type"),
        @Result(property = "template", column = "template"),
        @Result(property = "variables", column = "variables"),
        @Result(property = "versionLabel", column = "version_label"),
        @Result(property = "changeLog", column = "change_log"),
        @Result(property = "status", column = "status"),
        @Result(property = "supersededBy", column = "superseded_by"),
        @Result(property = "createdBy", column = "created_by"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<EvalPromptVersionEntity> findById(Long id);

    @Select("""
            select id, space_id, prompt_key, name, description, prompt_type, template, variables, version_label, change_log,
                   status, superseded_by, created_by, created_at, updated_at
            from eval_prompt_version
            where space_id = #{spaceId} and prompt_key = #{promptKey}
            order by version_label desc
            """)
    @ResultMap("PromptResultMap")
    List<EvalPromptVersionEntity> listByPromptKey(@Param("spaceId") Long spaceId,
                                                  @Param("promptKey") String promptKey);

    @Select("""
            select id, space_id, prompt_key, name, description, prompt_type, template, variables, version_label, change_log,
                   status, superseded_by, created_by, created_at, updated_at
            from eval_prompt_version
            where space_id = #{spaceId} and prompt_key = #{promptKey} and status = #{status}
            limit 1
            """)
    @ResultMap("PromptResultMap")
    Optional<EvalPromptVersionEntity> findByPromptKeyAndStatus(@Param("spaceId") Long spaceId,
                                                                @Param("promptKey") String promptKey,
                                                                @Param("status") String status);

    @Select("""
            select id, space_id, prompt_key, name, description, prompt_type, template, variables, version_label, change_log,
                   status, superseded_by, created_by, created_at, updated_at
            from eval_prompt_version
            where space_id = #{spaceId} and prompt_key = #{promptKey} and version_label = #{versionLabel}
            limit 1
            """)
    @ResultMap("PromptResultMap")
    Optional<EvalPromptVersionEntity> findByPromptKeyAndVersion(@Param("spaceId") Long spaceId,
                                                                 @Param("promptKey") String promptKey,
                                                                 @Param("versionLabel") String versionLabel);

    @Update("""
            update eval_prompt_version
            set name = #{name}, description = #{description}, template = #{template},
                variables = #{variables}, change_log = #{changeLog}, updated_at = current_timestamp
            where id = #{id}
            """)
    int updateContent(EvalPromptVersionEntity entity);

    @Update("""
            update eval_prompt_version
            set status = #{status}, superseded_by = #{supersededBy}, updated_at = current_timestamp
            where id = #{id}
            """)
    int updateStatus(Long id, String status, Long supersededBy);

    @Delete("delete from eval_prompt_version where id = #{id}")
    int deleteById(Long id);

    @Delete("delete from eval_prompt_version where space_id = #{spaceId} and prompt_key = #{promptKey}")
    int deleteByPromptKey(@Param("spaceId") Long spaceId, @Param("promptKey") String promptKey);

    @Select("select count(*) from eval_prompt_version where space_id = #{spaceId} and prompt_key = #{promptKey}")
    int countByPromptKey(@Param("spaceId") Long spaceId, @Param("promptKey") String promptKey);

    /** 按 promptKey 聚合：版本数 + 当前 ACTIVE 版本号，用于「Prompt 列表」左栏。 */
    @Select("""
            select prompt_key,
                   max(name)        as name,
                   max(description) as description,
                   count(*)         as version_count,
                   max(case when status = 'ACTIVE' then version_label end) as active_version,
                   max(updated_at)  as updated_at
            from eval_prompt_version
            where space_id = #{spaceId}
            group by prompt_key
            order by max(updated_at) desc
            """)
    @Results(id = "PromptKeySummaryMap", value = {
        @Result(property = "promptKey", column = "prompt_key"),
        @Result(property = "name", column = "name"),
        @Result(property = "description", column = "description"),
        @Result(property = "versionCount", column = "version_count"),
        @Result(property = "activeVersion", column = "active_version"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    List<PromptKeySummary> listKeySummaries(Long spaceId);
}
