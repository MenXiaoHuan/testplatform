package com.example.platform.task.mapper;

import com.example.platform.task.model.ArtifactEntity;
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
public interface ArtifactMapper {
    String ARTIFACT_COLUMNS = """
            id, task_id, case_result_id, artifact_type, bucket, object_key,
            content_type, size, url
            """;

    @Insert("""
            insert into artifact (
                task_id, case_result_id, artifact_type, bucket, object_key,
                content_type, size, url
            ) values (
                #{taskId}, #{caseResultId}, #{artifactType}, #{bucket}, #{objectKey},
                #{contentType}, #{size}, #{url}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ArtifactEntity entity);

    @Select("""
            select
            """ + ARTIFACT_COLUMNS + """
            from artifact
            where task_id = #{taskId}
            order by id asc
            """)
    @Results(id = "ArtifactResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "taskId", column = "task_id"),
            @Result(property = "caseResultId", column = "case_result_id"),
            @Result(property = "artifactType", column = "artifact_type"),
            @Result(property = "bucket", column = "bucket"),
            @Result(property = "objectKey", column = "object_key"),
            @Result(property = "contentType", column = "content_type"),
            @Result(property = "size", column = "size"),
            @Result(property = "url", column = "url")
    })
    List<ArtifactEntity> findAllByTaskIdOrderByIdAsc(@Param("taskId") Long taskId);

    @Select("""
            select
            """ + ARTIFACT_COLUMNS + """
            from artifact
            where case_result_id = #{caseResultId}
            order by id asc
            """)
    @ResultMap("ArtifactResultMap")
    List<ArtifactEntity> findAllByCaseResultIdOrderByIdAsc(@Param("caseResultId") Long caseResultId);

    @Select("""
            <script>
            select
            """ + ARTIFACT_COLUMNS + """
            from artifact
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
    @ResultMap("ArtifactResultMap")
    List<ArtifactEntity> findAllByTaskIdIn(@Param("taskIds") List<Long> taskIds);

    @Delete("""
            <script>
            delete from artifact
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
