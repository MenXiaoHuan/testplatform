package com.example.platform.scene.mapper;

import com.example.platform.scene.model.ScheduleEventEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    @Insert("""
            insert into schedule_event (
                space_id, scene_id, planned_fire_at, status, task_id, trigger_reason, error_message, failure_category, retry_count, next_retry_at, last_error_at
            ) values (
                #{spaceId}, #{sceneId}, #{plannedFireAt}, #{status}, #{taskId}, #{triggerReason}, #{errorMessage}, #{failureCategory},
                #{retryCount}, #{nextRetryAt}, #{lastErrorAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ScheduleEventEntity entity);

    @Select("""
            select id, space_id, scene_id, planned_fire_at, status, task_id, trigger_reason, error_message,
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
            @Result(property = "taskId", column = "task_id"),
            @Result(property = "triggerReason", column = "trigger_reason"),
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
            select id, space_id, scene_id, planned_fire_at, status, task_id, trigger_reason, error_message,
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
                task_id = #{taskId},
                trigger_reason = #{triggerReason},
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
            set status = 'RETRYING'
            where id = #{id}
              and task_id is null
              and status in ('FAILED', 'ABANDONED')
            """)
    int tryStartRetry(@Param("id") Long id);

    @Select("""
            select /*+ INDEX(schedule_event idx_schedule_event_retry) */
                   id, space_id, scene_id, planned_fire_at, status, task_id, trigger_reason, error_message,
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
            select id, space_id, scene_id, planned_fire_at, status, task_id, trigger_reason, error_message,
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
                   id, space_id, scene_id, planned_fire_at, status, task_id, trigger_reason, error_message,
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
                   id, space_id, scene_id, planned_fire_at, status, task_id, trigger_reason, error_message,
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
}
