package com.example.platform.scene.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SceneJpaRepository extends JpaRepository<SceneEntity, Long> {
    List<SceneEntity> findAllByOrderByUpdatedAtDescIdDesc();
    List<SceneEntity> findAllByScheduleEnabledTrue();
    List<SceneEntity> findAllByScheduleEnabledTrueAndNextRunAtIsNullOrderByIdAsc();
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select scene from SceneEntity scene where scene.id = :id")
    Optional<SceneEntity> findByIdForUpdate(@Param("id") Long id);
    @Query("""
            select scene
            from SceneEntity scene
            where scene.scheduleEnabled = true
              and scene.nextRunAt is not null
              and scene.nextRunAt <= :now
            order by scene.nextRunAt asc, scene.id asc
            """)
    List<SceneEntity> findDueScheduledScenes(@Param("now") LocalDateTime now);
    List<SceneEntity> findAllByRepoId(Long repoId);
    void deleteAllByRepoId(Long repoId);
}
