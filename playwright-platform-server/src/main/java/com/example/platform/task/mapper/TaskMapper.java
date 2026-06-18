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

@Mapper
public interface TaskMapper {
    String TASK_COLUMNS = """
            id, scene_id, repo_id, status, current_stage, result_code, result_message,
            cancel_requested, cancel_requested_at, cancel_requested_by, trigger_type,
            trigger_reason, trigger_user, queued_at, branch, commit_sha, started_at,
            finished_at, duration_ms, runner_name, log_url, resolved_branch,
            resolved_browser, resolved_env_json, resolved_match_value, resolved_test_root,
            resolved_run_command, created_at, updated_at
            """;

    @Insert("""
            insert into task (
                scene_id, repo_id, status, current_stage, result_code, result_message,
                cancel_requested, cancel_requested_at, cancel_requested_by, trigger_type,
                trigger_reason, trigger_user, queued_at, branch, commit_sha, started_at,
                finished_at, duration_ms, runner_name, log_url, resolved_branch,
                resolved_browser, resolved_env_json, resolved_match_value, resolved_test_root,
                resolved_run_command
            ) values (
                #{sceneId}, #{repoId}, #{status}, #{currentStage}, #{resultCode}, #{resultMessage},
                #{cancelRequested}, #{cancelRequestedAt}, #{cancelRequestedBy}, #{triggerType},
                #{triggerReason}, #{triggerUser}, #{queuedAt}, #{branch}, #{commitSha}, #{startedAt},
                #{finishedAt}, #{durationMs}, #{runnerName}, #{logUrl}, #{resolvedBranch},
                #{resolvedBrowser}, #{resolvedEnvJson}, #{resolvedMatchValue}, #{resolvedTestRoot},
                #{resolvedRunCommand}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TaskEntity entity);

    @Update("""
            update task
            set scene_id = #{sceneId},
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

    @Select("""
            select
            """ + TASK_COLUMNS + """
            from task
            where id = #{id}
            """)
    @Results(id = "TaskResultMap", value = {
            @Result(property = "id", column = "id", id = true),
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

    @Select("""
            select
            """ + TASK_COLUMNS + """
            from task
            order by created_at desc, id desc
            limit #{limit} offset #{offset}
            """)
    @ResultMap("TaskResultMap")
    List<TaskEntity> findPage(@Param("limit") int limit, @Param("offset") int offset);

    @Select("select count(1) from task")
    long countAll();

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

    @Select("""
            select count(1)
            from task
            where scene_id = #{sceneId}
            """)
    long countBySceneId(@Param("sceneId") Long sceneId);

    @Select("""
            select
            """ + TASK_COLUMNS + """
            from task
            where scene_id = #{sceneId}
            order by created_at desc, id desc
            """)
    @ResultMap("TaskResultMap")
    List<TaskEntity> findAllBySceneIdOrderByCreatedAtDescIdDesc(@Param("sceneId") Long sceneId);

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

    @Select("""
            select
            """ + TASK_COLUMNS + """
            from task
            order by created_at desc, id desc
            """)
    @ResultMap("TaskResultMap")
    List<TaskEntity> findAllByOrderByCreatedAtDescIdDesc();

    @Select("""
            select
            """ + TASK_COLUMNS + """
            from task
            where repo_id = #{repoId}
            order by id asc
            """)
    @ResultMap("TaskResultMap")
    List<TaskEntity> findAllByRepoIdOrderByIdAsc(@Param("repoId") Long repoId);

    @Select("""
            select
            """ + TASK_COLUMNS + """
            from task
            where scene_id = #{sceneId}
            order by id asc
            """)
    @ResultMap("TaskResultMap")
    List<TaskEntity> findAllBySceneIdOrderByIdAsc(@Param("sceneId") Long sceneId);

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

    @Delete("""
            delete from task
            where repo_id = #{repoId}
            """)
    int deleteAllByRepoId(@Param("repoId") Long repoId);

    @Delete("""
            delete from task
            where scene_id = #{sceneId}
            """)
    int deleteAllBySceneId(@Param("sceneId") Long sceneId);
}
