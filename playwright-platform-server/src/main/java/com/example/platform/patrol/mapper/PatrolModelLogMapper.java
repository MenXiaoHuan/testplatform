package com.example.platform.patrol.mapper;

import com.example.platform.patrol.model.PatrolModelLogEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PatrolModelLogMapper {

    String COLUMNS = """
            id, task_id, case_id, case_name, stage, purpose, model_id, status,
            system_prompt, user_prompt, raw_output, prompt_tokens, completion_tokens,
            duration_ms, error_message, created_at
            """;

    @Insert("""
            insert into patrol_model_log (
                task_id, case_id, case_name, stage, purpose, model_id, status,
                system_prompt, user_prompt, raw_output, prompt_tokens, completion_tokens,
                duration_ms, error_message, created_at
            ) values (
                #{taskId}, #{caseId}, #{caseName}, #{stage}, #{purpose}, #{modelId}, #{status},
                #{systemPrompt}, #{userPrompt}, #{rawOutput}, #{promptTokens}, #{completionTokens},
                #{durationMs}, #{errorMessage}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PatrolModelLogEntity entity);

    @Select("select " + COLUMNS + " from patrol_model_log where task_id = #{taskId} order by id asc")
    @Results(id = "PatrolModelLogResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "taskId", column = "task_id"),
            @Result(property = "caseId", column = "case_id"),
            @Result(property = "caseName", column = "case_name"),
            @Result(property = "stage", column = "stage"),
            @Result(property = "purpose", column = "purpose"),
            @Result(property = "modelId", column = "model_id"),
            @Result(property = "status", column = "status"),
            @Result(property = "systemPrompt", column = "system_prompt"),
            @Result(property = "userPrompt", column = "user_prompt"),
            @Result(property = "rawOutput", column = "raw_output"),
            @Result(property = "promptTokens", column = "prompt_tokens"),
            @Result(property = "completionTokens", column = "completion_tokens"),
            @Result(property = "durationMs", column = "duration_ms"),
            @Result(property = "errorMessage", column = "error_message"),
            @Result(property = "createdAt", column = "created_at")
    })
    List<PatrolModelLogEntity> findByTaskId(@Param("taskId") Long taskId);

    @Select("select " + COLUMNS + " from patrol_model_log where task_id = #{taskId} and id = #{id}")
    @ResultMap("PatrolModelLogResultMap")
    PatrolModelLogEntity findById(@Param("taskId") Long taskId, @Param("id") Long id);
}
