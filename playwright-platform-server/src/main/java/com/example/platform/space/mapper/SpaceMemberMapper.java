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

@Mapper
public interface SpaceMemberMapper {
    @Insert("""
            insert into space_member (
                space_id, user_id, role, status, joined_at
            ) values (
                #{spaceId}, #{userId}, #{role}, #{status}, #{joinedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SpaceMemberEntity entity);

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

    @Select("""
            select id, space_id, user_id, role, status, joined_at, created_at, updated_at
            from space_member
            where space_id = #{spaceId}
            order by id asc
            """)
    @org.apache.ibatis.annotations.ResultMap("SpaceMemberResultMap")
    List<SpaceMemberEntity> findBySpaceId(Long spaceId);

    @Update("""
            update space_member
            set status = #{status}
            where space_id = #{spaceId}
              and user_id = #{userId}
            """)
    int updateStatus(@Param("spaceId") Long spaceId, @Param("userId") Long userId, @Param("status") String status);

    @Update("""
            update space_member
            set role = #{role}
            where space_id = #{spaceId}
              and user_id = #{userId}
            """)
    int updateRole(@Param("spaceId") Long spaceId, @Param("userId") Long userId, @Param("role") String role);

    @Delete("""
            delete from space_member
            where space_id = #{spaceId}
            """)
    int deleteBySpaceId(@Param("spaceId") Long spaceId);
}
