package com.example.platform.task.service;

import com.example.platform.common.PageResponse;
import com.example.platform.task.dto.TaskReportSummaryResponse;
import com.example.platform.task.dto.SceneTaskListResponse;
import com.example.platform.task.dto.CaseResultResponse;
import com.example.platform.task.dto.TaskDetailResponse;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.CaseResultEntity;
import com.example.platform.task.model.TaskEntity;
import java.util.List;

public interface TaskService {
    TaskEntity createAndStart(Long sceneId);
    TaskEntity createAndRun(Long sceneId);
    PageResponse<SceneTaskListResponse> list(int page, int size);
    PageResponse<SceneTaskListResponse> listByScene(Long sceneId, int page, int size);
    TaskDetailResponse getDetail(Long taskId);
    TaskEntity get(Long taskId);
    TaskReportSummaryResponse getReportSummary(Long taskId);
    String getReportUrl(Long taskId);
    List<ArtifactEntity> listArtifacts(Long taskId);
    List<CaseResultResponse> listCaseResultResponses(Long taskId);
    List<CaseResultEntity> listCaseResults(Long taskId);
    List<ArtifactEntity> listArtifactsByCaseResult(Long caseResultId);
}
