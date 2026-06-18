package com.example.platform.scene.mapper;

import com.example.platform.scene.model.SceneScheduleStateEntity;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
    int update(SceneScheduleStateEntity entity);
}
