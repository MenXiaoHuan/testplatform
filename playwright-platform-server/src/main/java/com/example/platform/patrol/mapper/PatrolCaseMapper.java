package com.example.platform.patrol.mapper;

import com.example.platform.patrol.model.PatrolCaseEntity;
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
public interface PatrolCaseMapper {

    String COLUMNS = """
            id, task_id, seq, name, intent, expected, priority, worst_risk, status, exec_status,
            source, judge_reason, video_key, console_key, dom_key, screenshot_key, model_id, tokens,
            duration_ms, degraded, created_at
            """;

    @Insert("""
            insert into patrol_case (
                task_id, seq, name, intent, expected, priority, worst_risk, status, exec_status,
                source, degraded, created_at
            ) values (
                #{taskId}, #{seq}, #{name}, #{intent}, #{expected}, #{priority}, #{worstRisk}, #{status}, #{execStatus},
                #{source}, #{degraded}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PatrolCaseEntity entity);

    @Update("""
            update patrol_case
            set status = #{status}, exec_status = #{execStatus}, judge_reason = #{judgeReason},
                screenshot_key = coalesce(#{screenshotKey}, screenshot_key),
                duration_ms = #{durationMs}, degraded = #{degraded}
            where id = #{id}
            """)
    int updateResult(@Param("id") Long id, @Param("status") String status,
                     @Param("execStatus") String execStatus, @Param("judgeReason") String judgeReason,
                     @Param("screenshotKey") String screenshotKey, @Param("durationMs") Long durationMs,
                     @Param("degraded") boolean degraded);

    @Update("""
            update patrol_case
            set video_key = #{videoKey}, console_key = #{consoleKey}, dom_key = #{domKey},
                screenshot_key = coalesce(#{screenshotKey}, screenshot_key)
            where id = #{id}
            """)
    int updateEvidence(@Param("id") Long id, @Param("videoKey") String videoKey,
                       @Param("consoleKey") String consoleKey, @Param("domKey") String domKey,
                       @Param("screenshotKey") String screenshotKey);

    @Update("update patrol_case set model_id = #{modelId}, tokens = #{tokens} where id = #{id}")
    int updateModelUsage(@Param("id") Long id, @Param("modelId") String modelId, @Param("tokens") int tokens);

    @Update("""
            update patrol_case
            set status = 'SKIP', exec_status = coalesce(#{execStatus}, exec_status),
                judge_reason = #{reason}
            where task_id = #{taskId} and status = 'PENDING'
            """)
    int markPendingSkipped(@Param("taskId") Long taskId, @Param("execStatus") String execStatus,
                           @Param("reason") String reason);

    @Select("select " + COLUMNS + " from patrol_case where id = #{id}")
    @Results(id = "PatrolCaseResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "taskId", column = "task_id"),
            @Result(property = "seq", column = "seq"),
            @Result(property = "name", column = "name"),
            @Result(property = "intent", column = "intent"),
            @Result(property = "expected", column = "expected"),
            @Result(property = "priority", column = "priority"),
            @Result(property = "worstRisk", column = "worst_risk"),
            @Result(property = "status", column = "status"),
            @Result(property = "execStatus", column = "exec_status"),
            @Result(property = "source", column = "source"),
            @Result(property = "judgeReason", column = "judge_reason"),
            @Result(property = "videoKey", column = "video_key"),
            @Result(property = "consoleKey", column = "console_key"),
            @Result(property = "domKey", column = "dom_key"),
            @Result(property = "screenshotKey", column = "screenshot_key"),
            @Result(property = "modelId", column = "model_id"),
            @Result(property = "tokens", column = "tokens"),
            @Result(property = "durationMs", column = "duration_ms"),
            @Result(property = "degraded", column = "degraded"),
            @Result(property = "createdAt", column = "created_at")
    })
    Optional<PatrolCaseEntity> findById(@Param("id") Long id);

    @Select("""
            select\s""" + COLUMNS + """
            from patrol_case
            where task_id = #{taskId}
            order by seq asc, id asc
            """)
    @ResultMap("PatrolCaseResultMap")
    List<PatrolCaseEntity> findByTaskId(@Param("taskId") Long taskId);

    @Delete("delete from patrol_case where task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") Long taskId);
}
