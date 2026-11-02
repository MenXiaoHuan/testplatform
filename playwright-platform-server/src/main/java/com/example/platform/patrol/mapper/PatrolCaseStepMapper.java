package com.example.platform.patrol.mapper;

import com.example.platform.patrol.model.PatrolCaseStepEntity;
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
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PatrolCaseStepMapper {

    String COLUMNS = """
            id, case_id, seq, action, target_selector, locator_json, target_label, input_value,
            operation, risk, approved, status, message, screenshot_key, request_json, duration_ms, created_at
            """;

    @Insert("""
            insert into patrol_case_step (
                case_id, seq, action, target_selector, locator_json, target_label, input_value,
                operation, risk, approved, status, created_at
            ) values (
                #{caseId}, #{seq}, #{action}, #{targetSelector}, #{locatorJson}, #{targetLabel}, #{inputValue},
                #{operation}, #{risk}, #{approved}, #{status}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PatrolCaseStepEntity entity);

    @Update("""
            update patrol_case_step
            set status = #{status}, message = #{message}, screenshot_key = #{screenshotKey},
                request_json = #{requestJson}, approved = #{approved}, duration_ms = #{durationMs}
            where case_id = #{caseId} and seq = #{seq}
            """)
    int updateResult(@Param("caseId") Long caseId, @Param("seq") Integer seq,
                     @Param("status") String status, @Param("message") String message,
                     @Param("screenshotKey") String screenshotKey, @Param("requestJson") String requestJson,
                     @Param("approved") boolean approved, @Param("durationMs") Long durationMs);

    @Select("select " + COLUMNS + " from patrol_case_step where case_id = #{caseId} order by seq asc")
    @Results(id = "PatrolCaseStepResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "caseId", column = "case_id"),
            @Result(property = "seq", column = "seq"),
            @Result(property = "action", column = "action"),
            @Result(property = "targetSelector", column = "target_selector"),
            @Result(property = "locatorJson", column = "locator_json"),
            @Result(property = "targetLabel", column = "target_label"),
            @Result(property = "inputValue", column = "input_value"),
            @Result(property = "operation", column = "operation"),
            @Result(property = "risk", column = "risk"),
            @Result(property = "approved", column = "approved"),
            @Result(property = "status", column = "status"),
            @Result(property = "message", column = "message"),
            @Result(property = "screenshotKey", column = "screenshot_key"),
            @Result(property = "requestJson", column = "request_json"),
            @Result(property = "durationMs", column = "duration_ms"),
            @Result(property = "createdAt", column = "created_at")
    })
    List<PatrolCaseStepEntity> findByCaseId(@Param("caseId") Long caseId);

    @Select("""
            select s.id, s.case_id, s.seq, s.action, s.target_selector, s.locator_json, s.target_label,
                   s.input_value, s.operation, s.risk, s.approved, s.status, s.message, s.screenshot_key,
                   s.request_json, s.duration_ms, s.created_at
            from patrol_case_step s
            join patrol_case c on c.id = s.case_id
            where c.task_id = #{taskId}
            order by s.case_id asc, s.seq asc
            """)
    @ResultMap("PatrolCaseStepResultMap")
    List<PatrolCaseStepEntity> findByTaskId(@Param("taskId") Long taskId);

    @Delete("delete from patrol_case_step where case_id = #{caseId}")
    int deleteByCaseId(@Param("caseId") Long caseId);
}
