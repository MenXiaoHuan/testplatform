package com.example.platform.patrol.mapper;

import com.example.platform.patrol.model.PatrolFindingEntity;
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
public interface PatrolFindingMapper {

    String COLUMNS = """
            id, task_id, case_id, step_seq, type, severity, title, description, evidence_json,
            status, degraded, handled_by, handled_at, created_at
            """;

    @Insert("""
            insert into patrol_finding (
                task_id, case_id, step_seq, type, severity, title, description, evidence_json,
                status, degraded, created_at
            ) values (
                #{taskId}, #{caseId}, #{stepSeq}, #{type}, #{severity}, #{title}, #{description}, #{evidenceJson},
                #{status}, #{degraded}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PatrolFindingEntity entity);

    @Select("select " + COLUMNS + " from patrol_finding where id = #{id} and task_id = #{taskId}")
    @Results(id = "PatrolFindingResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "taskId", column = "task_id"),
            @Result(property = "caseId", column = "case_id"),
            @Result(property = "stepSeq", column = "step_seq"),
            @Result(property = "type", column = "type"),
            @Result(property = "severity", column = "severity"),
            @Result(property = "title", column = "title"),
            @Result(property = "description", column = "description"),
            @Result(property = "evidenceJson", column = "evidence_json"),
            @Result(property = "status", column = "status"),
            @Result(property = "degraded", column = "degraded"),
            @Result(property = "handledBy", column = "handled_by"),
            @Result(property = "handledAt", column = "handled_at"),
            @Result(property = "createdAt", column = "created_at")
    })
    Optional<PatrolFindingEntity> findByIdAndTaskId(@Param("id") Long id, @Param("taskId") Long taskId);

    @Select("""
            select\s""" + COLUMNS + """
            from patrol_finding
            where task_id = #{taskId}
            order by case_id asc, step_seq asc, id asc
            """)
    @ResultMap("PatrolFindingResultMap")
    List<PatrolFindingEntity> findByTaskId(@Param("taskId") Long taskId);

    @Update("""
            update patrol_finding
            set status = #{status}, handled_by = #{handledBy}, handled_at = #{handledAt}
            where id = #{id} and task_id = #{taskId}
            """)
    int updateStatus(@Param("id") Long id, @Param("taskId") Long taskId,
                     @Param("status") String status, @Param("handledBy") String handledBy,
                     @Param("handledAt") LocalDateTime handledAt);

    @Delete("delete from patrol_finding where task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") Long taskId);
}
