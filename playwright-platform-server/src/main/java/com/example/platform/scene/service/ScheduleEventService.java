package com.example.platform.scene.service;

import com.example.platform.common.PageResponse;
import com.example.platform.scene.model.ScheduleEventEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduleEventService {
    Optional<ScheduleEventEntity> createAcquiredEvent(Long sceneId, LocalDateTime plannedFireAt, String triggerReason);
    Optional<ScheduleEventEntity> get(Long eventId);
    Optional<ScheduleEventEntity> startRetry(Long eventId);
    void markTaskCreated(Long eventId, Long taskId);
    void markFailed(Long eventId, String errorMessage, String failureCategory);
    List<ScheduleEventEntity> listRetryableFailedEvents(int limit, LocalDateTime now);
    PageResponse<ScheduleEventEntity> listIssueEvents(List<String> statuses, Long spaceId, Long sceneId, int page, int size);

    /** 创建一条 Agent 调度事件（schedule_type=AGENT, status=RUNNING），返回事件 ID。 */
    Long createAgentEvent(Long spaceId, String traceId, String sessionId, String userMessage);

    /** 更新 Agent 调度事件最终状态：成功 COMPLETED / 失败 FAILED。 */
    void completeAgentEvent(Long eventId, boolean success, String errorMessage);

    /** 通用筛选查询（支持 scheduleType 筛选）。scheduleType 为 null/空 表示不筛选类型。 */
    PageResponse<ScheduleEventEntity> listEventsWithFilter(
            List<String> statuses, Long spaceId, Long sceneId, String scheduleType, int page, int size);
}
