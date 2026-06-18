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

@Mapper
public interface TaskStageLogMapper {
    String STAGE_LOG_COLUMNS = """
            id, task_id, stage, stream_type, object_key, content_type,
            size, line_count, preview_text, created_at
            """;

    @Insert("""
            insert into task_stage_log (
                task_id, stage, stream_type, object_key, content_type,
                size, line_count, preview_text
            ) values (
                #{taskId}, #{stage}, #{streamType}, #{objectKey}, #{contentType},
                #{size}, #{lineCount}, #{previewText}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TaskStageLogEntity entity);

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
            @Result(property = "previewText", column = "preview_text"),
            @Result(property = "createdAt", column = "created_at")
    })
    List<TaskStageLogEntity> findAllByTaskIdOrderByIdAsc(@Param("taskId") Long taskId);

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
