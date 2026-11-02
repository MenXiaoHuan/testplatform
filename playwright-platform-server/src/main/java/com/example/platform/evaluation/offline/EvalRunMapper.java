package com.example.platform.evaluation.offline;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

@Mapper
public interface EvalRunMapper {

    @Insert("""
            insert into eval_run (space_id, target_id, prompt_version_id, dataset_id, status, total_samples, evaluator_configs, sample_ids, max_concurrency, trigger_user)
            values (#{spaceId}, #{targetId}, #{promptVersionId}, #{datasetId}, #{status}, #{totalSamples}, #{evaluatorConfigs}, #{sampleIds}, #{maxConcurrency}, #{triggerUser})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EvalRunEntity entity);

    @Select("""
            select id, space_id, target_id, prompt_version_id, dataset_id, status, total_samples, completed_samples, failed_samples,
                   overall_score, evaluator_configs, sample_ids, max_concurrency, started_at, finished_at, trigger_user,
                   version, created_at, updated_at
            from eval_run where id = #{id}
            """)
    @Results(id = "EvalRunResultMap", value = {
        @Result(property = "id", column = "id", id = true),
        @Result(property = "spaceId", column = "space_id"),
        @Result(property = "targetId", column = "target_id"),
        @Result(property = "promptVersionId", column = "prompt_version_id"),
        @Result(property = "datasetId", column = "dataset_id"),
        @Result(property = "status", column = "status"),
        @Result(property = "totalSamples", column = "total_samples"),
        @Result(property = "completedSamples", column = "completed_samples"),
        @Result(property = "failedSamples", column = "failed_samples"),
        @Result(property = "overallScore", column = "overall_score"),
        @Result(property = "evaluatorConfigs", column = "evaluator_configs"),
        @Result(property = "sampleIds", column = "sample_ids"),
        @Result(property = "maxConcurrency", column = "max_concurrency"),
        @Result(property = "startedAt", column = "started_at"),
        @Result(property = "finishedAt", column = "finished_at"),
        @Result(property = "triggerUser", column = "trigger_user"),
        @Result(property = "version", column = "version"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<EvalRunEntity> findById(Long id);

    @Select("""
            select id, space_id, target_id, prompt_version_id, dataset_id, status, total_samples, completed_samples, failed_samples,
                   overall_score, evaluator_configs, sample_ids, max_concurrency, started_at, finished_at, trigger_user,
                   version, created_at, updated_at
            from eval_run where space_id = #{spaceId} order by id desc
            """)
    @ResultMap("EvalRunResultMap")
    List<EvalRunEntity> listBySpace(Long spaceId);

    @Select("select count(*) from eval_run where space_id = #{spaceId} and status in ('RUNNING', 'PENDING')")
    long countRunningBySpace(Long spaceId);

    @Select("select count(*) from eval_run where target_id = #{targetId} and status in ('RUNNING', 'PENDING')")
    long countRunningByTarget(Long targetId);

    /**
     * C20 状态机乐观锁。
     * @param started SQL 片段：RUNNING 转移时传 "started_at = current_timestamp,"，其他传 ""
     */
    @Update("""
            update eval_run
            set status = #{toStatus}, version = version + 1,
                ${started}
                updated_at = current_timestamp
            where id = #{id} and status = #{fromStatus} and version = #{expectedVersion}
            """)
    int updateStatusAndVersion(@Param("id") Long id,
                               @Param("fromStatus") String fromStatus,
                               @Param("toStatus") String toStatus,
                               @Param("expectedVersion") Long expectedVersion,
                               @Param("started") String started);

    /** 强制状态转移（CANCELLED / FAILED 兜底）。 */
    @Update("""
            update eval_run
            set status = #{toStatus}, version = version + 1,
                finished_at = current_timestamp,
                completed_samples = #{completedSamples},
                failed_samples = #{failedSamples},
                overall_score = #{overallScore},
                updated_at = current_timestamp
            where id = #{id}
            """)
    int forceStatusUpdate(Long id, String toStatus, int completedSamples, int failedSamples, Double overallScore);

    @Update("""
            update eval_run
            set completed_samples = #{completed}, failed_samples = #{failed}, updated_at = current_timestamp
            where id = #{id}
            """)
    int updateProgress(Long id, int completed, int failed);

    /**
     * 重跑覆盖：清空原任务的进度与总分并重新排队，原 Agent / 评测集 / 用例范围 / 评估器配置保持不变。
     * 执行中的任务不能被覆盖，需先终止。
     */
    @Update("""
            update eval_run
            set status = 'PENDING', total_samples = #{totalSamples}, sample_ids = #{sampleIds},
                completed_samples = 0, failed_samples = 0, overall_score = null,
                started_at = null, finished_at = null, trigger_user = #{triggerUser},
                version = version + 1, updated_at = current_timestamp
            where id = #{id} and status not in ('PENDING', 'RUNNING')
            """)
    int resetForRerun(@Param("id") Long id,
                      @Param("totalSamples") int totalSamples,
                      @Param("sampleIds") String sampleIds,
                      @Param("triggerUser") String triggerUser);

    @Delete("delete from eval_run where id = #{id} and space_id = #{spaceId}")
    int deleteByIdAndSpace(@Param("id") Long id, @Param("spaceId") Long spaceId);

    /** 已被 listBySpace 取代，保留为兼容旧调用。 */
    default List<EvalRunEntity> listBySpace_placeholder(Long spaceId) {
        return listBySpace(spaceId);
    }
}
