package com.example.platform.evaluation.dataset;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

@Mapper
public interface EvalDatasetMapper {

    @Insert("""
            insert into eval_dataset (space_id, name, version, description, scenario_tags, recommended_target_types,
                                      recommended_evaluators, external_source, builtin_key, is_builtin, total_samples, annotated_samples)
            values (#{spaceId}, #{name}, #{version}, #{description}, #{scenarioTags}, #{recommendedTargetTypes},
                    #{recommendedEvaluators}, #{externalSource}, #{builtinKey}, #{isBuiltin}, #{totalSamples}, #{annotatedSamples})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EvalDatasetEntity entity);

    @Select("""
            select id, space_id, name, version, description, scenario_tags, recommended_target_types, recommended_evaluators, external_source,
                   builtin_key, is_builtin, sync_status, last_synced_at, sync_error,
                   total_samples, annotated_samples, created_at, updated_at
            from eval_dataset where id = #{id}
            """)
    @Results(id = "DatasetResultMap", value = {
        @Result(property = "id", column = "id", id = true),
        @Result(property = "spaceId", column = "space_id"),
        @Result(property = "name", column = "name"),
        @Result(property = "version", column = "version"),
        @Result(property = "description", column = "description"),
        @Result(property = "scenarioTags", column = "scenario_tags"),
        @Result(property = "recommendedTargetTypes", column = "recommended_target_types"),
        @Result(property = "recommendedEvaluators", column = "recommended_evaluators"),
        @Result(property = "externalSource", column = "external_source"),
        @Result(property = "builtinKey", column = "builtin_key"),
        @Result(property = "isBuiltin", column = "is_builtin"),
        @Result(property = "syncStatus", column = "sync_status"),
        @Result(property = "lastSyncedAt", column = "last_synced_at"),
        @Result(property = "syncError", column = "sync_error"),
        @Result(property = "totalSamples", column = "total_samples"),
        @Result(property = "annotatedSamples", column = "annotated_samples"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<EvalDatasetEntity> findById(Long id);

    @Select("""
            select id, space_id, name, version, description, scenario_tags, recommended_target_types, recommended_evaluators, external_source,
                   builtin_key, is_builtin, sync_status, last_synced_at, sync_error,
                   total_samples, annotated_samples, created_at, updated_at
            from eval_dataset
            where space_id = #{spaceId}
            order by is_builtin desc, id asc
            """)
    @ResultMap("DatasetResultMap")
    List<EvalDatasetEntity> listBySpace(Long spaceId);

    @Select("""
            select id, space_id, name, version, description, scenario_tags, recommended_target_types, recommended_evaluators, external_source,
                   builtin_key, is_builtin, sync_status, last_synced_at, sync_error,
                   total_samples, annotated_samples, created_at, updated_at
            from eval_dataset
            where space_id = #{spaceId} and builtin_key = #{builtinKey}
            """)
    @ResultMap("DatasetResultMap")
    Optional<EvalDatasetEntity> findByBuiltinKey(@Param("spaceId") Long spaceId,
                                                 @Param("builtinKey") String builtinKey);

    @Select("""
            select id, space_id, name, version, description, scenario_tags, recommended_target_types, recommended_evaluators, external_source,
                   builtin_key, is_builtin, sync_status, last_synced_at, sync_error,
                   total_samples, annotated_samples, created_at, updated_at
            from eval_dataset where is_builtin = 1 order by id asc
            """)
    @ResultMap("DatasetResultMap")
    List<EvalDatasetEntity> listBuiltin();

    @Select("select count(*) from eval_dataset where space_id = #{spaceId} and name = #{name}")
    int countBySpaceAndName(@Param("spaceId") Long spaceId, @Param("name") String name);

    @Update("update eval_dataset set total_samples = #{totalSamples}, annotated_samples = #{annotatedSamples} where id = #{id}")
    int updateCounts(Long id, int totalSamples, int annotatedSamples);

    @Update("update eval_dataset set version = #{version} where id = #{id}")
    int updateVersion(@Param("id") Long id, @Param("version") String version);

    @Update("update eval_dataset set recommended_evaluators = #{recommendedEvaluators} where id = #{id}")
    int updateRecommendedEvaluators(@Param("id") Long id,
                                    @Param("recommendedEvaluators") String recommendedEvaluators);

    @Update("""
            update eval_dataset
            set sync_status = #{syncStatus},
                last_synced_at = #{lastSyncedAt},
                sync_error = #{syncError}
            where id = #{id}
            """)
    int updateSyncStatus(@Param("id") Long id,
                         @Param("syncStatus") String syncStatus,
                         @Param("lastSyncedAt") java.time.LocalDateTime lastSyncedAt,
                         @Param("syncError") String syncError);

    @Delete("delete from eval_dataset where id = #{id}")
    int deleteById(Long id);
}
