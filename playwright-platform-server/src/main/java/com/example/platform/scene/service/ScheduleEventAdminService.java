package com.example.platform.scene.service;

import com.example.platform.common.PageResponse;
import com.example.platform.scene.dto.ScheduleEventIssueResponse;
import com.example.platform.scene.dto.ScheduleEventRetryRequest;
import com.example.platform.scene.model.ScheduleEventEntity;
import com.example.platform.task.dto.TaskRunResponse;
import java.util.List;

/**
 * 调度事件管理服务接口 —— 面向管理员的调度事件查询与手动重试操作。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #listIssueEvents} —— 按状态分页查询调度事件列表</li>
 *   <li>{@link #listEventsWithFilter} —— 通用筛选查询（支持 scheduleType 筛选）</li>
 *   <li>{@link #listEventsV2} —— V2 版本查询（带场景名称 JOIN，支持 sceneName/traceId 筛选）</li>
 *   <li>{@link #retryEvent} —— 手动重试指定调度事件</li>
 * </ul>
 */
public interface ScheduleEventAdminService {
    PageResponse<ScheduleEventIssueResponse> listIssueEvents(List<String> statuses, Long spaceId, Long sceneId, int page, int size);
    PageResponse<ScheduleEventIssueResponse> listEventsWithFilter(List<String> statuses, Long spaceId, Long sceneId, String scheduleType, int page, int size);
    PageResponse<ScheduleEventIssueResponse> listEventsV2(Long spaceId, Long sceneId, String scheduleType, String sceneNameLike, String traceId, int page, int size);
    TaskRunResponse retryEvent(Long spaceId, Long eventId, ScheduleEventRetryRequest request);
}
