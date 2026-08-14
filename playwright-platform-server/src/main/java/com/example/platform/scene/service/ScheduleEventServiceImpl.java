package com.example.platform.scene.service;

import com.example.platform.common.PageResponse;
import com.example.platform.scene.mapper.ScheduleEventMapper;
import com.example.platform.scene.model.ScheduleEventEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleEventServiceImpl implements ScheduleEventService {
    static final String FAILURE_CATEGORY_RETRYABLE_SYSTEM = "RETRYABLE_SYSTEM";
    static final String FAILURE_CATEGORY_RETRYABLE_CONFLICT = "RETRYABLE_CONFLICT";
    static final String FAILURE_CATEGORY_NON_RETRYABLE_CONFIG = "NON_RETRYABLE_CONFIG";
    static final String FAILURE_CATEGORY_NON_RETRYABLE_RESOURCE = "NON_RETRYABLE_RESOURCE";
    private final ScheduleEventMapper mapper;
    private final int maxRetries;
    private final int retryDelaySeconds;

    public ScheduleEventServiceImpl(
            ScheduleEventMapper mapper,
            @Value("${platform.scheduler.retry.max-retries:3}") int maxRetries,
            @Value("${platform.scheduler.retry.delay-seconds:60}") int retryDelaySeconds) {
        this.mapper = mapper;
        this.maxRetries = maxRetries;
        this.retryDelaySeconds = retryDelaySeconds;
    }

    @Override
    @Transactional
    public Optional<ScheduleEventEntity> createAcquiredEvent(Long sceneId, LocalDateTime plannedFireAt, String triggerReason) {
        ScheduleEventEntity entity = new ScheduleEventEntity();
        entity.setSceneId(sceneId);
        entity.setPlannedFireAt(plannedFireAt);
        entity.setStatus("ACQUIRED");
        entity.setTriggerReason(triggerReason);
        entity.setRetryCount(0);
        try {
            mapper.insert(entity);
            return Optional.of(entity);
        } catch (DuplicateKeyException exception) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ScheduleEventEntity> get(Long eventId) {
        return mapper.findById(eventId);
    }

    @Override
    @Transactional
    public Optional<ScheduleEventEntity> startRetry(Long eventId) {
        if (mapper.tryStartRetry(eventId) == 0) {
            return Optional.empty();
        }
        return mapper.findById(eventId);
    }

    @Override
    @Transactional
    public void markTaskCreated(Long eventId, Long taskId) {
        ScheduleEventEntity entity = mapper.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule event not found: " + eventId));
        entity.setStatus("TASK_CREATED");
        entity.setTaskId(taskId);
        mapper.update(entity);
    }

    @Override
    @Transactional
    public void markFailed(Long eventId, String errorMessage, String failureCategory) {
        ScheduleEventEntity entity = mapper.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule event not found: " + eventId));
        int nextRetryCount = (entity.getRetryCount() == null ? 0 : entity.getRetryCount()) + 1;
        entity.setRetryCount(nextRetryCount);
        entity.setErrorMessage(errorMessage);
        entity.setFailureCategory(failureCategory);
        entity.setLastErrorAt(LocalDateTime.now());
        if (isNonRetryable(failureCategory)) {
            entity.setStatus("ABANDONED");
            entity.setNextRetryAt(null);
        } else if (nextRetryCount >= maxRetries) {
            entity.setStatus("ABANDONED");
            entity.setNextRetryAt(null);
        } else {
            entity.setStatus("FAILED");
            entity.setNextRetryAt(entity.getLastErrorAt().plusSeconds(retryDelaySeconds));
        }
        mapper.update(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleEventEntity> listRetryableFailedEvents(int limit, LocalDateTime now) {
        return mapper.findRetryableFailedEvents(limit, now, maxRetries);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ScheduleEventEntity> listIssueEvents(List<String> statuses, Long spaceId, Long sceneId, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        if (spaceId != null && sceneId != null) {
            return PageResponse.of(
                    mapper.findIssueEventsPageBySpaceIdAndSceneId(statuses, spaceId, sceneId, normalizedSize, offset),
                    mapper.countIssueEventsBySpaceIdAndSceneId(statuses, spaceId, sceneId),
                    normalizedPage,
                    normalizedSize);
        }
        if (spaceId != null) {
            return PageResponse.of(
                    mapper.findIssueEventsPageBySpaceId(statuses, spaceId, normalizedSize, offset),
                    mapper.countIssueEventsBySpaceId(statuses, spaceId),
                    normalizedPage,
                    normalizedSize);
        }
        return PageResponse.of(
                mapper.findIssueEventsPage(statuses, sceneId, normalizedSize, offset),
                mapper.countIssueEvents(statuses, sceneId),
                normalizedPage,
                normalizedSize);
    }

    private boolean isNonRetryable(String failureCategory) {
        return FAILURE_CATEGORY_NON_RETRYABLE_CONFIG.equals(failureCategory)
                || FAILURE_CATEGORY_NON_RETRYABLE_RESOURCE.equals(failureCategory);
    }

    @Override
    @Transactional
    public Long createAgentEvent(Long spaceId, String traceId, String sessionId, String userMessage) {
        ScheduleEventEntity entity = new ScheduleEventEntity();
        entity.setSpaceId(spaceId);
        entity.setPlannedFireAt(LocalDateTime.now());
        entity.setStatus("RUNNING");
        entity.setScheduleType("AGENT");
        entity.setTraceId(traceId);
        entity.setSessionId(sessionId);
        entity.setUserMessage(userMessage != null && userMessage.length() > 1024
                ? userMessage.substring(0, 1024) : userMessage);
        entity.setTriggerReason("AI_AGENT_CHAT");
        entity.setRetryCount(0);
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional
    public void completeAgentEvent(Long eventId, boolean success, String errorMessage) {
        if (eventId == null) {
            return;
        }
        ScheduleEventEntity entity = mapper.findById(eventId).orElse(null);
        if (entity == null) {
            return;
        }
        String status = success ? "COMPLETED" : "FAILED";
        String truncatedError = errorMessage != null && errorMessage.length() > 1024
                ? errorMessage.substring(0, 1024) : errorMessage;
        mapper.updateStatus(eventId, status, truncatedError, success ? null : "AGENT_ERROR", LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ScheduleEventEntity> listEventsWithFilter(
            List<String> statuses, Long spaceId, Long sceneId, String scheduleType, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        return PageResponse.of(
                mapper.findEventsPageWithFilter(spaceId, sceneId, statuses, scheduleType, normalizedSize, offset),
                mapper.countEventsWithFilter(spaceId, sceneId, statuses, scheduleType),
                normalizedPage,
                normalizedSize);
    }
}
