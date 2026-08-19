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

/**
 * 任务服务接口。
 *
 * <p>核心职责：
 * <ul>
 *   <li>定义任务的创建、启动、取消等命令操作</li>
 *   <li>定义任务的查询、详情、诊断等读操作</li>
 *   <li>定义制品和用例结果的查询与下载操作</li>
 * </ul>
 *
 * <p>依赖：{@link TaskEntity}、{@link TaskDetailResponse}、{@link CaseResultResponse} 等
 */
public interface TaskService {

    /**
     * 创建并启动任务（场景级）
     *
     * @param sceneId 场景ID
     * @return 创建的任务实体
     */
    TaskEntity createAndStart(Long sceneId);

    /**
     * 创建并启动任务（空间+场景级）
     *
     * @param spaceId 空间ID
     * @param sceneId 场景ID
     * @return 创建的任务实体
     */
    TaskEntity createAndStart(Long spaceId, Long sceneId);

    /**
     * 创建并立即运行任务（同步执行）
     *
     * @param sceneId 场景ID
     * @return 创建的任务实体
     */
    TaskEntity createAndRun(Long sceneId);

    /**
     * 创建定时调度任务
     *
     * @param sceneId 场景ID
     * @param triggerReason 触发原因
     * @return 创建的任务实体
     */
    TaskEntity createScheduledTask(Long sceneId, String triggerReason);

    /**
     * 分页查询任务列表
     *
     * @param page 页码
     * @param size 每页数量
     * @return 分页响应
     */
    PageResponse<SceneTaskListResponse> list(int page, int size);

    /**
     * 分页查询指定空间的任务列表
     *
     * @param spaceId 空间ID
     * @param page 页码
     * @param size 每页数量
     * @return 分页响应
     */
    PageResponse<SceneTaskListResponse> list(Long spaceId, int page, int size);

    /**
     * 分页查询指定场景的任务列表
     *
     * @param sceneId 场景ID
     * @param page 页码
     * @param size 每页数量
     * @return 分页响应
     */
    PageResponse<SceneTaskListResponse> listByScene(Long sceneId, int page, int size);

    /**
     * 分页查询指定空间下场景的任务列表
     *
     * @param spaceId 空间ID
     * @param sceneId 场景ID
     * @param page 页码
     * @param size 每页数量
     * @return 分页响应
     */
    PageResponse<SceneTaskListResponse> listByScene(Long spaceId, Long sceneId, int page, int size);

    /**
     * 获取任务详情
     *
     * @param taskId 任务ID
     * @return 任务详情响应
     */
    TaskDetailResponse getDetail(Long taskId);

    /**
     * 获取指定空间的任务详情
     *
     * @param spaceId 空间ID
     * @param taskId 任务ID
     * @return 任务详情响应
     */
    TaskDetailResponse getDetail(Long spaceId, Long taskId);

    /**
     * 获取任务诊断信息
     *
     * @param taskId 任务ID
     * @return 任务诊断响应
     */
    TaskDiagnosticsResponse getDiagnostics(Long taskId);

    /**
     * 获取指定空间的任务诊断信息
     *
     * @param spaceId 空间ID
     * @param taskId 任务ID
     * @return 任务诊断响应
     */
    TaskDiagnosticsResponse getDiagnostics(Long spaceId, Long taskId);

    /**
     * 获取任务实体
     *
     * @param taskId 任务ID
     * @return 任务实体
     */
    TaskEntity get(Long taskId);

    /**
     * 获取指定空间的任务实体
     *
     * @param spaceId 空间ID
     * @param taskId 任务ID
     * @return 任务实体
     */
    TaskEntity get(Long spaceId, Long taskId);

    /**
     * 列出任务的所有制品
     *
     * @param taskId 任务ID
     * @return 制品列表
     */
    List<ArtifactEntity> listArtifacts(Long taskId);

    /**
     * 列出指定空间任务的所有制品
     *
     * @param spaceId 空间ID
     * @param taskId 任务ID
     * @return 制品列表
     */
    List<ArtifactEntity> listArtifacts(Long spaceId, Long taskId);

    /**
     * 列出任务的所有用例结果响应
     *
     * @param taskId 任务ID
     * @return 用例结果响应列表
     */
    List<CaseResultResponse> listCaseResultResponses(Long taskId);

    /**
     * 列出指定空间任务的所有用例结果响应
     *
     * @param spaceId 空间ID
     * @param taskId 任务ID
     * @return 用例结果响应列表
     */
    List<CaseResultResponse> listCaseResultResponses(Long spaceId, Long taskId);

    /**
     * 列出任务的所有用例结果实体
     *
     * @param taskId 任务ID
     * @return 用例结果实体列表
     */
    List<CaseResultEntity> listCaseResults(Long taskId);

    /**
     * 根据用例结果ID列出制品
     *
     * @param caseResultId 用例结果ID
     * @return 制品列表
     */
    List<ArtifactEntity> listArtifactsByCaseResult(Long caseResultId);

    /**
     * 根据用例结果ID列出制品（空间级）
     *
     * @param spaceId 空间ID
     * @param taskId 任务ID
     * @param caseResultId 用例结果ID
     * @return 制品列表
     */
    List<ArtifactEntity> listArtifactsByCaseResult(Long spaceId, Long taskId, Long caseResultId);

    /**
     * 取消任务
     *
     * @param taskId 任务ID
     * @param operatorName 操作人名称
     */
    void cancelTask(Long taskId, String operatorName);

    /**
     * 取消指定空间的任务
     *
     * @param spaceId 空间ID
     * @param taskId 任务ID
     * @param operatorName 操作人名称
     */
    void cancelTask(Long spaceId, Long taskId, String operatorName);

    /**
     * 列出任务的所有阶段日志
     *
     * @param taskId 任务ID
     * @return 阶段日志响应列表
     */
    List<TaskStageLogResponse> listStageLogs(Long taskId);

    /**
     * 列出指定空间任务的所有阶段日志
     *
     * @param spaceId 空间ID
     * @param taskId 任务ID
     * @return 阶段日志响应列表
     */
    List<TaskStageLogResponse> listStageLogs(Long spaceId, Long taskId);

    /**
     * 下载制品文件
     *
     * @param taskId 任务ID
     * @param artifactId 制品ID
     * @return 文件响应
     */
    ResponseEntity<Resource> downloadArtifact(Long taskId, Long artifactId);

    /**
     * 下载指定空间任务的制品文件
     *
     * @param spaceId 空间ID
     * @param taskId 任务ID
     * @param artifactId 制品ID
     * @return 文件响应
     */
    ResponseEntity<Resource> downloadArtifact(Long spaceId, Long taskId, Long artifactId);

    /**
     * 下载阶段日志文件
     *
     * @param taskId 任务ID
     * @param stageLogId 阶段日志ID
     * @return 文件响应
     */
    ResponseEntity<Resource> downloadStageLog(Long taskId, Long stageLogId);

    /**
     * 下载指定空间任务的阶段日志文件
     *
     * @param spaceId 空间ID
     * @param taskId 任务ID
     * @param stageLogId 阶段日志ID
     * @return 文件响应
     */
    ResponseEntity<Resource> downloadStageLog(Long spaceId, Long taskId, Long stageLogId);
}
