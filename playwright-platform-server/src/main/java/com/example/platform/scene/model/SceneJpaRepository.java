package com.example.platform.scene.model;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SceneJpaRepository extends JpaRepository<SceneEntity, Long> {
    List<SceneEntity> findAllByOrderByUpdatedAtDescIdDesc();
    List<SceneEntity> findAllByScheduleEnabledTrue();
    List<SceneEntity> findAllByRepoId(Long repoId);
    void deleteAllByRepoId(Long repoId);
}
