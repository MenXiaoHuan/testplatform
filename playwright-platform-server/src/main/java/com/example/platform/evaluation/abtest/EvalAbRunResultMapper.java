package com.example.platform.evaluation.abtest;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface EvalAbRunResultMapper {

    @Insert("""
            insert into eval_ab_run_result (experiment_id, input_text, control_output, treatment_outputs,
                control_scores, treatment_scores, langfuse_trace_ids, prompt_versions_used)
            values (#{experimentId}, #{inputText}, #{controlOutput}, #{treatmentOutputs},
                #{controlScores}, #{treatmentScores}, #{langfuseTraceIds}, #{promptVersionsUsed})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EvalAbRunResultEntity entity);

    @Select("select count(*) from eval_ab_run_result where experiment_id = #{experimentId}")
    long countByExperiment(@Param("experimentId") Long experimentId);

    @Select("""
            select control_scores, treatment_scores from eval_ab_run_result
            where experiment_id = #{experimentId} order by id desc limit #{limit}
            """)
    List<EvalAbRunResultEntity> listScoresByExperiment(@Param("experimentId") Long experimentId,
                                                       @Param("limit") int limit);
}
