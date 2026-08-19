package com.example.platform.task.mapper;

import com.example.platform.task.model.TaskStageLogEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/**
 * 任务阶段日志数据访问接口（MyBatis Mapper）。
 *
 * <p>核心职责：
 * <ul>
 *   <li>提供任务阶段日志表的 CRUD 操作</li>
 *   <li>支持按任务ID查询阶段日志</li>
 *   <li>支持批量按任务ID删除阶段日志</li>
 * </ul>
 *
 * <p>依赖：{@link TaskStageLogEntity}
 */
@Mapper
public interface TaskStageLogMapper {

    /** 阶段日志表列名常量 */
    String STAGE_LOG_COLUMNS = """
            id, task_id, stage, stream_type, object_key, content_type,
            size, line_count, duration_ms, exit_code, stage_status, command,
            started_at, ended_at, error_message, preview_text, created_at
            """;

    /**
     * 插入阶段日志记录
     *
     * @param entity 阶段日志实体
     * @return 影响行数
     */
    @Insert("""
            insert into task_stage_log (
                task_id, stage, stream_type, object_key, content_type,
                size, line_count, duration_ms, exit_code, stage_status, command,
                started_at, ended_at, error_message, preview_text
            ) values (
                #{taskId}, #{stage}, #{streamType}, #{objectKey}, #{contentType},
                #{size}, #{lineCount}, #{durationMs}, #{exitCode}, #{stageStatus}, #{command},
                #{startedAt}, #{endedAt}, #{errorMessage}, #{previewText}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TaskStageLogEntity entity);

    /**
     * 根据任务ID查询所有阶段日志（按ID升序）
     *
     * @param taskId 任务ID
     * @return 阶段日志列表
     */
    @Select("""
            select
            """ + STAGE_LOG_COLUMNS + """
            from task_stage_log
            where task_id = #{taskId}
            order by id asc
            """)
    @Results(id = "TaskStageLogResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "taskId", column = "task_id"),
            @Result(property = "stage", column = "stage"),
            @Result(property = "streamType", column = "stream_type"),
            @Result(property = "objectKey", column = "object_key"),
            @Result(property = "contentType", column = "content_type"),
            @Result(property = "size", column = "size"),
            @Result(property = "lineCount", column = "line_count"),
            @Result(property = "durationMs", column = "duration_ms"),
            @Result(property = "exitCode", column = "exit_code"),
            @Result(property = "stageStatus", column = "stage_status"),
            @Result(property = "command", column = "command"),
            @Result(property = "startedAt", column = "started_at"),
            @Result(property = "endedAt", column = "ended_at"),
            @Result(property = "errorMessage", column = "error_message"),
            @Result(property = "previewText", column = "preview_text"),
            @Result(property = "createdAt", column = "created_at")
    })
    List<TaskStageLogEntity> findAllByTaskIdOrderByIdAsc(@Param("taskId") Long taskId);

    /**
     * 根据任务ID列表批量查询阶段日志
     *
     * @param taskIds 任务ID列表
     * @return 阶段日志列表
     */
    @Select("""
            <script>
            select
            """ + STAGE_LOG_COLUMNS + """
            from task_stage_log
            where
            <choose>
              <when test='taskIds != null and !taskIds.isEmpty()'>
                task_id in
                <foreach collection='taskIds' item='taskId' open='(' separator=',' close=')'>
                  #{taskId}
                </foreach>
              </when>
              <otherwise>1 = 0</otherwise>
            </choose>
            order by task_id asc, id asc
            </script>
            """)
    @ResultMap("TaskStageLogResultMap")
    List<TaskStageLogEntity> findAllByTaskIdIn(@Param("taskIds") List<Long> taskIds);

    /**
     * 根据任务ID列表批量删除阶段日志
     *
     * @param taskIds 任务ID列表
     * @return 影响行数
     */
    @Delete("""
            <script>
            delete from task_stage_log
            where
            <choose>
              <when test='taskIds != null and !taskIds.isEmpty()'>
                task_id in
                <foreach collection='taskIds' item='taskId' open='(' separator=',' close=')'>
                  #{taskId}
                </foreach>
              </when>
              <otherwise>1 = 0</otherwise>
            </choose>
            </script>
            """)
    int deleteAllByTaskIdIn(@Param("taskIds") List<Long> taskIds);
}
