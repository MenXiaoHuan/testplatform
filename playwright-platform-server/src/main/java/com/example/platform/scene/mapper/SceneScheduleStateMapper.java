package com.example.platform.scene.mapper;

import com.example.platform.scene.model.SceneScheduleStateEntity;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

public interface SceneScheduleStateMapper {
    Optional<SceneScheduleStateEntity> findBySceneId(@Param("sceneId") Long sceneId);

    int insert(SceneScheduleStateEntity entity);

    int update(SceneScheduleStateEntity entity);
}
