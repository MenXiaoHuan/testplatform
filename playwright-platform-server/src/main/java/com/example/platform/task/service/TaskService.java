package com.example.platform.task.service;

import com.example.platform.common.PageResponse;
import com.example.platform.task.dto.SceneTaskListResponse;
import com.example.platform.task.dto.CaseResultResponse;
import com.example.platform.task.dto.TaskDetailResponse;
import com.example.platform.task.dto.TaskDiagnosticsResponse;
import com.example.platform.task.dto.TaskStageLogResponse;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.CaseResultEntity;
import com.example.platform.task.model.TaskEntity;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

public interface TaskService {
    TaskEntity createAndStart(Long sceneId);
    TaskEntity createAndStart(Long spaceId, Long sceneId);
    TaskEntity createAndRun(Long sceneId);
    TaskEntity createScheduledTask(Long sceneId, String triggerReason);
    PageResponse<SceneTaskListResponse> list(int page, int size);
    PageResponse<SceneTaskListResponse> list(Long spaceId, int page, int size);
    PageResponse<SceneTaskListResponse> listByScene(Long sceneId, int page, int size);
    PageResponse<SceneTaskListResponse> listByScene(Long spaceId, Long sceneId, int page, int size);
    TaskDetailResponse getDetail(Long taskId);
    TaskDetailResponse getDetail(Long spaceId, Long taskId);
    TaskDiagnosticsResponse getDiagnostics(Long taskId);
    TaskDiagnosticsResponse getDiagnostics(Long spaceId, Long taskId);
    TaskEntity get(Long taskId);
    TaskEntity get(Long spaceId, Long taskId);
    List<ArtifactEntity> listArtifacts(Long taskId);
    List<ArtifactEntity> listArtifacts(Long spaceId, Long taskId);
    List<CaseResultResponse> listCaseResultResponses(Long taskId);
    List<CaseResultResponse> listCaseResultResponses(Long spaceId, Long taskId);
    List<CaseResultEntity> listCaseResults(Long taskId);
    List<ArtifactEntity> listArtifactsByCaseResult(Long caseResultId);
    List<ArtifactEntity> listArtifactsByCaseResult(Long spaceId, Long taskId, Long caseResultId);
    void cancelTask(Long taskId, String operatorName);
    void cancelTask(Long spaceId, Long taskId, String operatorName);
    List<TaskStageLogResponse> listStageLogs(Long taskId);
    List<TaskStageLogResponse> listStageLogs(Long spaceId, Long taskId);
    ResponseEntity<Resource> downloadArtifact(Long taskId, Long artifactId);
    ResponseEntity<Resource> downloadArtifact(Long spaceId, Long taskId, Long artifactId);
    ResponseEntity<Resource> downloadStageLog(Long taskId, Long stageLogId);
    ResponseEntity<Resource> downloadStageLog(Long spaceId, Long taskId, Long stageLogId);
}
