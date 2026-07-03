package com.example.platform.space.mapper;

import com.example.platform.space.model.SpaceEntity;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface SpaceMapper {
    @Insert("""
            insert into space (
                name, description, owner_user_id, created_by
            ) values (
                #{name}, #{description}, #{ownerUserId}, #{createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SpaceEntity entity);

    @Select("""
            select id, name, description, owner_user_id, created_by, created_at, updated_at
            from space
            where name = #{name}
            limit 1
            """)
    @org.apache.ibatis.annotations.ResultMap("SpaceResultMap")
    Optional<SpaceEntity> findByName(@Param("name") String name);

    @Select("""
            select id, name, description, owner_user_id, created_by, created_at, updated_at
            from space
            where id = #{id}
            """)
    @Results(id = "SpaceResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "name", column = "name"),
            @Result(property = "description", column = "description"),
            @Result(property = "ownerUserId", column = "owner_user_id"),
            @Result(property = "createdBy", column = "created_by"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<SpaceEntity> findById(Long id);

    @Select("""
            select id, name, description, owner_user_id, created_by, created_at, updated_at
            from space
            order by id asc
            """)
    @org.apache.ibatis.annotations.ResultMap("SpaceResultMap")
    List<SpaceEntity> findAll();

    @Select("""
            select distinct s.id, s.name, s.description, s.owner_user_id, s.created_by, s.created_at, s.updated_at
            from space s
            left join space_member sm on sm.space_id = s.id
            where (sm.user_id = #{userId} and sm.status = 'ACTIVE')
               or s.owner_user_id = #{userId}
               or s.created_by = #{userId}
            order by s.id asc
            """)
    @org.apache.ibatis.annotations.ResultMap("SpaceResultMap")
    List<SpaceEntity> findByUserId(@Param("userId") Long userId);

    @Update("""
            update space
            set name = #{name},
                description = #{description}
            where id = #{id}
            """)
    int update(SpaceEntity entity);

    @Delete("""
            delete from space
            where id = #{id}
            """)
    int deleteById(@Param("id") Long id);
}
