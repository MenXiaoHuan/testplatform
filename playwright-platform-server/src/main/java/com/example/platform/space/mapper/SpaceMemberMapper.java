package com.example.platform.space.mapper;

import com.example.platform.space.model.SpaceMemberEntity;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 空间成员数据访问接口，提供空间成员的 CRUD 操作。
 *
 * <p>核心职责：
 * <ul>
 *   <li>新增空间成员记录</li>
 *   <li>查询指定空间中指定用户的活跃成员记录</li>
 *   <li>查询指定空间的所有成员</li>
 *   <li>更新成员状态</li>
 *   <li>更新成员角色</li>
 *   <li>删除指定空间的所有成员</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link SpaceMemberEntity} - 空间成员实体类</li>
 *   <li>MyBatis - ORM 框架</li>
 * </ul>
 */
@Mapper
public interface SpaceMemberMapper {
    
    /**
     * 插入新成员记录
     *
     * @param entity 空间成员实体
     * @return 受影响的行数
     */
    @Insert("""
            insert into space_member (
                space_id, user_id, role, status, joined_at
            ) values (
                #{spaceId}, #{userId}, #{role}, #{status}, #{joinedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SpaceMemberEntity entity);

    /**
     * 查询指定空间中指定用户的活跃成员记录
     *
     * @param spaceId 空间ID
     * @param userId 用户ID
     * @return 空间成员实体（可选）
     */
    @Select("""
            select id, space_id, user_id, role, status, joined_at, created_at, updated_at
            from space_member
            where space_id = #{spaceId}
              and user_id = #{userId}
              and status = 'ACTIVE'
            limit 1
            """)
    @Results(id = "SpaceMemberResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "spaceId", column = "space_id"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "role", column = "role"),
            @Result(property = "status", column = "status"),
            @Result(property = "joinedAt", column = "joined_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<SpaceMemberEntity> findActiveBySpaceIdAndUserId(Long spaceId, Long userId);

    /**
     * 查询指定空间的所有成员
     *
     * @param spaceId 空间ID
     * @return 空间成员实体列表
     */
    @Select("""
            select id, space_id, user_id, role, status, joined_at, created_at, updated_at
            from space_member
            where space_id = #{spaceId}
            order by id asc
            """)
    @org.apache.ibatis.annotations.ResultMap("SpaceMemberResultMap")
    List<SpaceMemberEntity> findBySpaceId(Long spaceId);

    /**
     * 更新成员状态
     *
     * @param spaceId 空间ID
     * @param userId 用户ID
     * @param status 新状态
     * @return 受影响的行数
     */
    @Update("""
            update space_member
            set status = #{status}
            where space_id = #{spaceId}
              and user_id = #{userId}
            """)
    int updateStatus(@Param("spaceId") Long spaceId, @Param("userId") Long userId, @Param("status") String status);

    /**
     * 更新成员角色
     *
     * @param spaceId 空间ID
     * @param userId 用户ID
     * @param role 新角色
     * @return 受影响的行数
     */
    @Update("""
            update space_member
            set role = #{role}
            where space_id = #{spaceId}
              and user_id = #{userId}
            """)
    int updateRole(@Param("spaceId") Long spaceId, @Param("userId") Long userId, @Param("role") String role);

    /**
     * 删除指定空间的所有成员
     *
     * @param spaceId 空间ID
     * @return 受影响的行数
     */
    @Delete("""
            delete from space_member
            where space_id = #{spaceId}
            """)
    int deleteBySpaceId(@Param("spaceId") Long spaceId);
}