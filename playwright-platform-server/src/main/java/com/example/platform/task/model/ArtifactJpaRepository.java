package com.example.platform.task.model;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtifactJpaRepository extends JpaRepository<ArtifactEntity, Long> {
    List<ArtifactEntity> findAllByTaskIdOrderByIdAsc(Long taskId);
    List<ArtifactEntity> findAllByCaseResultIdOrderByIdAsc(Long caseResultId);
    List<ArtifactEntity> findAllByTaskIdIn(List<Long> taskIds);
    void deleteAllByTaskIdIn(List<Long> taskIds);
}
