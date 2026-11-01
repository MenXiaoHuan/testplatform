package com.example.platform.evaluation.online;

import org.apache.ibatis.annotations.*;

import java.util.Optional;

@Mapper
public interface EvalOnlineScoringMapper {

    @Insert("""
            insert into eval_online_scoring (policy_id, langfuse_trace_id, evaluator_scores, weighted_score, low_score)
            values (#{policyId}, #{langfuseTraceId}, #{evaluatorScores}, #{weightedScore}, #{lowScore})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EvalOnlineScoringEntity entity);

    /** C13 断点续跑：判重是否已评分过。 */
    @Select("select id from eval_online_scoring where policy_id = #{policyId} and langfuse_trace_id = #{traceId}")
    Optional<Long> findIdByPolicyAndTrace(@Param("policyId") Long policyId, @Param("traceId") String traceId);

    @Select("select count(*) from eval_online_scoring where policy_id = #{policyId}")
    long countByPolicy(@Param("policyId") Long policyId);
}
