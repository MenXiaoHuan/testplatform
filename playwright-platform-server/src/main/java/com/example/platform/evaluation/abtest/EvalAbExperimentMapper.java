package com.example.platform.evaluation.abtest;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

@Mapper
public interface EvalAbExperimentMapper {

    @Insert("""
            insert into eval_ab_experiment (space_id, name, hypothesis, status, control_target_id, control_prompt_version_id,
                treatment_target_ids, treatment_prompt_version_ids, traffic_percentage, core_metrics, dataset_id, started_at)
            values (#{spaceId}, #{name}, #{hypothesis}, #{status}, #{controlTargetId}, #{controlPromptVersionId},
                #{treatmentTargetIds}, #{treatmentPromptVersionIds}, #{trafficPercentage}, #{coreMetrics}, #{datasetId}, #{startedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EvalAbExperimentEntity entity);

    @Select("""
            select id, space_id, name, hypothesis, status, control_target_id, control_prompt_version_id,
                   treatment_target_ids, treatment_prompt_version_ids, traffic_percentage, core_metrics, dataset_id,
                   started_at, finished_at, created_at, updated_at
            from eval_ab_experiment where id = #{id}
            """)
    @Results(id = "AbExperimentResultMap", value = {
        @Result(property = "id", column = "id", id = true),
        @Result(property = "spaceId", column = "space_id"),
        @Result(property = "name", column = "name"),
        @Result(property = "hypothesis", column = "hypothesis"),
        @Result(property = "status", column = "status"),
        @Result(property = "controlTargetId", column = "control_target_id"),
        @Result(property = "controlPromptVersionId", column = "control_prompt_version_id"),
        @Result(property = "treatmentTargetIds", column = "treatment_target_ids"),
        @Result(property = "treatmentPromptVersionIds", column = "treatment_prompt_version_ids"),
        @Result(property = "trafficPercentage", column = "traffic_percentage"),
        @Result(property = "coreMetrics", column = "core_metrics"),
        @Result(property = "datasetId", column = "dataset_id"),
        @Result(property = "startedAt", column = "started_at"),
        @Result(property = "finishedAt", column = "finished_at"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<EvalAbExperimentEntity> findById(Long id);

    @Select("""
            select id, space_id, name, hypothesis, status, control_target_id, control_prompt_version_id,
                   treatment_target_ids, treatment_prompt_version_ids, traffic_percentage, core_metrics, dataset_id,
                   started_at, finished_at, created_at, updated_at
            from eval_ab_experiment where space_id = #{spaceId} order by id desc
            """)
    @ResultMap("AbExperimentResultMap")
    List<EvalAbExperimentEntity> listBySpace(Long spaceId);

    @Update("update eval_ab_experiment set status = #{status}, updated_at = current_timestamp where id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("""
            update eval_ab_experiment set status = #{status}, finished_at = current_timestamp, updated_at = current_timestamp where id = #{id}
            """)
    int finish(@Param("id") Long id, @Param("status") String status);

    @Delete("delete from eval_ab_experiment where id = #{id}")
    int deleteById(Long id);
}
