package com.example.platform.evaluation.abtest;

import org.apache.ibatis.annotations.*;

import java.util.Optional;

@Mapper
public interface EvalAbAssignmentMapper {

    @Insert("""
            insert into eval_ab_assignment (experiment_id, user_hash_bucket, assigned_group)
            values (#{experimentId}, #{userHashBucket}, #{assignedGroup})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EvalAbAssignmentEntity entity);

    @Select("""
            select id, experiment_id, user_hash_bucket, assigned_group
            from eval_ab_assignment where experiment_id = #{experimentId} and user_hash_bucket = #{bucket}
            """)
    Optional<EvalAbAssignmentEntity> findByExperimentAndBucket(@Param("experimentId") Long experimentId,
                                                               @Param("bucket") Integer bucket);
}
