package com.example.platform.task.model;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskJpaRepository extends JpaRepository<TaskEntity, Long> {
    List<TaskEntity> findAllBySceneIdOrderByCreatedAtDescIdDesc(Long sceneId);
    List<TaskEntity> findAllByOrderByCreatedAtDescIdDesc();
    List<TaskEntity> findAllByRepoIdOrderByIdAsc(Long repoId);
    List<TaskEntity> findAllBySceneIdOrderByIdAsc(Long sceneId);
    Page<TaskEntity> findAllBySceneId(Long sceneId, Pageable pageable);
    java.util.Optional<TaskEntity> findFirstBySceneIdOrderByCreatedAtDescIdDesc(Long sceneId);
    void deleteAllByRepoId(Long repoId);
    void deleteAllBySceneId(Long sceneId);
}
