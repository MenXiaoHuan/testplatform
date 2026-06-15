package com.example.platform.task.model;

import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskJpaRepository extends JpaRepository<TaskEntity, Long> {
    List<TaskEntity> findAllBySceneIdOrderByCreatedAtDescIdDesc(Long sceneId);
    List<TaskEntity> findAllByStatusInOrderByCreatedAtAscIdAsc(Collection<String> statuses);
    List<TaskEntity> findAllByOrderByCreatedAtDescIdDesc();
    List<TaskEntity> findAllByRepoIdOrderByIdAsc(Long repoId);
    List<TaskEntity> findAllBySceneIdOrderByIdAsc(Long sceneId);
    Page<TaskEntity> findAllBySceneId(Long sceneId, Pageable pageable);
    java.util.Optional<TaskEntity> findFirstBySceneIdOrderByCreatedAtDescIdDesc(Long sceneId);
    boolean existsBySceneIdAndStatusIn(Long sceneId, Collection<String> statuses);
    void deleteAllByRepoId(Long repoId);
    void deleteAllBySceneId(Long sceneId);
}
