package com.example.platform.task.mapper;

import com.example.platform.task.model.CaseResultEntity;
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
 * 用例结果数据访问接口（MyBatis Mapper）。
 *
 * <p>核心职责：
 * <ul>
 *   <li>提供用例结果表的 CRUD 操作</li>
 *   <li>支持按任务ID查询用例结果</li>
 *   <li>支持批量按任务ID删除用例结果</li>
 * </ul>
 *
 * <p>依赖：{@link CaseResultEntity}
 */
@Mapper
public interface CaseResultMapper {

    /** 用例结果表列名常量 */
    String CASE_RESULT_COLUMNS = """
            id, task_id, history_id, full_name, suite_name, story_name, status,
            duration_ms, owner_name, severity, project_name
            """;

    /**
     * 插入用例结果记录
     *
     * @param entity 用例结果实体
     * @return 影响行数
     */
    @Insert("""
            insert into case_result (
                task_id, history_id, full_name, suite_name, story_name, status,
                duration_ms, owner_name, severity, project_name
            ) values (
                #{taskId}, #{historyId}, #{fullName}, #{suiteName}, #{storyName}, #{status},
                #{durationMs}, #{ownerName}, #{severity}, #{projectName}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CaseResultEntity entity);

    /**
     * 根据任务ID查询所有用例结果（按ID升序）
     *
     * @param taskId 任务ID
     * @return 用例结果列表
     */
    @Select("""
            select
            """ + CASE_RESULT_COLUMNS + """
            from case_result
            where task_id = #{taskId}
            order by id asc
            """)
    @Results(id = "CaseResultResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "taskId", column = "task_id"),
            @Result(property = "historyId", column = "history_id"),
            @Result(property = "fullName", column = "full_name"),
            @Result(property = "suiteName", column = "suite_name"),
            @Result(property = "storyName", column = "story_name"),
            @Result(property = "status", column = "status"),
            @Result(property = "durationMs", column = "duration_ms"),
            @Result(property = "ownerName", column = "owner_name"),
            @Result(property = "severity", column = "severity"),
            @Result(property = "projectName", column = "project_name")
    })
    List<CaseResultEntity> findAllByTaskIdOrderByIdAsc(@Param("taskId") Long taskId);

    /**
     * 根据任务ID列表批量删除用例结果
     *
     * @param taskIds 任务ID列表
     * @return 影响行数
     */
    @Delete("""
            <script>
            delete from case_result
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

    /**
     * 根据任务ID列表批量查询用例结果
     *
     * @param taskIds 任务ID列表
     * @return 用例结果列表
     */
    @Select("""
            <script>
            select
            """ + CASE_RESULT_COLUMNS + """
            from case_result
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
    @ResultMap("CaseResultResultMap")
    List<CaseResultEntity> findAllByTaskIdIn(@Param("taskIds") List<Long> taskIds);
}
