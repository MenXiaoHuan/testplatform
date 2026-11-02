package com.example.platform.patrol.mapper;

import com.example.platform.patrol.model.PatrolSceneTokenTotal;
import com.example.platform.patrol.model.PatrolTaskEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PatrolTaskMapper {

    String COLUMNS = """
            id, space_id, scene_id, scene_name, trigger_type, status, current_stage, start_url,
            task_brief, planning_note, scene_snapshot_json, model_snapshot_json, snapshot_json, snapshot_screenshot_key,
            approval_json, approved_by, approved_at, budget_duration_minutes, budget_llm_tokens, budget_cases,
            case_count, case_pass_count, case_fail_count, case_skip_count,
            finding_p0_count, finding_p1_count, finding_p2_count, llm_token_used, degraded,
            result_message, cancel_requested, retention_until, started_at, finished_at, duration_ms,
            created_by, created_at, updated_at
            """;

    @Insert("""
            insert into patrol_task (
                space_id, scene_id, scene_name, trigger_type, status, current_stage, start_url, task_brief, planning_note,
                scene_snapshot_json, model_snapshot_json, budget_duration_minutes, budget_llm_tokens, budget_cases,
                created_by, created_at
            ) values (
                #{spaceId}, #{sceneId}, #{sceneName}, #{triggerType}, #{status}, #{currentStage}, #{startUrl}, #{taskBrief}, #{planningNote},
                #{sceneSnapshotJson}, #{modelSnapshotJson}, #{budgetDurationMinutes}, #{budgetLlmTokens}, #{budgetCases},
                #{createdBy}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PatrolTaskEntity entity);

    @Update("""
            update patrol_task
            set status = #{status},
                current_stage = #{currentStage},
                planning_note = #{planningNote},
                snapshot_json = #{snapshotJson},
                snapshot_screenshot_key = #{snapshotScreenshotKey},
                approval_json = #{approvalJson},
                approved_by = #{approvedBy},
                approved_at = #{approvedAt},
                case_count = #{caseCount},
                case_pass_count = #{casePassCount},
                case_fail_count = #{caseFailCount},
                case_skip_count = #{caseSkipCount},
                finding_p0_count = #{findingP0Count},
                finding_p1_count = #{findingP1Count},
                finding_p2_count = #{findingP2Count},
                llm_token_used = #{llmTokenUsed},
                degraded = #{degraded},
                result_message = #{resultMessage},
                cancel_requested = #{cancelRequested},
                retention_until = #{retentionUntil},
                started_at = #{startedAt},
                finished_at = #{finishedAt},
                duration_ms = #{durationMs}
            where id = #{id}
            """)
    int update(PatrolTaskEntity entity);

    @Update("""
            update patrol_task
            set status = #{status}, current_stage = #{stage}, started_at = coalesce(started_at, now())
            where id = #{id}
            """)
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("stage") String stage);

    @Update("update patrol_task set cancel_requested = #{cancelRequested} where id = #{id}")
    int updateCancelRequested(@Param("id") Long id, @Param("cancelRequested") boolean cancelRequested);

    @Update("""
            update patrol_task
            set status = #{status}, current_stage = 'FINISHED', result_message = #{message},
                finished_at = now(),
                duration_ms = timestampdiff(microsecond, coalesce(started_at, created_at), now()) / 1000,
                retention_until = date_add(now(), interval #{retentionDays} day)
            where id = #{id}
            """)
    int finish(@Param("id") Long id, @Param("status") String status,
               @Param("message") String message, @Param("retentionDays") int retentionDays);

    @Select("select " + COLUMNS + " from patrol_task where id = #{id}")
    @Results(id = "PatrolTaskResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "spaceId", column = "space_id"),
            @Result(property = "sceneId", column = "scene_id"),
            @Result(property = "sceneName", column = "scene_name"),
            @Result(property = "triggerType", column = "trigger_type"),
            @Result(property = "status", column = "status"),
            @Result(property = "currentStage", column = "current_stage"),
            @Result(property = "startUrl", column = "start_url"),
            @Result(property = "taskBrief", column = "task_brief"),
            @Result(property = "planningNote", column = "planning_note"),
            @Result(property = "sceneSnapshotJson", column = "scene_snapshot_json"),
            @Result(property = "modelSnapshotJson", column = "model_snapshot_json"),
            @Result(property = "snapshotJson", column = "snapshot_json"),
            @Result(property = "snapshotScreenshotKey", column = "snapshot_screenshot_key"),
            @Result(property = "approvalJson", column = "approval_json"),
            @Result(property = "approvedBy", column = "approved_by"),
            @Result(property = "approvedAt", column = "approved_at"),
            @Result(property = "budgetDurationMinutes", column = "budget_duration_minutes"),
            @Result(property = "budgetLlmTokens", column = "budget_llm_tokens"),
            @Result(property = "budgetCases", column = "budget_cases"),
            @Result(property = "caseCount", column = "case_count"),
            @Result(property = "casePassCount", column = "case_pass_count"),
            @Result(property = "caseFailCount", column = "case_fail_count"),
            @Result(property = "caseSkipCount", column = "case_skip_count"),
            @Result(property = "findingP0Count", column = "finding_p0_count"),
            @Result(property = "findingP1Count", column = "finding_p1_count"),
            @Result(property = "findingP2Count", column = "finding_p2_count"),
            @Result(property = "llmTokenUsed", column = "llm_token_used"),
            @Result(property = "degraded", column = "degraded"),
            @Result(property = "resultMessage", column = "result_message"),
            @Result(property = "cancelRequested", column = "cancel_requested"),
            @Result(property = "retentionUntil", column = "retention_until"),
            @Result(property = "startedAt", column = "started_at"),
            @Result(property = "finishedAt", column = "finished_at"),
            @Result(property = "durationMs", column = "duration_ms"),
            @Result(property = "createdBy", column = "created_by"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<PatrolTaskEntity> findById(@Param("id") Long id);

    @Select("select " + COLUMNS + " from patrol_task where id = #{id} and space_id = #{spaceId}")
    @ResultMap("PatrolTaskResultMap")
    Optional<PatrolTaskEntity> findByIdAndSpaceId(@Param("id") Long id, @Param("spaceId") Long spaceId);

    @Select("""
            select\s""" + COLUMNS + """
            from patrol_task
            where space_id = #{spaceId}
            order by created_at desc, id desc
            limit #{limit} offset #{offset}
            """)
    @ResultMap("PatrolTaskResultMap")
    List<PatrolTaskEntity> findPageBySpaceId(@Param("spaceId") Long spaceId,
                                             @Param("limit") int limit,
                                             @Param("offset") int offset);

    @Select("select count(1) from patrol_task where space_id = #{spaceId}")
    long countBySpaceId(@Param("spaceId") Long spaceId);

    @Select("""
            select\s""" + COLUMNS + """
            from patrol_task
            where scene_id = #{sceneId}
            order by created_at desc, id desc
            limit #{limit} offset #{offset}
            """)
    @ResultMap("PatrolTaskResultMap")
    List<PatrolTaskEntity> findPageBySceneId(@Param("sceneId") Long sceneId,
                                             @Param("limit") int limit,
                                             @Param("offset") int offset);

    @Select("select count(1) from patrol_task where scene_id = #{sceneId}")
    long countBySceneId(@Param("sceneId") Long sceneId);

    @Select("select " + COLUMNS + " from patrol_task where scene_id = #{sceneId} order by id asc")
    @ResultMap("PatrolTaskResultMap")
    List<PatrolTaskEntity> findBySceneId(@Param("sceneId") Long sceneId);

    @Select("""
            select scene_id, coalesce(sum(llm_token_used), 0) as total_tokens
            from patrol_task
            where space_id = #{spaceId}
            group by scene_id
            """)
    @Results(id = "PatrolSceneTokenTotalMap", value = {
            @Result(property = "sceneId", column = "scene_id"),
            @Result(property = "totalTokens", column = "total_tokens")
    })
    List<PatrolSceneTokenTotal> sumTokensBySpaceId(@Param("spaceId") Long spaceId);

    @Select("""
            select count(1) from patrol_task
            where status in ('QUEUED','PREPARING','SNAPSHOTTING','PLANNING','WAITING_APPROVAL','EXECUTING','JUDGING')
            """)
    long countActive();

    @Select("""
            select count(1) > 0 from patrol_task
            where scene_id = #{sceneId}
              and status in ('QUEUED','PREPARING','SNAPSHOTTING','PLANNING','WAITING_APPROVAL','EXECUTING','JUDGING')
            """)
    boolean existsActiveBySceneId(@Param("sceneId") Long sceneId);

    @Select("""
            select\s""" + COLUMNS + """
            from patrol_task
            where status in ('FINISHED','FAILED','CANCELED')
              and retention_until is not null
              and retention_until <= #{now}
            order by retention_until asc
            limit #{limit}
            """)
    @ResultMap("PatrolTaskResultMap")
    List<PatrolTaskEntity> findRetentionCandidates(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 服务重启后，内存中的执行线程已丢失：把非审批态的活动任务标记为中断，
     * 避免场景被永久占用；待审批任务保留，重启后仍可批准恢复。
     */
    @Update("""
            update patrol_task
            set status = 'FAILED',
                current_stage = 'FINISHED',
                result_message = '服务重启，任务执行中断',
                finished_at = now(),
                retention_until = date_add(now(), interval #{retentionDays} day)
            where status in ('QUEUED','PREPARING','SNAPSHOTTING','PLANNING','EXECUTING','JUDGING')
            """)
    int failInterruptedTasks(@Param("retentionDays") int retentionDays);

    @Select("""
            select\s""" + COLUMNS + """
            from patrol_task
            where status in ('QUEUED','PREPARING','SNAPSHOTTING','PLANNING','WAITING_APPROVAL','EXECUTING','JUDGING')
              and created_at < #{before}
            limit 20
            """)
    @ResultMap("PatrolTaskResultMap")
    List<PatrolTaskEntity> findStaleActiveTasks(@Param("before") LocalDateTime before);

    @Delete("delete from patrol_task where id = #{id}")
    int deleteById(@Param("id") Long id);
}
