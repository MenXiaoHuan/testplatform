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

/**
 * 场景 Mapper —— 基于注解的 MyBatis 数据访问层，负责场景表的 CRUD 与调度查询。
 *
 * <p>核心职责：
 * <ul>
 *   <li>场景 CRUD：{@link #insert}、{@link #update}、{@link #findById}、{@link #deleteById}</li>
 *   <li>分页查询：{@link #findPage}、{@link #findPageBySpaceId}、{@link #countBySpaceId}</li>
 *   <li>唯一性校验：{@link #existsByNameIgnoreCase}、{@link #existsByNameIgnoreCaseAndIdNot}</li>
 *   <li>仓库关联查询：{@link #findAllByRepoId}、{@link #deleteAllByRepoId}</li>
 *   <li>调度相关查询：{@link #findDueScheduledScenes}、{@link #findAllByScheduleEnabledTrue} 等</li>
 * </ul>
 *
 * <p>场景写入由服务层事务包裹，本 Mapper 仅负责 SQL 编写与列映射。
 */
@Mapper
public interface SceneMapper {
    /** 场景全字段列常量，供多处 SELECT 语句复用。 */
    String SCENE_COLUMNS = """
            id, space_id, repo_id, name, description, branch, test_selector_type, test_selector_value,
            project_name, browser, env_json, run_command, schedule_enabled, cron_expression,
            next_run_at, last_run_at, last_task_status, created_at, updated_at
            """;

    /** 新增场景，使用自增主键回填 ID。 */
    @Insert("""
            insert into scene (
                space_id, repo_id, name, description, branch, test_selector_type, test_selector_value,
                project_name, browser, env_json, run_command, schedule_enabled, cron_expression,
                next_run_at, last_run_at, last_task_status
            ) values (
                #{spaceId}, #{repoId}, #{name}, #{description}, #{branch}, #{testSelectorType}, #{testSelectorValue},
                #{projectName}, #{browser}, #{envJson}, #{runCommand}, #{scheduleEnabled}, #{cronExpression},
                #{nextRunAt}, #{lastRunAt}, #{lastTaskStatus}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SceneEntity entity);

    @Update("""
            update scene
            set space_id = #{spaceId},
                repo_id = #{repoId},
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
    /** 根据 ID 全量更新场景字段。 */
    int update(SceneEntity entity);

    @Select("""
            select
            """ + SCENE_COLUMNS + """
            from scene
            where id = #{id}
            """)
    @Results(id = "SceneResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "spaceId", column = "space_id"),
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
    /** 按 ID 查询场景（不限空间，内部使用）。 */
    Optional<SceneEntity> findById(@Param("id") Long id);

    @Select("""
            select
            """ + SCENE_COLUMNS + """
            from scene
            where id = #{id}
              and space_id = #{spaceId}
            """)
    @ResultMap("SceneResultMap")
    /** 按 ID + 空间 ID 查询场景（数据隔离）。 */
    Optional<SceneEntity> findByIdAndSpaceId(@Param("id") Long id, @Param("spaceId") Long spaceId);

    @Select("""
            select
            """ + SCENE_COLUMNS + """
            from scene
            where id = #{id}
            for update
            """)
    @ResultMap("SceneResultMap")
    /** 按 ID 加行锁查询场景（用于调度触发时的并发控制）。 */
    Optional<SceneEntity> findByIdForUpdate(@Param("id") Long id);

    @Select("""
            select
            """ + SCENE_COLUMNS + """
            from scene
            order by updated_at desc, id desc
            limit #{limit} offset #{offset}
            """)
    @ResultMap("SceneResultMap")
    /** 全表分页查询（按更新时间倒序）。 */
    List<SceneEntity> findPage(@Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            select
            """ + SCENE_COLUMNS + """
            from scene
            where space_id = #{spaceId}
            order by updated_at desc, id desc
            limit #{limit} offset #{offset}
            """)
    @ResultMap("SceneResultMap")
    /** 按空间分页查询。 */
    List<SceneEntity> findPageBySpaceId(@Param("spaceId") Long spaceId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            select count(1)
            from scene
            """)
    /** 统计全表场景数量。 */
    long countAll();

    @Select("""
            select count(1)
            from scene
            where space_id = #{spaceId}
            """)
    /** 统计指定空间的场景数量。 */
    long countBySpaceId(@Param("spaceId") Long spaceId);

    @Select("""
            select count(1) > 0
            from scene
            where lower(name) = lower(#{name})
            """)
    /** 按名称忽略大小写判断是否存在（新建时校验）。 */
    boolean existsByNameIgnoreCase(@Param("name") String name);

    @Select("""
            select count(1) > 0
            from scene
            where lower(name) = lower(#{name})
              and id <> #{id}
            """)
    /** 按名称忽略大小写判断是否存在（排除指定 ID，更新时校验）。 */
    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("id") Long id);

    @Select("""
            select
            """ + SCENE_COLUMNS + """
            from scene
            where repo_id = #{repoId}
            order by id asc
            """)
    @ResultMap("SceneResultMap")
    /** 按仓库 ID 查询所有场景。 */
    List<SceneEntity> findAllByRepoId(@Param("repoId") Long repoId);

    @Select("""
            select
            """ + SCENE_COLUMNS + """
            from scene
            where space_id = #{spaceId}
            order by id asc
            """)
    @ResultMap("SceneResultMap")
    /** 按空间 ID 查询所有场景。 */
    List<SceneEntity> findAllBySpaceId(@Param("spaceId") Long spaceId);

    @Select("""
            select
            """ + SCENE_COLUMNS + """
            from scene
            where schedule_enabled = true
            order by id asc
            """)
    @ResultMap("SceneResultMap")
    /** 查询所有启用调度的场景。 */
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
    /** 查询到期需要触发的调度场景（next_run_at <= now）。 */
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
    /** 查询启用调度但尚未设置 next_run_at 的遗留场景。 */
    List<SceneEntity> findAllByScheduleEnabledTrueAndNextRunAtIsNullOrderByIdAsc();

    @Delete("""
            delete from scene
            where id = #{id}
            """)
    /** 按 ID 删除场景。 */
    int deleteById(@Param("id") Long id);

    @Delete("""
            delete from scene
            where repo_id = #{repoId}
            """)
    /** 按仓库 ID 删除所有关联场景。 */
    int deleteAllByRepoId(@Param("repoId") Long repoId);
}
