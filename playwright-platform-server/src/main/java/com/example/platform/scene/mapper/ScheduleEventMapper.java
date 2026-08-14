package com.example.platform.scene.mapper;

import com.example.platform.scene.model.ScheduleEventEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ScheduleEventMapper {

    String SELECT_COLUMNS = """
            id, space_id, scene_id, planned_fire_at, status, schedule_type, trace_id, session_id,
            task_id, trigger_reason, user_message, error_message, failure_category,
            retry_count, next_retry_at, last_error_at, created_at, updated_at
            """;

    @Insert("""
            insert into schedule_event (
                space_id, scene_id, planned_fire_at, status, schedule_type, trace_id, session_id,
                task_id, trigger_reason, user_message, error_message, failure_category,
                retry_count, next_retry_at, last_error_at
            ) values (
                #{spaceId}, #{sceneId}, #{plannedFireAt}, #{status}, #{scheduleType}, #{traceId}, #{sessionId},
                #{taskId}, #{triggerReason}, #{userMessage}, #{errorMessage}, #{failureCategory},
                #{retryCount}, #{nextRetryAt}, #{lastErrorAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ScheduleEventEntity entity);

    @Select("""
            select id, space_id, scene_id, planned_fire_at, status, schedule_type, trace_id, session_id,
                   task_id, trigger_reason, user_message, error_message, failure_category,
                   retry_count, next_retry_at, last_error_at, created_at, updated_at
            from schedule_event
            where scene_id = #{sceneId}
              and planned_fire_at = #{plannedFireAt}
            """)
    @Results(id = "ScheduleEventResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "spaceId", column = "space_id"),
            @Result(property = "sceneId", column = "scene_id"),
            @Result(property = "plannedFireAt", column = "planned_fire_at"),
            @Result(property = "status", column = "status"),
            @Result(property = "scheduleType", column = "schedule_type"),
            @Result(property = "traceId", column = "trace_id"),
            @Result(property = "sessionId", column = "session_id"),
            @Result(property = "taskId", column = "task_id"),
            @Result(property = "triggerReason", column = "trigger_reason"),
            @Result(property = "userMessage", column = "user_message"),
            @Result(property = "errorMessage", column = "error_message"),
            @Result(property = "failureCategory", column = "failure_category"),
            @Result(property = "retryCount", column = "retry_count"),
            @Result(property = "nextRetryAt", column = "next_retry_at"),
            @Result(property = "lastErrorAt", column = "last_error_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<ScheduleEventEntity> findBySceneIdAndPlannedFireAt(@Param("sceneId") Long sceneId,
                                                                @Param("plannedFireAt") LocalDateTime plannedFireAt);

    @Select("""
            select id, space_id, scene_id, planned_fire_at, status, schedule_type, trace_id, session_id,
                   task_id, trigger_reason, user_message, error_message, failure_category,
                   retry_count, next_retry_at, last_error_at, created_at, updated_at
            from schedule_event
            where id = #{id}
            """)
    @org.apache.ibatis.annotations.ResultMap("ScheduleEventResultMap")
    Optional<ScheduleEventEntity> findById(@Param("id") Long id);

    @Update("""
            update schedule_event
            set space_id = #{spaceId},
                status = #{status},
                schedule_type = #{scheduleType},
                trace_id = #{traceId},
                session_id = #{sessionId},
                task_id = #{taskId},
                trigger_reason = #{triggerReason},
                user_message = #{userMessage},
                error_message = #{errorMessage},
                failure_category = #{failureCategory},
                retry_count = #{retryCount},
                next_retry_at = #{nextRetryAt},
                last_error_at = #{lastErrorAt}
            where id = #{id}
            """)
    int update(ScheduleEventEntity entity);

    @Update("""
            update schedule_event
            set status = #{status},
                error_message = #{errorMessage},
                failure_category = #{failureCategory},
                last_error_at = #{lastErrorAt}
            where id = #{id}
            """)
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("errorMessage") String errorMessage,
                     @Param("failureCategory") String failureCategory,
                     @Param("lastErrorAt") LocalDateTime lastErrorAt);

    @Update("""
            update schedule_event
            set status = 'RETRYING'
            where id = #{id}
              and task_id is null
              and status in ('FAILED', 'ABANDONED')
            """)
    int tryStartRetry(@Param("id") Long id);

    @Select("""
            select /*+ INDEX(schedule_event idx_schedule_event_retry) */
                   id, space_id, scene_id, planned_fire_at, status, schedule_type, trace_id, session_id,
                   task_id, trigger_reason, user_message, error_message, failure_category,
                   retry_count, next_retry_at, last_error_at, created_at, updated_at
            from schedule_event
            where status = 'FAILED'
              and task_id is null
              and retry_count < #{maxRetries}
              and next_retry_at <= #{now}
            order by created_at asc, id asc
            limit #{limit}
            """)
    @org.apache.ibatis.annotations.ResultMap("ScheduleEventResultMap")
    List<ScheduleEventEntity> findRetryableFailedEvents(@Param("limit") int limit,
                                                        @Param("now") LocalDateTime now,
                                                        @Param("maxRetries") int maxRetries);

    // ============ 通用筛选查询（支持 scheduleType 筛选，用于前端调度事件列表） ============

    @Select("""
            <script>
            select /*+ INDEX(schedule_event idx_schedule_event_type_status_updated) */
                   id, space_id, scene_id, planned_fire_at, status, schedule_type, trace_id, session_id,
                   task_id, trigger_reason, user_message, error_message, failure_category,
                   retry_count, next_retry_at, last_error_at, created_at, updated_at
            from schedule_event
            <where>
              and space_id = #{spaceId}
              <if test='scheduleType != null and scheduleType != ""'>
                and schedule_type = #{scheduleType}
              </if>
              <choose>
                <when test='statuses != null and !statuses.isEmpty()'>
                  and status in
                  <foreach collection='statuses' item='status' open='(' separator=',' close=')'>
                    #{status}
                  </foreach>
                </when>
              </choose>
              <if test='sceneId != null'>
                and scene_id = #{sceneId}
              </if>
            </where>
            order by updated_at desc, id desc
            limit #{limit} offset #{offset}
            </script>
            """)
    @org.apache.ibatis.annotations.ResultMap("ScheduleEventResultMap")
    List<ScheduleEventEntity> findEventsPageWithFilter(@Param("spaceId") Long spaceId,
                                                       @Param("sceneId") Long sceneId,
                                                       @Param("statuses") List<String> statuses,
                                                       @Param("scheduleType") String scheduleType,
                                                       @Param("limit") int limit,
                                                       @Param("offset") int offset);

    @Select("""
            <script>
            select count(1)
            from schedule_event
            <where>
              and space_id = #{spaceId}
              <if test='scheduleType != null and scheduleType != ""'>
                and schedule_type = #{scheduleType}
              </if>
              <choose>
                <when test='statuses != null and !statuses.isEmpty()'>
                  and status in
                  <foreach collection='statuses' item='status' open='(' separator=',' close=')'>
                    #{status}
                  </foreach>
                </when>
              </choose>
              <if test='sceneId != null'>
                and scene_id = #{sceneId}
              </if>
            </where>
            </script>
            """)
    long countEventsWithFilter(@Param("spaceId") Long spaceId,
                               @Param("sceneId") Long sceneId,
                               @Param("statuses") List<String> statuses,
                               @Param("scheduleType") String scheduleType);

    // ============ 带场景名称 JOIN 的筛选查询（v2，支持 sceneName/traceId 过滤） ============

    @Select("""
            <script>
            select se.id, se.space_id, se.scene_id, se.planned_fire_at, se.status, se.schedule_type,
                   se.trace_id, se.session_id, se.task_id, se.trigger_reason, se.user_message,
                   se.error_message, se.failure_category, se.retry_count, se.next_retry_at,
                   se.last_error_at, se.created_at, se.updated_at,
                   sc.name as scene_name
            from schedule_event se
            left join scene sc on sc.id = se.scene_id
            <where>
              and se.space_id = #{spaceId}
              <if test='scheduleType != null and scheduleType != ""'>
                and se.schedule_type = #{scheduleType}
              </if>
              <if test='sceneId != null'>
                and se.scene_id = #{sceneId}
              </if>
              <if test='sceneNameLike != null and sceneNameLike != ""'>
                and lower(sc.name) like lower(concat('%', #{sceneNameLike}, '%'))
              </if>
              <if test='traceId != null and traceId != ""'>
                and se.trace_id = #{traceId}
              </if>
            </where>
            order by se.updated_at desc, se.id desc
            limit #{limit} offset #{offset}
            </script>
            """)
    @Results(id = "ScheduleEventWithSceneNameMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "spaceId", column = "space_id"),
            @Result(property = "sceneId", column = "scene_id"),
            @Result(property = "plannedFireAt", column = "planned_fire_at"),
            @Result(property = "status", column = "status"),
            @Result(property = "scheduleType", column = "schedule_type"),
            @Result(property = "traceId", column = "trace_id"),
            @Result(property = "sessionId", column = "session_id"),
            @Result(property = "taskId", column = "task_id"),
            @Result(property = "triggerReason", column = "trigger_reason"),
            @Result(property = "userMessage", column = "user_message"),
            @Result(property = "errorMessage", column = "error_message"),
            @Result(property = "failureCategory", column = "failure_category"),
            @Result(property = "retryCount", column = "retry_count"),
            @Result(property = "nextRetryAt", column = "next_retry_at"),
            @Result(property = "lastErrorAt", column = "last_error_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at"),
    })
    List<ScheduleEventEntity> findEventsPageV2(@Param("spaceId") Long spaceId,
                                               @Param("sceneId") Long sceneId,
                                               @Param("scheduleType") String scheduleType,
                                               @Param("sceneNameLike") String sceneNameLike,
                                               @Param("traceId") String traceId,
                                               @Param("limit") int limit,
                                               @Param("offset") int offset);

    @Select("""
            <script>
            select count(1)
            from schedule_event se
            left join scene sc on sc.id = se.scene_id
            <where>
              and se.space_id = #{spaceId}
              <if test='scheduleType != null and scheduleType != ""'>
                and se.schedule_type = #{scheduleType}
              </if>
              <if test='sceneId != null'>
                and se.scene_id = #{sceneId}
              </if>
              <if test='sceneNameLike != null and sceneNameLike != ""'>
                and lower(sc.name) like lower(concat('%', #{sceneNameLike}, '%'))
              </if>
              <if test='traceId != null and traceId != ""'>
                and se.trace_id = #{traceId}
              </if>
            </where>
            </script>
            """)
    long countEventsV2(@Param("spaceId") Long spaceId,
                       @Param("sceneId") Long sceneId,
                       @Param("scheduleType") String scheduleType,
                       @Param("sceneNameLike") String sceneNameLike,
                       @Param("traceId") String traceId);

    @Select("""
            <script>
            select se.id, sc.name as scene_name
            from schedule_event se
            left join scene sc on sc.id = se.scene_id
            where se.id in
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>
              #{id}
            </foreach>
            </script>
            """)
    List<java.util.Map<String, Object>> findSceneNamesForIds(@Param("ids") List<Long> ids);

    // ============ 旧接口保留（向后兼容） ============

    @Select("""
            <script>
            select count(1)
            from schedule_event
            <where>
              <choose>
                <when test='statuses != null and !statuses.isEmpty()'>
                  status in
                  <foreach collection='statuses' item='status' open='(' separator=',' close=')'>
                    #{status}
                  </foreach>
                </when>
                <otherwise>1 = 0</otherwise>
              </choose>
              <if test='sceneId != null'>
                and scene_id = #{sceneId}
              </if>
            </where>
            </script>
            """)
    long countIssueEvents(@Param("statuses") List<String> statuses, @Param("sceneId") Long sceneId);

    @Select("""
            <script>
            select /*+ INDEX(schedule_event idx_schedule_event_issue_scene_status_updated) */ count(1)
            from schedule_event
            <where>
              <choose>
                <when test='statuses != null and !statuses.isEmpty()'>
                  status in
                  <foreach collection='statuses' item='status' open='(' separator=',' close=')'>
                    #{status}
                  </foreach>
                </when>
                <otherwise>1 = 0</otherwise>
              </choose>
              and space_id = #{spaceId}
            </where>
            </script>
            """)
    long countIssueEventsBySpaceId(@Param("statuses") List<String> statuses, @Param("spaceId") Long spaceId);

    @Select("""
            <script>
            select /*+ INDEX(schedule_event idx_schedule_event_issue_scene_status_updated) */ count(1)
            from schedule_event
            <where>
              <choose>
                <when test='statuses != null and !statuses.isEmpty()'>
                  status in
                  <foreach collection='statuses' item='status' open='(' separator=',' close=')'>
                    #{status}
                  </foreach>
                </when>
                <otherwise>1 = 0</otherwise>
              </choose>
              and space_id = #{spaceId}
              and scene_id = #{sceneId}
            </where>
            </script>
            """)
    long countIssueEventsBySpaceIdAndSceneId(@Param("statuses") List<String> statuses,
                                             @Param("spaceId") Long spaceId,
                                             @Param("sceneId") Long sceneId);

    @Select("""
            <script>
            select id, space_id, scene_id, planned_fire_at, status, schedule_type, trace_id, session_id,
                   task_id, trigger_reason, user_message, error_message, failure_category,
                   retry_count, next_retry_at, last_error_at, created_at, updated_at
            from schedule_event
            <where>
              <choose>
                <when test='statuses != null and !statuses.isEmpty()'>
                  status in
                  <foreach collection='statuses' item='status' open='(' separator=',' close=')'>
                    #{status}
                  </foreach>
                </when>
                <otherwise>1 = 0</otherwise>
              </choose>
              <if test='sceneId != null'>
                and scene_id = #{sceneId}
              </if>
            </where>
            order by updated_at desc, id desc
            limit #{limit} offset #{offset}
            </script>
            """)
    @org.apache.ibatis.annotations.ResultMap("ScheduleEventResultMap")
    List<ScheduleEventEntity> findIssueEventsPage(@Param("statuses") List<String> statuses,
                                                  @Param("sceneId") Long sceneId,
                                                  @Param("limit") int limit,
                                                  @Param("offset") int offset);

    @Select("""
            <script>
            select /*+ INDEX(schedule_event idx_schedule_event_issue_scene_status_updated) */
                   id, space_id, scene_id, planned_fire_at, status, schedule_type, trace_id, session_id,
                   task_id, trigger_reason, user_message, error_message, failure_category,
                   retry_count, next_retry_at, last_error_at, created_at, updated_at
            from schedule_event
            <where>
              <choose>
                <when test='statuses != null and !statuses.isEmpty()'>
                  status in
                  <foreach collection='statuses' item='status' open='(' separator=',' close=')'>
                    #{status}
                  </foreach>
                </when>
                <otherwise>1 = 0</otherwise>
              </choose>
              and space_id = #{spaceId}
            </where>
            order by updated_at desc, id desc
            limit #{limit} offset #{offset}
            </script>
            """)
    @org.apache.ibatis.annotations.ResultMap("ScheduleEventResultMap")
    List<ScheduleEventEntity> findIssueEventsPageBySpaceId(@Param("statuses") List<String> statuses,
                                                           @Param("spaceId") Long spaceId,
                                                           @Param("limit") int limit,
                                                           @Param("offset") int offset);

    @Select("""
            <script>
            select /*+ INDEX(schedule_event idx_schedule_event_issue_scene_status_updated) */
                   id, space_id, scene_id, planned_fire_at, status, schedule_type, trace_id, session_id,
                   task_id, trigger_reason, user_message, error_message, failure_category,
                   retry_count, next_retry_at, last_error_at, created_at, updated_at
            from schedule_event
            <where>
              <choose>
                <when test='statuses != null and !statuses.isEmpty()'>
                  status in
                  <foreach collection='statuses' item='status' open='(' separator=',' close=')'>
                    #{status}
                  </foreach>
                </when>
                <otherwise>1 = 0</otherwise>
              </choose>
              and space_id = #{spaceId}
              and scene_id = #{sceneId}
            </where>
            order by updated_at desc, id desc
            limit #{limit} offset #{offset}
            </script>
            """)
    @org.apache.ibatis.annotations.ResultMap("ScheduleEventResultMap")
    List<ScheduleEventEntity> findIssueEventsPageBySpaceIdAndSceneId(@Param("statuses") List<String> statuses,
                                                                     @Param("spaceId") Long spaceId,
                                                                     @Param("sceneId") Long sceneId,
                                                                     @Param("limit") int limit,
                                                                     @Param("offset") int offset);

    @Delete("""
            delete from schedule_event
            where scene_id = #{sceneId}
            """)
    int deleteAllBySceneId(@Param("sceneId") Long sceneId);
}
