package com.example.platform.task.service;

import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.CaseResultEntity;
import com.example.platform.task.model.TaskEntity;
import java.util.List;

public interface TaskService {
    TaskEntity createAndRun(Long sceneId);
    List<TaskEntity> list();
    TaskEntity get(Long taskId);
    String getReportUrl(Long taskId);
    List<ArtifactEntity> listArtifacts(Long taskId);
    List<CaseResultEntity> listCaseResults(Long taskId);
    List<ArtifactEntity> listArtifactsByCaseResult(Long caseResultId);
}
