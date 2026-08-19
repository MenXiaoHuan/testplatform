package com.example.platform.scene.service;

import com.example.platform.common.PageResponse;
import com.example.platform.audit.mapper.PlatformAuditLogMapper;
import com.example.platform.audit.model.PlatformAuditLogEntity;
import com.example.platform.scene.dto.ScheduleEventIssueResponse;
import com.example.platform.scene.dto.ScheduleEventRetryRequest;
import com.example.platform.scene.mapper.ScheduleEventMapper;
import com.example.platform.scene.model.ScheduleEventEntity;
import com.example.platform.task.dto.TaskRunResponse;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.service.TaskService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 调度事件管理服务实现类 —— 面向管理员的调度事件查询与手动重试操作。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #listIssueEvents} —— 按状态分页查询调度事件列表</li>
 *   <li>{@link #listEventsWithFilter} —— 通用筛选查询（支持 scheduleType 筛选）</li>
 *   <li>{@link #listEventsV2} —— V2 版本查询：带场景名 JOIN，支持 sceneName/traceId 筛选</li>
 *   <li>{@link #retryEvent} —— 手动重试指定调度事件，创建任务并记录审计日志</li>
 * </ul>
 *
 * <p>依赖：{@link ScheduleEventService}、{@link com.example.platform.task.service.TaskService}、
 * {@link SceneScheduleLeaseService}、{@link com.example.platform.audit.mapper.PlatformAuditLogMapper}。
 */
@Service
public class ScheduleEventAdminServiceImpl implements ScheduleEventAdminService {
    private final ScheduleEventService scheduleEventService;
    private final TaskService taskService;
    private final SceneScheduleLeaseService leaseService;
    private final PlatformAuditLogMapper auditLogMapper;
    private final ScheduleEventMapper scheduleEventMapper;

    public ScheduleEventAdminServiceImpl(
            ScheduleEventService scheduleEventService,
            TaskService taskService,
            SceneScheduleLeaseService leaseService,
            PlatformAuditLogMapper auditLogMapper,
            ScheduleEventMapper scheduleEventMapper) {
        this.scheduleEventService = scheduleEventService;
        this.taskService = taskService;
        this.leaseService = leaseService;
        this.auditLogMapper = auditLogMapper;
        this.scheduleEventMapper = scheduleEventMapper;
    }

    /** 按状态分页查询调度事件列表。 */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ScheduleEventIssueResponse> listIssueEvents(List<String> statuses, Long spaceId, Long sceneId, int page, int size) {
        return scheduleEventService.listIssueEvents(statuses, spaceId, sceneId, page, size)
                .map(ScheduleEventIssueResponse::from);
    }

    /** 通用筛选查询（支持 scheduleType 筛选）。 */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ScheduleEventIssueResponse> listEventsWithFilter(
            List<String> statuses, Long spaceId, Long sceneId, String scheduleType, int page, int size) {
        return scheduleEventService.listEventsWithFilter(statuses, spaceId, sceneId, scheduleType, page, size)
                .map(ScheduleEventIssueResponse::from);
    }

    /** V2 版本查询：带场景名 JOIN，支持 sceneName/traceId 筛选。 */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ScheduleEventIssueResponse> listEventsV2(
            Long spaceId, Long sceneId, String scheduleType, String sceneNameLike, String traceId, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;

        List<ScheduleEventEntity> entities = scheduleEventMapper.findEventsPageV2(
                spaceId, sceneId, scheduleType, sceneNameLike, traceId, normalizedSize, offset);
        long total = scheduleEventMapper.countEventsV2(
                spaceId, sceneId, scheduleType, sceneNameLike, traceId);

        Map<Long, String> sceneNameMap = new HashMap<>();
        for (ScheduleEventEntity e : entities) {
            if (e.getSceneId() != null) {
                sceneNameMap.put(e.getSceneId(), null);
            }
        }
        if (!sceneNameMap.isEmpty()) {
            List<java.util.Map<String, Object>> rows = scheduleEventMapper.findSceneNamesForIds(
                    List.copyOf(sceneNameMap.keySet()));
            for (java.util.Map<String, Object> row : rows) {
                Object idObj = row.get("id");
                Object nameObj = row.get("scene_name");
                if (idObj instanceof Number idNum && nameObj != null) {
                    sceneNameMap.put(idNum.longValue(), nameObj.toString());
                }
            }
        }

        List<ScheduleEventIssueResponse> items = entities.stream()
                .map(e -> ScheduleEventIssueResponse.withSceneName(e, sceneNameMap.get(e.getSceneId())))
                .toList();
        return PageResponse.of(items, total, normalizedPage, normalizedSize);
    }

    /** 手动重试指定调度事件，创建任务并记录审计日志。 */
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

    /** 记录重试操作审计日志。 */
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
