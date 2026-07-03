package com.example.platform.auth.mapper;

import com.example.platform.auth.model.PlatformUserEntity;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PlatformUserMapper {
    @Select("""
            select id, username, nickname, password_hash, avatar_object_key, enabled, last_space_id, created_at, updated_at
            from platform_user
            where username = #{username}
              and enabled = 1
            limit 1
            """)
    @Results(id = "PlatformUserResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "username", column = "username"),
            @Result(property = "nickname", column = "nickname"),
            @Result(property = "passwordHash", column = "password_hash"),
            @Result(property = "avatarObjectKey", column = "avatar_object_key"),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "lastSpaceId", column = "last_space_id"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<PlatformUserEntity> findEnabledByUsername(@Param("username") String username);

    @Select("""
            select id, username, nickname, password_hash, avatar_object_key, enabled, last_space_id, created_at, updated_at
            from platform_user
            where username = #{username}
            limit 1
            """)
    @org.apache.ibatis.annotations.ResultMap("PlatformUserResultMap")
    Optional<PlatformUserEntity> findByUsername(@Param("username") String username);

    @Select("""
            select id, username, nickname, password_hash, avatar_object_key, enabled, last_space_id, created_at, updated_at
            from platform_user
            where nickname = #{nickname}
            limit 1
            """)
    @org.apache.ibatis.annotations.ResultMap("PlatformUserResultMap")
    Optional<PlatformUserEntity> findByNickname(@Param("nickname") String nickname);

    @Insert("""
            insert into platform_user (
                username, nickname, password_hash, avatar_object_key, enabled, last_space_id
            ) values (
                #{username}, #{nickname}, #{passwordHash}, #{avatarObjectKey}, #{enabled}, #{lastSpaceId}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PlatformUserEntity entity);

    @Select("""
            select id, username, nickname, password_hash, avatar_object_key, enabled, last_space_id, created_at, updated_at
            from platform_user
            where id = #{id}
            limit 1
            """)
    @org.apache.ibatis.annotations.ResultMap("PlatformUserResultMap")
    Optional<PlatformUserEntity> findById(@Param("id") Long id);

    @Update("""
            update platform_user
            set nickname = #{nickname}
            where id = #{id}
            """)
    int updateNickname(@Param("id") Long id, @Param("nickname") String nickname);

    @Update("""
            update platform_user
            set avatar_object_key = #{avatarObjectKey}
            where id = #{id}
            """)
    int updateAvatarObjectKey(@Param("id") Long id, @Param("avatarObjectKey") String avatarObjectKey);

    @Update("""
            update platform_user
            set last_space_id = #{lastSpaceId}
            where id = #{id}
            """)
    int updateLastSpaceId(@Param("id") Long id, @Param("lastSpaceId") Long lastSpaceId);
}
