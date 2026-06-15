package com.example.platform.task.model;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskStageLogJpaRepository extends JpaRepository<TaskStageLogEntity, Long> {
    List<TaskStageLogEntity> findAllByTaskIdOrderByIdAsc(Long taskId);
    List<TaskStageLogEntity> findAllByTaskIdIn(List<Long> taskIds);
    void deleteAllByTaskIdIn(List<Long> taskIds);
}
