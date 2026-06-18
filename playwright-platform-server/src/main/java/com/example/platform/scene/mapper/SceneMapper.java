package com.example.platform.scene.mapper;

import com.example.platform.scene.model.SceneEntity;
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
public interface SceneMapper {
    String SCENE_COLUMNS = """
            id, repo_id, name, description, branch, test_selector_type, test_selector_value,
            project_name, browser, env_json, run_command, schedule_enabled, cron_expression,
            next_run_at, last_run_at, last_task_status, created_at, updated_at
            """;

    @Insert("""
            insert into scene (
                repo_id, name, description, branch, test_selector_type, test_selector_value,
                project_name, browser, env_json, run_command, schedule_enabled, cron_expression,
                next_run_at, last_run_at, last_task_status
            ) values (
                #{repoId}, #{name}, #{description}, #{branch}, #{testSelectorType}, #{testSelectorValue},
                #{projectName}, #{browser}, #{envJson}, #{runCommand}, #{scheduleEnabled}, #{cronExpression},
                #{nextRunAt}, #{lastRunAt}, #{lastTaskStatus}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SceneEntity entity);

    @Update("""
            update scene
            set repo_id = #{repoId},
                name = #{name},
                description = #{description},
                branch = #{branch},
                test_selector_type = #{testSelectorType},
                test_selector_value = #{testSelectorValue},
                project_name = #{projectName},
                browser = #{browser},
                env_json = #{envJson},
                run_command = #{runCommand},
                schedule_enabled = #{scheduleEnabled},
                cron_expression = #{cronExpression},
                next_run_at = #{nextRunAt},
                last_run_at = #{lastRunAt},
                last_task_status = #{lastTaskStatus}
            where id = #{id}
            """)
    int update(SceneEntity entity);

    @Select("""
            select
            """ + SCENE_COLUMNS + """
            from scene
            where id = #{id}
            """)
    @Results(id = "SceneResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "repoId", column = "repo_id"),
            @Result(property = "name", column = "name"),
            @Result(property = "description", column = "description"),
            @Result(property = "branch", column = "branch"),
            @Result(property = "testSelectorType", column = "test_selector_type"),
            @Result(property = "testSelectorValue", column = "test_selector_value"),
            @Result(property = "projectName", column = "project_name"),
            @Result(property = "browser", column = "browser"),
            @Result(property = "envJson", column = "env_json"),
            @Result(property = "runCommand", column = "run_command"),
            @Result(property = "scheduleEnabled", column = "schedule_enabled"),
            @Result(property = "cronExpression", column = "cron_expression"),
            @Result(property = "nextRunAt", column = "next_run_at"),
            @Result(property = "lastRunAt", column = "last_run_at"),
            @Result(property = "lastTaskStatus", column = "last_task_status"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<SceneEntity> findById(@Param("id") Long id);

    @Select("""
            select
            """ + SCENE_COLUMNS + """
            from scene
            where id = #{id}
            for update
            """)
    @ResultMap("SceneResultMap")
    Optional<SceneEntity> findByIdForUpdate(@Param("id") Long id);

    @Select("""
            select
            """ + SCENE_COLUMNS + """
            from scene
            order by updated_at desc, id desc
            limit #{limit} offset #{offset}
            """)
    @ResultMap("SceneResultMap")
    List<SceneEntity> findPage(@Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            select count(1)
            from scene
            """)
    long countAll();

    @Select("""
            select count(1) > 0
            from scene
            where lower(name) = lower(#{name})
            """)
    boolean existsByNameIgnoreCase(@Param("name") String name);

    @Select("""
            select count(1) > 0
            from scene
            where lower(name) = lower(#{name})
              and id <> #{id}
            """)
    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("id") Long id);

    @Select("""
            select
            """ + SCENE_COLUMNS + """
            from scene
            where repo_id = #{repoId}
            order by id asc
            """)
    @ResultMap("SceneResultMap")
    List<SceneEntity> findAllByRepoId(@Param("repoId") Long repoId);

    @Select("""
            select
            """ + SCENE_COLUMNS + """
            from scene
            where schedule_enabled = true
            order by id asc
            """)
    @ResultMap("SceneResultMap")
    List<SceneEntity> findAllByScheduleEnabledTrue();

    @Select("""
            select
            """ + SCENE_COLUMNS + """
            from scene
            where schedule_enabled = true
              and cron_expression is not null
              and cron_expression <> ''
              and next_run_at is not null
              and next_run_at <= #{now}
            order by next_run_at asc, id asc
            """)
    @ResultMap("SceneResultMap")
    List<SceneEntity> findDueScheduledScenes(@Param("now") LocalDateTime now);

    @Select("""
            select
            """ + SCENE_COLUMNS + """
            from scene
            where schedule_enabled = true
              and next_run_at is null
            order by id asc
            """)
    @ResultMap("SceneResultMap")
    List<SceneEntity> findAllByScheduleEnabledTrueAndNextRunAtIsNullOrderByIdAsc();

    @Delete("""
            delete from scene
            where id = #{id}
            """)
    int deleteById(@Param("id") Long id);

    @Delete("""
            delete from scene
            where repo_id = #{repoId}
            """)
    int deleteAllByRepoId(@Param("repoId") Long repoId);
}
