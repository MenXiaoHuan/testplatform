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
}
