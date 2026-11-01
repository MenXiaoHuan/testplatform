package com.example.platform.evaluation.agent;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

@Mapper
public interface EvalAgentConnectionMapper {

    @Insert("""
            insert into eval_agent_connection (space_id, target_id, agent_key, public_key, secret_hash, status)
            values (#{spaceId}, #{targetId}, #{agentKey}, #{publicKey}, #{secretHash}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EvalAgentConnectionEntity entity);

    @Select("""
            select id, space_id, target_id, agent_key, public_key, secret_hash, status,
                   last_seen_at, trace_count, created_at, updated_at
            from eval_agent_connection where public_key = #{publicKey}
            """)
    @Results(id = "AgentConnResultMap", value = {
        @Result(property = "id", column = "id", id = true),
        @Result(property = "spaceId", column = "space_id"),
        @Result(property = "targetId", column = "target_id"),
        @Result(property = "agentKey", column = "agent_key"),
        @Result(property = "publicKey", column = "public_key"),
        @Result(property = "secretHash", column = "secret_hash"),
        @Result(property = "status", column = "status"),
        @Result(property = "lastSeenAt", column = "last_seen_at"),
        @Result(property = "traceCount", column = "trace_count"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<EvalAgentConnectionEntity> findByPublicKey(String publicKey);

    @Select("""
            select id, space_id, target_id, agent_key, public_key, secret_hash, status,
                   last_seen_at, trace_count, created_at, updated_at
            from eval_agent_connection where target_id = #{targetId}
            """)
    @ResultMap("AgentConnResultMap")
    Optional<EvalAgentConnectionEntity> findByTargetId(Long targetId);

    @Update("""
            update eval_agent_connection
            set public_key = #{publicKey}, secret_hash = #{secretHash}, updated_at = current_timestamp
            where target_id = #{targetId}
            """)
    int rotate(@Param("targetId") Long targetId,
               @Param("publicKey") String publicKey,
               @Param("secretHash") String secretHash);

    @Update("update eval_agent_connection set status = #{status} where target_id = #{targetId}")
    int updateStatus(@Param("targetId") Long targetId, @Param("status") String status);

    @Update("""
            update eval_agent_connection
            set last_seen_at = current_timestamp, trace_count = trace_count + #{delta}
            where target_id = #{targetId}
            """)
    int touch(@Param("targetId") Long targetId, @Param("delta") int delta);

    @Select("""
            select c.target_id, t.name, c.agent_key, c.public_key, c.status,
                   c.last_seen_at, c.trace_count, c.created_at
            from eval_agent_connection c
            join eval_target t on t.id = c.target_id
            where c.space_id = #{spaceId}
            order by c.id desc
            """)
    @Results(id = "AgentSummaryMap", value = {
        @Result(property = "targetId", column = "target_id"),
        @Result(property = "name", column = "name"),
        @Result(property = "agentKey", column = "agent_key"),
        @Result(property = "publicKey", column = "public_key"),
        @Result(property = "status", column = "status"),
        @Result(property = "lastSeenAt", column = "last_seen_at"),
        @Result(property = "traceCount", column = "trace_count"),
        @Result(property = "createdAt", column = "created_at")
    })
    List<AgentSummary> listBySpace(Long spaceId);
}
