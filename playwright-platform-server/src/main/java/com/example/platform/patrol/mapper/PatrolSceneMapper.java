package com.example.platform.patrol.mapper;

import com.example.platform.patrol.model.PatrolSceneEntity;
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
public interface PatrolSceneMapper {

    String COLUMNS = """
            id, space_id, name, description, start_url, browser, headless, auth_type,
            auth_cookie_encrypted, auth_token_encrypted, auth_token_header, auth_local_storage_encrypted,
            plan_model_id, judge_model_id, resolve_model_id, supplement_model_id, supplements_json, budget_duration_minutes,
            visible_text_limit, scroll_scan, record_video, auto_approve_write, auto_approve_destructive,
            schedule_enabled, cron_expression, next_run_at, last_run_at, last_task_id, last_task_status,
            created_by, created_at, updated_at
            """;

    @Insert("""
            insert into patrol_scene (
                space_id, name, description, start_url, browser, headless, auth_type,
                auth_cookie_encrypted, auth_token_encrypted, auth_token_header, auth_local_storage_encrypted,
                plan_model_id, judge_model_id, resolve_model_id, supplement_model_id, supplements_json, budget_duration_minutes,
                visible_text_limit, scroll_scan, record_video, auto_approve_write, auto_approve_destructive,
                schedule_enabled, cron_expression, next_run_at, created_by, created_at
            ) values (
                #{spaceId}, #{name}, #{description}, #{startUrl}, #{browser}, #{headless}, #{authType},
                #{authCookieEncrypted}, #{authTokenEncrypted}, #{authTokenHeader}, #{authLocalStorageEncrypted},
                #{planModelId}, #{judgeModelId}, #{resolveModelId}, #{supplementModelId}, #{supplementsJson}, #{budgetDurationMinutes},
                #{visibleTextLimit}, #{scrollScan}, #{recordVideo}, #{autoApproveWrite}, #{autoApproveDestructive},
                #{scheduleEnabled}, #{cronExpression}, #{nextRunAt}, #{createdBy}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PatrolSceneEntity entity);

    @Update("""
            update patrol_scene
            set name = #{name},
                description = #{description},
                start_url = #{startUrl},
                browser = #{browser},
                headless = #{headless},
                auth_type = #{authType},
                auth_cookie_encrypted = #{authCookieEncrypted},
                auth_token_encrypted = #{authTokenEncrypted},
                auth_token_header = #{authTokenHeader},
                auth_local_storage_encrypted = #{authLocalStorageEncrypted},
                plan_model_id = #{planModelId},
                judge_model_id = #{judgeModelId},
                resolve_model_id = #{resolveModelId},
                supplement_model_id = #{supplementModelId},
                supplements_json = #{supplementsJson},
                budget_duration_minutes = #{budgetDurationMinutes},
                visible_text_limit = #{visibleTextLimit},
                scroll_scan = #{scrollScan},
                record_video = #{recordVideo},
                auto_approve_write = #{autoApproveWrite},
                auto_approve_destructive = #{autoApproveDestructive},
                schedule_enabled = #{scheduleEnabled},
                cron_expression = #{cronExpression},
                next_run_at = #{nextRunAt}
            where id = #{id} and space_id = #{spaceId}
            """)
    int update(PatrolSceneEntity entity);

    @Select("select " + COLUMNS + " from patrol_scene where id = #{id} and space_id = #{spaceId}")
    @Results(id = "PatrolSceneResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "spaceId", column = "space_id"),
            @Result(property = "name", column = "name"),
            @Result(property = "description", column = "description"),
            @Result(property = "startUrl", column = "start_url"),
            @Result(property = "browser", column = "browser"),
            @Result(property = "headless", column = "headless"),
            @Result(property = "authType", column = "auth_type"),
            @Result(property = "authCookieEncrypted", column = "auth_cookie_encrypted"),
            @Result(property = "authTokenEncrypted", column = "auth_token_encrypted"),
            @Result(property = "authTokenHeader", column = "auth_token_header"),
            @Result(property = "authLocalStorageEncrypted", column = "auth_local_storage_encrypted"),
            @Result(property = "planModelId", column = "plan_model_id"),
            @Result(property = "judgeModelId", column = "judge_model_id"),
            @Result(property = "resolveModelId", column = "resolve_model_id"),
            @Result(property = "supplementModelId", column = "supplement_model_id"),
            @Result(property = "supplementsJson", column = "supplements_json"),
            @Result(property = "budgetDurationMinutes", column = "budget_duration_minutes"),
            @Result(property = "visibleTextLimit", column = "visible_text_limit"),
            @Result(property = "scrollScan", column = "scroll_scan"),
            @Result(property = "recordVideo", column = "record_video"),
            @Result(property = "autoApproveWrite", column = "auto_approve_write"),
            @Result(property = "autoApproveDestructive", column = "auto_approve_destructive"),
            @Result(property = "scheduleEnabled", column = "schedule_enabled"),
            @Result(property = "cronExpression", column = "cron_expression"),
            @Result(property = "nextRunAt", column = "next_run_at"),
            @Result(property = "lastRunAt", column = "last_run_at"),
            @Result(property = "lastTaskId", column = "last_task_id"),
            @Result(property = "lastTaskStatus", column = "last_task_status"),
            @Result(property = "createdBy", column = "created_by"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<PatrolSceneEntity> findByIdAndSpaceId(@Param("id") Long id, @Param("spaceId") Long spaceId);

    @Select("select " + COLUMNS + " from patrol_scene where id = #{id}")
    @ResultMap("PatrolSceneResultMap")
    Optional<PatrolSceneEntity> findById(@Param("id") Long id);

    @Select("""
            select\s""" + COLUMNS + """
            from patrol_scene
            where space_id = #{spaceId}
            order by created_at asc, id asc
            limit #{limit} offset #{offset}
            """)
    @ResultMap("PatrolSceneResultMap")
    List<PatrolSceneEntity> findPageBySpaceId(@Param("spaceId") Long spaceId,
                                              @Param("limit") int limit,
                                              @Param("offset") int offset);

    @Select("select count(1) from patrol_scene where space_id = #{spaceId}")
    long countBySpaceId(@Param("spaceId") Long spaceId);

    @Select("""
            select count(1) > 0
            from patrol_scene
            where space_id = #{spaceId} and name = #{name}
              and (#{excludeId} is null or id <> #{excludeId})
            """)
    boolean existsByName(@Param("spaceId") Long spaceId, @Param("name") String name,
                         @Param("excludeId") Long excludeId);

    @Select("""
            select\s""" + COLUMNS + """
            from patrol_scene
            where schedule_enabled = 1 and next_run_at is not null and next_run_at <= #{now}
            order by next_run_at asc
            limit 20
            """)
    @ResultMap("PatrolSceneResultMap")
    List<PatrolSceneEntity> findDueScenes(@Param("now") LocalDateTime now);

    @Update("""
            update patrol_scene
            set next_run_at = #{nextRunAt}, last_run_at = #{lastRunAt},
                last_task_id = #{lastTaskId}, last_task_status = #{lastTaskStatus}
            where id = #{id}
            """)
    int updateScheduleState(@Param("id") Long id,
                            @Param("nextRunAt") LocalDateTime nextRunAt,
                            @Param("lastRunAt") LocalDateTime lastRunAt,
                            @Param("lastTaskId") Long lastTaskId,
                            @Param("lastTaskStatus") String lastTaskStatus);

    @Update("""
            update patrol_scene
            set last_run_at = #{lastRunAt}, last_task_id = #{lastTaskId}, last_task_status = #{lastTaskStatus}
            where id = #{id}
            """)
    int updateLastTask(@Param("id") Long id,
                       @Param("lastRunAt") LocalDateTime lastRunAt,
                       @Param("lastTaskId") Long lastTaskId,
                       @Param("lastTaskStatus") String lastTaskStatus);

    @Update("update patrol_scene set next_run_at = #{nextRunAt} where id = #{id}")
    int updateNextRunAt(@Param("id") Long id, @Param("nextRunAt") LocalDateTime nextRunAt);

    @Delete("delete from patrol_scene where id = #{id} and space_id = #{spaceId}")
    int deleteByIdAndSpaceId(@Param("id") Long id, @Param("spaceId") Long spaceId);
}
