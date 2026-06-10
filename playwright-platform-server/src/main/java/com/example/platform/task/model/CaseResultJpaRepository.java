package com.example.platform.task.model;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseResultJpaRepository extends JpaRepository<CaseResultEntity, Long> {
    List<CaseResultEntity> findAllByTaskIdOrderByIdAsc(Long taskId);
}
