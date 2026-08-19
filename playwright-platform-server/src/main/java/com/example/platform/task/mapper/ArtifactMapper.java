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

/**
 * 制品数据访问接口（MyBatis Mapper）。
 *
 * <p>核心职责：
 * <ul>
 *   <li>提供制品表的 CRUD 操作</li>
 *   <li>支持按任务ID、用例结果ID查询制品</li>
 *   <li>支持批量按任务ID删除制品</li>
 * </ul>
 *
 * <p>依赖：{@link ArtifactEntity}
 */
@Mapper
public interface ArtifactMapper {

    /** 制品表列名常量 */
    String ARTIFACT_COLUMNS = """
            id, task_id, case_result_id, artifact_type, bucket, object_key,
            content_type, size, url
            """;

    /**
     * 插入制品记录
     *
     * @param entity 制品实体
     * @return 影响行数
     */
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

    /**
     * 根据任务ID查询所有制品（按ID升序）
     *
     * @param taskId 任务ID
     * @return 制品列表
     */
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

    /**
     * 根据用例结果ID查询所有制品（按ID升序）
     *
     * @param caseResultId 用例结果ID
     * @return 制品列表
     */
    @Select("""
            select
            """ + ARTIFACT_COLUMNS + """
            from artifact
            where case_result_id = #{caseResultId}
            order by id asc
            """)
    @ResultMap("ArtifactResultMap")
    List<ArtifactEntity> findAllByCaseResultIdOrderByIdAsc(@Param("caseResultId") Long caseResultId);

    /**
     * 根据任务ID列表批量查询制品
     *
     * @param taskIds 任务ID列表
     * @return 制品列表
     */
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

    /**
     * 根据任务ID列表批量删除制品
     *
     * @param taskIds 任务ID列表
     * @return 影响行数
     */
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
