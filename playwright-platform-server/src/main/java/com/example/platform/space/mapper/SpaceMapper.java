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

/**
 * 空间数据访问接口，提供空间的 CRUD 操作。
 *
 * <p>核心职责：
 * <ul>
 *   <li>新增空间记录</li>
 *   <li>根据名称查询空间</li>
 *   <li>根据ID查询空间</li>
 *   <li>查询所有空间</li>
 *   <li>根据用户ID查询关联的空间</li>
 *   <li>更新空间信息</li>
 *   <li>删除空间</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link SpaceEntity} - 空间实体类</li>
 *   <li>MyBatis - ORM 框架</li>
 * </ul>
 */
@Mapper
public interface SpaceMapper {
    
    /**
     * 插入新空间记录
     *
     * @param entity 空间实体
     * @return 受影响的行数
     */
    @Insert("""
            insert into space (
                name, description, owner_user_id, created_by
            ) values (
                #{name}, #{description}, #{ownerUserId}, #{createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SpaceEntity entity);

    /**
     * 根据名称查询空间
     *
     * @param name 空间名称
     * @return 空间实体（可选）
     */
    @Select("""
            select id, name, description, owner_user_id, created_by, created_at, updated_at
            from space
            where name = #{name}
            limit 1
            """)
    @org.apache.ibatis.annotations.ResultMap("SpaceResultMap")
    Optional<SpaceEntity> findByName(@Param("name") String name);

    /**
     * 根据ID查询空间
     *
     * @param id 空间ID
     * @return 空间实体（可选）
     */
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

    /**
     * 查询所有空间
     *
     * @return 空间实体列表
     */
    @Select("""
            select id, name, description, owner_user_id, created_by, created_at, updated_at
            from space
            order by id asc
            """)
    @org.apache.ibatis.annotations.ResultMap("SpaceResultMap")
    List<SpaceEntity> findAll();

    /**
     * 根据用户ID查询其关联的所有空间
     * 包括用户作为成员的空间、作为所有者的空间和作为创建者的空间
     *
     * @param userId 用户ID
     * @return 空间实体列表
     */
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

    /**
     * 更新空间信息
     *
     * @param entity 空间实体
     * @return 受影响的行数
     */
    @Update("""
            update space
            set name = #{name},
                description = #{description}
            where id = #{id}
            """)
    int update(SpaceEntity entity);

    /**
     * 根据ID删除空间
     *
     * @param id 空间ID
     * @return 受影响的行数
     */
    @Delete("""
            delete from space
            where id = #{id}
            """)
    int deleteById(@Param("id") Long id);
}