package com.example.platform.admin.mapper;

import com.example.platform.auth.model.PlatformUserEntity;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 超管用户管理专用的用户数据访问接口。
 *
 * <p>与 {@link com.example.platform.auth.mapper.PlatformUserMapper} 同表，但独立出一套查询，
 * 避免为管理端新增方法时改动登录/注册链路的 Mapper 签名。
 */
@Mapper
public interface PlatformUserAdminMapper {

    @Select("""
            <script>
            select id, username, nickname, password_hash, avatar_object_key, enabled,
                   is_super_admin, last_space_id, created_at, updated_at
            from platform_user
            <where>
                <if test="keyword != null and keyword != ''">
                    and (username like concat('%', #{keyword}, '%')
                        or nickname like concat('%', #{keyword}, '%'))
                </if>
            </where>
            order by id asc
            limit #{limit} offset #{offset}
            </script>
            """)
    @Results(id = "AdminPlatformUserResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "username", column = "username"),
            @Result(property = "nickname", column = "nickname"),
            @Result(property = "passwordHash", column = "password_hash"),
            @Result(property = "avatarObjectKey", column = "avatar_object_key"),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "superAdmin", column = "is_super_admin"),
            @Result(property = "lastSpaceId", column = "last_space_id"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<PlatformUserEntity> findPage(
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("""
            <script>
            select count(*)
            from platform_user
            <where>
                <if test="keyword != null and keyword != ''">
                    and (username like concat('%', #{keyword}, '%')
                        or nickname like concat('%', #{keyword}, '%'))
                </if>
            </where>
            </script>
            """)
    long count(@Param("keyword") String keyword);

    @Select("""
            select id, username, nickname, password_hash, avatar_object_key, enabled,
                   is_super_admin, last_space_id, created_at, updated_at
            from platform_user
            where id = #{id}
            limit 1
            """)
    @ResultMap("AdminPlatformUserResultMap")
    Optional<PlatformUserEntity> findById(@Param("id") Long id);

    @Update("""
            update platform_user
            set nickname = #{nickname},
                enabled = #{enabled}
            where id = #{id}
            """)
    int updateProfile(
            @Param("id") Long id,
            @Param("nickname") String nickname,
            @Param("enabled") boolean enabled);

    @Update("""
            update platform_user
            set enabled = #{enabled}
            where id = #{id}
            """)
    int updateEnabled(@Param("id") Long id, @Param("enabled") boolean enabled);

    @Update("""
            update platform_user
            set password_hash = #{passwordHash}
            where id = #{id}
            """)
    int updatePasswordHash(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    @Delete("""
            delete from platform_user
            where id = #{id}
            """)
    int deleteById(@Param("id") Long id);
}
