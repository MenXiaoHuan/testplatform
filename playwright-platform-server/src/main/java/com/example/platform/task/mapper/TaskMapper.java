package com.example.platform.task.mapper;

import com.example.platform.task.model.TaskEntity;
import java.util.Collection;
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
 * 任务数据访问接口（MyBatis Mapper）。
 *
 * <p>核心职责：
 * <ul>
 *   <li>提供任务表的 CRUD 操作</li>
 *   <li>支持多种查询维度：按ID、场景ID、空间ID、仓库ID、状态等</li>
 *   <li>支持分页查询和计数查询</li>
 *   <li>支持按场景ID删除所有关联任务</li>
 * </ul>
 *
 * <p>依赖：{@link TaskEntity}
 */
@Mapper
public interface TaskMapper {

    /** 任务表列名常量 */
    String TASK_COLUMNS = """
            id, space_id, scene_id, repo_id, status, current_stage, result_code, result_message,
            cancel_requested, cancel_requested_at, cancel_requested_by, trigger_type,
            trigger_reason, trigger_user, queued_at, branch, commit_sha, started_at,
            finished_at, duration_ms, runner_name, log_url, resolved_branch,
            resolved_browser, resolved_env_json, resolved_match_value, resolved_test_root,
            resolved_run_command, created_at, updated_at
            """;

    /**
     * 插入任务记录
     *
     * @param entity 任务实体
     * @return 影响行数
     */
    @Insert("""
            insert into task (
                space_id, scene_id, repo_id, status, current_stage, result_code, result_message,
                cancel_requested, cancel_requested_at, cancel_requested_by, trigger_type,
                trigger_reason, trigger_user, queued_at, branch, commit_sha, started_at,
                finished_at, duration_ms, runner_name, log_url, resolved_branch,
                resolved_browser, resolved_env_json, resolved_match_value, resolved_test_root,
                resolved_run_command
            ) values (
                #{spaceId}, #{sceneId}, #{repoId}, #{status}, #{currentStage}, #{resultCode}, #{resultMessage},
                #{cancelRequested}, #{cancelRequestedAt}, #{cancelRequestedBy}, #{triggerType},
                #{triggerReason}, #{triggerUser}, #{queuedAt}, #{branch}, #{commitSha}, #{startedAt},
                #{finishedAt}, #{durationMs}, #{runnerName}, #{logUrl}, #{resolvedBranch},
                #{resolvedBrowser}, #{resolvedEnvJson}, #{resolvedMatchValue}, #{resolvedTestRoot},
                #{resolvedRunCommand}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TaskEntity entity);

    /**
     * 更新任务记录
     *
     * @param entity 任务实体
     * @return 影响行数
     */
    @Update("""
            update task
            set space_id = #{spaceId},
                scene_id = #{sceneId},
                repo_id = #{repoId},
                status = #{status},
                current_stage = #{currentStage},
                result_code = #{resultCode},
                result_message = #{resultMessage},
                cancel_requested = #{cancelRequested},
                cancel_requested_at = #{cancelRequestedAt},
                cancel_requested_by = #{cancelRequestedBy},
                trigger_type = #{triggerType},
                trigger_reason = #{triggerReason},
                trigger_user = #{triggerUser},
                queued_at = #{queuedAt},
                branch = #{branch},
                commit_sha = #{commitSha},
                started_at = #{startedAt},
                finished_at = #{finishedAt},
                duration_ms = #{durationMs},
                runner_name = #{runnerName},
                log_url = #{logUrl},
                resolved_branch = #{resolvedBranch},
                resolved_browser = #{resolvedBrowser},
                resolved_env_json = #{resolvedEnvJson},
                resolved_match_value = #{resolvedMatchValue},
                resolved_test_root = #{resolvedTestRoot},
                resolved_run_command = #{resolvedRunCommand}
            where id = #{id}
            """)
    int update(TaskEntity entity);

    /**
     * 根据ID查询任务
     *
     * @param id 任务ID
     * @return 任务实体
     */
    @Select("""
            select
            """ + TASK_COLUMNS + """
            from task
            where id = #{id}
            """)
    @Results(id = "TaskResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "spaceId", column = "space_id"),
            @Result(property = "sceneId", column = "scene_id"),
            @Result(property = "repoId", column = "repo_id"),
            @Result(property = "status", column = "status"),
            @Result(property = "currentStage", column = "current_stage"),
            @Result(property = "resultCode", column = "result_code"),
            @Result(property = "resultMessage", column = "result_message"),
            @Result(property = "cancelRequested", column = "cancel_requested"),
            @Result(property = "cancelRequestedAt", column = "cancel_requested_at"),
            @Result(property = "cancelRequestedBy", column = "cancel_requested_by"),
            @Result(property = "triggerType", column = "trigger_type"),
            @Result(property = "triggerReason", column = "trigger_reason"),
            @Result(property = "triggerUser", column = "trigger_user"),
            @Result(property = "queuedAt", column = "queued_at"),
            @Result(property = "branch", column = "branch"),
            @Result(property = "commitSha", column = "commit_sha"),
            @Result(property = "startedAt", column = "started_at"),
            @Result(property = "finishedAt", column = "finished_at"),
            @Result(property = "durationMs", column = "duration_ms"),
            @Result(property = "runnerName", column = "runner_name"),
            @Result(property = "logUrl", column = "log_url"),
            @Result(property = "resolvedBranch", column = "resolved_branch"),
            @Result(property = "resolvedBrowser", column = "resolved_browser"),
            @Result(property = "resolvedEnvJson", column = "resolved_env_json"),
            @Result(property = "resolvedMatchValue", column = "resolved_match_value"),
            @Result(property = "resolvedTestRoot", column = "resolved_test_root"),
            @Result(property = "resolvedRunCommand", column = "resolved_run_command"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<TaskEntity> findById(@Param("id") Long id);

    /**
     * 根据ID和空间ID查询任务
     *
     * @param id 任务ID
     * @param spaceId 空间ID
     * @return 任务实体
     */
    @Select("""
            select
            """ + TASK_COLUMNS + """
            from task
            where id = #{id}
              and space_id = #{spaceId}
            """)
    @ResultMap("TaskResultMap")
    Optional<TaskEntity> findByIdAndSpaceId(@Param("id") Long id, @Param("spaceId") Long spaceId);

    /**
     * 分页查询所有任务
     *
     * @param limit 每页数量
     * @param offset 偏移量
     * @return 任务列表
     */
    @Select("""
            select
            """ + TASK_COLUMNS + """
            from task
            order by created_at desc, id desc
            limit #{limit} offset #{offset}
            """)
    @ResultMap("TaskResultMap")
    List<TaskEntity> findPage(@Param("limit") int limit, @Param("offset") int offset);

    /**
     * 统计所有任务数量
     *
     * @return 任务总数
     */
    @Select("select count(1) from task")
    long countAll();

    /**
     * 根据空间ID分页查询任务
     *
     * @param spaceId 空间ID
     * @param limit 每页数量
     * @param offset 偏移量
     * @return 任务列表
     */
    @Select("""
            select
            """ + TASK_COLUMNS + """
            from task
            where space_id = #{spaceId}
            order by created_at desc, id desc
            limit #{limit} offset #{offset}
            """)
    @ResultMap("TaskResultMap")
    List<TaskEntity> findPageBySpaceId(@Param("spaceId") Long spaceId, @Param("limit") int limit, @Param("offset") int offset);

    /**
     * 统计指定空间的任务数量
     *
     * @param spaceId 空间ID
     * @return 任务数量
     */
    @Select("""
            select count(1)
            from task
            where space_id = #{spaceId}
            """)
    long countBySpaceId(@Param("spaceId") Long spaceId);

    /**
     * 根据场景ID分页查询任务
     *
     * @param sceneId 场景ID
     * @param limit 每页数量
     * @param offset 偏移量
     * @return 任务列表
     */
    @Select("""
            select
            """ + TASK_COLUMNS + """
            from task
            where scene_id = #{sceneId}
            order by created_at desc, id desc
            limit #{limit} offset #{offset}
            """)
    @ResultMap("TaskResultMap")
    List<TaskEntity> findBySceneIdPage(@Param("sceneId") Long sceneId, @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * 统计指定场景的任务数量
     *
     * @param sceneId 场景ID
     * @return 任务数量
     */
    @Select("""
            select count(1)
            from task
            where scene_id = #{sceneId}
            """)
    long countBySceneId(@Param("sceneId") Long sceneId);

    /**
     * 根据场景ID和空间ID分页查询任务
     *
     * @param sceneId 场景ID
     * @param spaceId 空间ID
     * @param limit 每页数量
     * @param offset 偏移量
     * @return 任务列表
     */
    @Select("""
            select
            """ + TASK_COLUMNS + """
            from task
            where scene_id = #{sceneId}
              and space_id = #{spaceId}
            order by created_at desc, id desc
            limit #{limit} offset #{offset}
            """)
    @ResultMap("TaskResultMap")
    List<TaskEntity> findBySceneIdAndSpaceIdPage(@Param("sceneId") Long sceneId,
                                                 @Param("spaceId") Long spaceId,
                                                 @Param("limit") int limit,
                                                 @Param("offset") int offset);

    /**
     * 统计指定场景和空间的任务数量
     *
     * @param sceneId 场景ID
     * @param spaceId 空间ID
     * @return 任务数量
     */
    @Select("""
            select count(1)
            from task
            where scene_id = #{sceneId}
              and space_id = #{spaceId}
            """)
    long countBySceneIdAndSpaceId(@Param("sceneId") Long sceneId, @Param("spaceId") Long spaceId);

    /**
     * 根据场景ID查询所有任务（按创建时间降序）
     *
     * @param sceneId 场景ID
     * @return 任务列表
     */
    @Select("""
            select
            """ + TASK_COLUMNS + """
            from task
            where scene_id = #{sceneId}
            order by created_at desc, id desc
            """)
    @ResultMap("TaskResultMap")
    List<TaskEntity> findAllBySceneIdOrderByCreatedAtDescIdDesc(@Param("sceneId") Long sceneId);

    /**
     * 根据状态列表查询所有任务
     *
     * @param statuses 状态列表
     * @return 任务列表
     */
    @Select("""
            <script>
            select
            """ + TASK_COLUMNS + """
            from task
            where
            <choose>
              <when test='statuses != null and !statuses.isEmpty()'>
                status in
                <foreach collection='statuses' item='status' open='(' separator=',' close=')'>
                  #{status}
                </foreach>
              </when>
              <otherwise>1 = 0</otherwise>
            </choose>
            order by created_at asc, id asc
            </script>
            """)
    @ResultMap("TaskResultMap")
    List<TaskEntity> findAllByStatusInOrderByCreatedAtAscIdAsc(@Param("statuses") Collection<String> statuses);

    /**
     * 查询所有任务（按创建时间降序）
     *
     * @return 任务列表
     */
    @Select("""
            select
            """ + TASK_COLUMNS + """
            from task
            order by created_at desc, id desc
            """)
    @ResultMap("TaskResultMap")
    List<TaskEntity> findAllByOrderByCreatedAtDescIdDesc();

    /**
     * 根据仓库ID查询所有任务（按ID升序）
     *
     * @param repoId 仓库ID
     * @return 任务列表
     */
    @Select("""
            select
            """ + TASK_COLUMNS + """
            from task
            where repo_id = #{repoId}
            order by id asc
            """)
    @ResultMap("TaskResultMap")
    List<TaskEntity> findAllByRepoIdOrderByIdAsc(@Param("repoId") Long repoId);

    /**
     * 根据场景ID查询所有任务（按ID升序）
     *
     * @param sceneId 场景ID
     * @return 任务列表
     */
    @Select("""
            select
            """ + TASK_COLUMNS + """
            from task
            where scene_id = #{sceneId}
            order by id asc
            """)
    @ResultMap("TaskResultMap")
    List<TaskEntity> findAllBySceneIdOrderByIdAsc(@Param("sceneId") Long sceneId);

    /**
     * 根据场景ID查询最新的一条任务
     *
     * @param sceneId 场景ID
     * @return 最新任务
     */
    @Select("""
            select
            """ + TASK_COLUMNS + """
            from task
            where scene_id = #{sceneId}
            order by created_at desc, id desc
            limit 1
            """)
    @ResultMap("TaskResultMap")
    Optional<TaskEntity> findFirstBySceneIdOrderByCreatedAtDescIdDesc(@Param("sceneId") Long sceneId);

    /**
     * 检查场景是否存在指定状态的任务
     *
     * @param sceneId 场景ID
     * @param statuses 状态列表
     * @return 是否存在
     */
    @Select("""
            <script>
            select count(1) > 0
            from task
            where scene_id = #{sceneId}
            <choose>
              <when test='statuses != null and !statuses.isEmpty()'>
                and status in
                <foreach collection='statuses' item='status' open='(' separator=',' close=')'>
                  #{status}
                </foreach>
              </when>
              <otherwise>and 1 = 0</otherwise>
            </choose>
            </script>
            """)
    boolean existsBySceneIdAndStatusIn(@Param("sceneId") Long sceneId,
            @Param("statuses") Collection<String> statuses);

    /**
     * 根据仓库ID删除所有任务
     *
     * @param repoId 仓库ID
     * @return 影响行数
     */
    @Delete("""
            delete from task
            where repo_id = #{repoId}
            """)
    int deleteAllByRepoId(@Param("repoId") Long repoId);

    /**
     * 根据场景ID删除所有任务
     *
     * @param sceneId 场景ID
     * @return 影响行数
     */
    @Delete("""
            delete from task
            where scene_id = #{sceneId}
            """)
    int deleteAllBySceneId(@Param("sceneId") Long sceneId);
}
