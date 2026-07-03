package com.example.platform.auth.mapper;

import com.example.platform.auth.model.AuthSession;
import com.example.platform.auth.model.UserSessionEntity;
import java.time.LocalDateTime;
import java.util.Optional;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.type.JdbcType;

@Mapper
public interface UserSessionMapper {
    @Insert("""
            insert into user_session (
                session_id, user_id, expires_at
            ) values (
                #{sessionId}, #{userId}, #{expiresAt}
            )
            """)
    int insert(UserSessionEntity entity);

    @Select("""
            select us.session_id, pu.id as user_id, pu.username, pu.nickname, pu.avatar_object_key, pu.last_space_id, us.expires_at
            from user_session us
            join platform_user pu on pu.id = us.user_id
            where us.session_id = #{sessionId}
              and pu.enabled = 1
            limit 1
            """)
    @ConstructorArgs({
            @Arg(column = "session_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
            @Arg(column = "user_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
            @Arg(column = "username", javaType = String.class, jdbcType = JdbcType.VARCHAR),
            @Arg(column = "nickname", javaType = String.class, jdbcType = JdbcType.VARCHAR),
            @Arg(column = "avatar_object_key", javaType = String.class, jdbcType = JdbcType.VARCHAR),
            @Arg(column = "last_space_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
            @Arg(column = "expires_at", javaType = LocalDateTime.class, jdbcType = JdbcType.TIMESTAMP)
    })
    Optional<AuthSession> findAuthSessionBySessionId(@Param("sessionId") String sessionId);

    @Update("""
            update user_session
            set expires_at = #{expiresAt}
            where session_id = #{sessionId}
            """)
    int updateExpiresAt(@Param("sessionId") String sessionId, @Param("expiresAt") LocalDateTime expiresAt);

    @Delete("""
            delete from user_session
            where session_id = #{sessionId}
            """)
    int deleteBySessionId(@Param("sessionId") String sessionId);
}
