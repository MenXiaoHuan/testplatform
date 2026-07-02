package com.example.platform.scene.service;

import com.example.platform.common.PageResponse;
import com.example.platform.audit.mapper.PlatformAuditLogMapper;
import com.example.platform.audit.model.PlatformAuditLogEntity;
import com.example.platform.scene.dto.ScheduleEventIssueResponse;
import com.example.platform.scene.dto.ScheduleEventRetryRequest;
import com.example.platform.scene.model.ScheduleEventEntity;
import com.example.platform.task.dto.TaskRunResponse;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.service.TaskService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScheduleEventAdminServiceImpl implements ScheduleEventAdminService {
    private final ScheduleEventService scheduleEventService;
    private final TaskService taskService;
    private final SceneScheduleLeaseService leaseService;
    private final PlatformAuditLogMapper auditLogMapper;

    public ScheduleEventAdminServiceImpl(
            ScheduleEventService scheduleEventService,
            TaskService taskService,
            SceneScheduleLeaseService leaseService,
            PlatformAuditLogMapper auditLogMapper) {
        this.scheduleEventService = scheduleEventService;
        this.taskService = taskService;
        this.leaseService = leaseService;
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ScheduleEventIssueResponse> listIssueEvents(List<String> statuses, Long spaceId, Long sceneId, int page, int size) {
        return scheduleEventService.listIssueEvents(statuses, spaceId, sceneId, page, size)
                .map(ScheduleEventIssueResponse::from);
    }

    @Override
    @Transactional
    public TaskRunResponse retryEvent(Long spaceId, Long eventId, ScheduleEventRetryRequest request) {
        ScheduleEventEntity event = scheduleEventService.get(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule event not found: " + eventId));
        if (spaceId != null && !spaceId.equals(event.getSpaceId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule event not found: " + eventId);
        }
        String status = event.getStatus() == null ? "" : event.getStatus().trim().toUpperCase();
        if (!"FAILED".equals(status) && !"ABANDONED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "only FAILED or ABANDONED events can be retried");
        }
        ScheduleEventEntity retryingEvent = scheduleEventService.startRetry(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "retry already in progress or event state changed"));
        try {
            TaskEntity task = taskService.createScheduledTask(retryingEvent.getSceneId(), retryingEvent.getTriggerReason());
            scheduleEventService.markTaskCreated(eventId, task.getId());
            leaseService.markTriggered(retryingEvent.getSceneId(), retryingEvent.getPlannedFireAt(), task.getId(), LocalDateTime.now());
            recordRetryAudit(retryingEvent, task, request);
            return TaskRunResponse.from(task);
        } catch (RuntimeException exception) {
            scheduleEventService.markFailed(
                    eventId,
                    exception.getMessage(),
                    SceneSchedulerServiceImpl.classifyFailureCategory(exception));
            throw exception;
        }
    }

    private void recordRetryAudit(ScheduleEventEntity event, TaskEntity task, ScheduleEventRetryRequest request) {
        PlatformAuditLogEntity auditLog = new PlatformAuditLogEntity();
        auditLog.setEntityType("SCHEDULE_EVENT");
        auditLog.setEntityId(event.getId());
        auditLog.setAction("RETRY");
        auditLog.setOperatorName(normalizeOperatorName(request == null ? null : request.operatorName()));
        auditLog.setDetailJson("""
                {"sceneId":%d,"plannedFireAt":"%s","taskId":%d,"triggerReason":"%s","operatorId":"%s","comment":"%s"}
                """.formatted(
                event.getSceneId(),
                event.getPlannedFireAt(),
                task.getId(),
                event.getTriggerReason(),
                normalizeOptional(request == null ? null : request.operatorId()),
                normalizeOptional(request == null ? null : request.comment())));
        auditLogMapper.insert(auditLog);
    }

    private String normalizeOperatorName(String operatorName) {
        if (operatorName == null || operatorName.isBlank()) {
            return "anonymous";
        }
        return operatorName.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }
}
