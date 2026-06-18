package com.example.platform.scene.mapper;

import com.example.platform.scene.model.SceneEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

public interface SceneMapper {
    int insert(SceneEntity entity);

    int update(SceneEntity entity);

    Optional<SceneEntity> findById(@Param("id") Long id);

    Optional<SceneEntity> findByIdForUpdate(@Param("id") Long id);

    List<SceneEntity> findPage(@Param("limit") int limit, @Param("offset") int offset);

    long countAll();

    boolean existsByNameIgnoreCase(@Param("name") String name);

    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("id") Long id);

    List<SceneEntity> findAllByRepoId(@Param("repoId") Long repoId);

    List<SceneEntity> findAllByScheduleEnabledTrue();

    List<SceneEntity> findDueScheduledScenes(@Param("now") LocalDateTime now);

    List<SceneEntity> findAllByScheduleEnabledTrueAndNextRunAtIsNullOrderByIdAsc();

    int deleteById(@Param("id") Long id);

    int deleteAllByRepoId(@Param("repoId") Long repoId);
}
