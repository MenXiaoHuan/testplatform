package com.example.platform.evaluation.offline;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface EvalRunCaseResultMapper {

    @Insert("""
            insert into eval_run_case_result
                (run_id, sample_id, sample_index, agent_output, golden_answer, evaluator_scores,
                 weighted_score, tokens_in, tokens_out, duration_ms, external_trace_id,
                 langfuse_trace_id, actual_prompt_version_id, status, error_message)
            values
                (#{runId}, #{sampleId}, #{sampleIndex}, #{agentOutput}, #{goldenAnswer}, #{evaluatorScores},
                 #{weightedScore}, #{tokensIn}, #{tokensOut}, #{durationMs}, #{externalTraceId},
                 #{langfuseTraceId}, #{actualPromptVersionId}, #{status}, #{errorMessage})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EvalRunCaseResultEntity entity);

    @Select("""
            select c.id, c.run_id, c.sample_id, c.sample_index, c.agent_output, c.golden_answer,
                   c.evaluator_scores, c.weighted_score, c.tokens_in, c.tokens_out, c.duration_ms,
                   c.external_trace_id, c.langfuse_trace_id, c.actual_prompt_version_id,
                   c.status, c.error_message, c.created_at,
                   s.input_text as sample_input_text
            from eval_run_case_result c
            left join eval_dataset_sample s on s.id = c.sample_id
            where c.run_id = #{runId}
            order by c.sample_index asc, c.id asc
            """)
    @Results(id = "EvalCaseResultMap", value = {
        @Result(property = "id", column = "id", id = true),
        @Result(property = "runId", column = "run_id"),
        @Result(property = "sampleId", column = "sample_id"),
        @Result(property = "sampleIndex", column = "sample_index"),
        @Result(property = "agentOutput", column = "agent_output"),
        @Result(property = "goldenAnswer", column = "golden_answer"),
        @Result(property = "evaluatorScores", column = "evaluator_scores"),
        @Result(property = "weightedScore", column = "weighted_score"),
        @Result(property = "tokensIn", column = "tokens_in"),
        @Result(property = "tokensOut", column = "tokens_out"),
        @Result(property = "durationMs", column = "duration_ms"),
        @Result(property = "externalTraceId", column = "external_trace_id"),
        @Result(property = "langfuseTraceId", column = "langfuse_trace_id"),
        @Result(property = "actualPromptVersionId", column = "actual_prompt_version_id"),
        @Result(property = "status", column = "status"),
        @Result(property = "errorMessage", column = "error_message"),
        @Result(property = "sampleInputText", column = "sample_input_text"),
        @Result(property = "createdAt", column = "created_at")
    })
    List<EvalRunCaseResultEntity> listByRun(Long runId);

    @Delete("delete from eval_run_case_result where run_id = #{runId}")
    int deleteByRunId(Long runId);
}
