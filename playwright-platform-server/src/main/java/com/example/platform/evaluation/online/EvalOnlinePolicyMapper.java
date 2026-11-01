package com.example.platform.evaluation.online;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface EvalOnlinePolicyMapper {

    @Insert("""
            insert into eval_online_policy (space_id, target_id, name, status, filter_config, evaluator_configs,
                alerting_config, rate_limit_config, scan_interval_minutes, next_scan_at)
            values (#{spaceId}, #{targetId}, #{name}, #{status}, #{filterConfig}, #{evaluatorConfigs},
                #{alertingConfig}, #{rateLimitConfig}, #{scanIntervalMinutes}, #{nextScanAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EvalOnlinePolicyEntity entity);

    @Select("""
            select id, space_id, target_id, name, status, filter_config, evaluator_configs,
                   alerting_config, rate_limit_config, scan_interval_minutes, next_scan_at,
                   created_at, updated_at
            from eval_online_policy where id = #{id}
            """)
    @Results(id = "OnlinePolicyResultMap", value = {
        @Result(property = "id", column = "id", id = true),
        @Result(property = "spaceId", column = "space_id"),
        @Result(property = "targetId", column = "target_id"),
        @Result(property = "name", column = "name"),
        @Result(property = "status", column = "status"),
        @Result(property = "filterConfig", column = "filter_config"),
        @Result(property = "evaluatorConfigs", column = "evaluator_configs"),
        @Result(property = "alertingConfig", column = "alerting_config"),
        @Result(property = "rateLimitConfig", column = "rate_limit_config"),
        @Result(property = "scanIntervalMinutes", column = "scan_interval_minutes"),
        @Result(property = "nextScanAt", column = "next_scan_at"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<EvalOnlinePolicyEntity> findById(Long id);

    @Select("""
            select id, space_id, target_id, name, status, filter_config, evaluator_configs,
                   alerting_config, rate_limit_config, scan_interval_minutes, next_scan_at,
                   created_at, updated_at
            from eval_online_policy where space_id = #{spaceId} order by id desc
            """)
    @ResultMap("OnlinePolicyResultMap")
    List<EvalOnlinePolicyEntity> listBySpace(Long spaceId);

    /** C17 调度扫描：所有 ACTIVE 且 next_scan_at ≤ now 的策略。 */
    @Select("""
            select id, space_id, target_id, name, status, filter_config, evaluator_configs,
                   alerting_config, rate_limit_config, scan_interval_minutes, next_scan_at,
                   created_at, updated_at
            from eval_online_policy
            where status = 'ACTIVE' and (next_scan_at is null or next_scan_at <= #{now})
            """)
    @ResultMap("OnlinePolicyResultMap")
    List<EvalOnlinePolicyEntity> listDuePolicies(@Param("now") LocalDateTime now);

    /** 更新 next_scan_at（C17 抖动调度后写回）。 */
    @Update("update eval_online_policy set next_scan_at = #{nextScanAt}, updated_at = current_timestamp where id = #{id}")
    int updateNextScanAt(@Param("id") Long id, @Param("nextScanAt") LocalDateTime nextScanAt);

    @Update("update eval_online_policy set status = #{status}, updated_at = current_timestamp where id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Delete("delete from eval_online_policy where id = #{id}")
    int deleteById(Long id);
}
