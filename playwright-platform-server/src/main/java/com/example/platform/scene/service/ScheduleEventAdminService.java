package com.example.platform.scene.service;

import com.example.platform.common.PageResponse;
import com.example.platform.scene.dto.ScheduleEventIssueResponse;
import com.example.platform.scene.dto.ScheduleEventRetryRequest;
import com.example.platform.scene.model.ScheduleEventEntity;
import com.example.platform.task.dto.TaskRunResponse;
import java.util.List;

public interface ScheduleEventAdminService {
    PageResponse<ScheduleEventIssueResponse> listIssueEvents(List<String> statuses, Long spaceId, Long sceneId, int page, int size);
    PageResponse<ScheduleEventIssueResponse> listEventsWithFilter(List<String> statuses, Long spaceId, Long sceneId, String scheduleType, int page, int size);
    PageResponse<ScheduleEventIssueResponse> listEventsV2(Long spaceId, Long sceneId, String scheduleType, String sceneNameLike, String traceId, int page, int size);
    TaskRunResponse retryEvent(Long spaceId, Long eventId, ScheduleEventRetryRequest request);
}
