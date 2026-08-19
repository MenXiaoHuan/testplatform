package com.example.platform.scene.mapper;

import com.example.platform.scene.model.SceneScheduleStateEntity;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 场景调度状态 Mapper —— 基于注解的 MyBatis 数据访问层，负责场景调度状态表的 CRUD 与租约操作。
 *
 * <p>核心职责：
 * <ul>
 *   <li>按场景 ID 查询/插入/更新调度状态：{@link #findBySceneId}、{@link #insert}、{@link #update}</li>
 *   <li>租约竞争：{@link #tryAcquire} —— 乐观锁确保同一时间只有一个调度实例能触发场景</li>
 *   <li>标记触发：{@link #markTriggered} —— 场景触发后更新 last_triggered_at 与 last_task_id</li>
 *   <li>删除：{@link #deleteBySceneId}</li>
 * </ul>
 *
 * <p>依赖：{@link SceneScheduleStateEntity}（实体类）。
 */
@Mapper
public interface SceneScheduleStateMapper {
    @Select("""
            select scene_id, last_planned_fire_at, last_triggered_at, last_task_id,
                   lease_owner, lease_until, version, updated_at
            from scene_schedule_state
            where scene_id = #{sceneId}
            """)
    @Results(id = "SceneScheduleStateResultMap", value = {
            @Result(property = "sceneId", column = "scene_id", id = true),
            @Result(property = "lastPlannedFireAt", column = "last_planned_fire_at"),
            @Result(property = "lastTriggeredAt", column = "last_triggered_at"),
            @Result(property = "lastTaskId", column = "last_task_id"),
            @Result(property = "leaseOwner", column = "lease_owner"),
            @Result(property = "leaseUntil", column = "lease_until"),
            @Result(property = "version", column = "version"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    /** 按场景 ID 查询调度状态。 */
    Optional<SceneScheduleStateEntity> findBySceneId(@Param("sceneId") Long sceneId);

    @Insert("""
            insert into scene_schedule_state (
                scene_id, last_planned_fire_at, last_triggered_at, last_task_id,
                lease_owner, lease_until, version
            ) values (
                #{sceneId}, #{lastPlannedFireAt}, #{lastTriggeredAt}, #{lastTaskId},
                #{leaseOwner}, #{leaseUntil}, #{version}
            )
            """)
    /** 新增调度状态记录。 */
    int insert(SceneScheduleStateEntity entity);

    @Update("""
            update scene_schedule_state
            set last_planned_fire_at = #{lastPlannedFireAt},
                last_triggered_at = #{lastTriggeredAt},
                last_task_id = #{lastTaskId},
                lease_owner = #{leaseOwner},
                lease_until = #{leaseUntil},
                version = #{version}
            where scene_id = #{sceneId}
            """)
    /** 全量更新调度状态。 */
    int update(SceneScheduleStateEntity entity);

    @Update("""
            update scene_schedule_state
            set last_planned_fire_at = #{plannedFireAt},
                lease_owner = #{leaseOwner},
                lease_until = #{leaseUntil},
                version = version + 1
            where scene_id = #{sceneId}
              and (last_planned_fire_at is null or last_planned_fire_at &lt; #{plannedFireAt})
            """)
    /** 尝试获取场景调度租约（乐观锁，仅当 plannedFireAt 匹配时才更新成功）。 */
    int tryAcquire(@Param("sceneId") Long sceneId,
                   @Param("plannedFireAt") java.time.LocalDateTime plannedFireAt,
                   @Param("leaseOwner") String leaseOwner,
                   @Param("leaseUntil") java.time.LocalDateTime leaseUntil);

    @Update("""
            update scene_schedule_state
            set last_triggered_at = #{triggeredAt},
                last_task_id = #{taskId}
            where scene_id = #{sceneId}
              and last_planned_fire_at = #{plannedFireAt}
            """)
    /** 标记场景已触发，更新触发时间与关联任务 ID。 */
    int markTriggered(@Param("sceneId") Long sceneId,
                      @Param("plannedFireAt") java.time.LocalDateTime plannedFireAt,
                      @Param("taskId") Long taskId,
                      @Param("triggeredAt") java.time.LocalDateTime triggeredAt);

    @Delete("""
            delete from scene_schedule_state
            where scene_id = #{sceneId}
            """)
    /** 按场景 ID 删除调度状态。 */
    int deleteBySceneId(@Param("sceneId") Long sceneId);
}
