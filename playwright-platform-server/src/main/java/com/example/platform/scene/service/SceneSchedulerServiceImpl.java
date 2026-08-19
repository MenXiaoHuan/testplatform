package com.example.platform.scene.service;

import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.model.ScheduleEventEntity;
import com.example.platform.task.service.TaskService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 场景调度服务实现类 —— 定时触发到期场景、失败事件重试与遗留场景初始化。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #triggerDueScenes} —— 触发所有到期场景：先重试失败事件 → 初始化遗留场景 → 触发到期场景</li>
 *   <li>{@link #retryFailedScheduleEvents} —— 重试失败的调度事件（最多 20 条）</li>
 *   <li>{@link #classifyFailureCategory} —— 根据异常消息分类失败原因（可重试系统/冲突、不可重试配置/资源）</li>
 *   <li>{@link #initializeNextRunAtForLegacyScenes} —— 为历史遗留场景初始化 next_run_at</li>
 * </ul>
 *
 * <p>依赖：{@link SceneMapper}、{@link SceneScheduleLeaseService}、
 * {@link ScheduleEventService}、{@link com.example.platform.task.service.TaskService}。
 */
@Service
public class SceneSchedulerServiceImpl implements SceneSchedulerService {
    private static final Logger log = LoggerFactory.getLogger(SceneSchedulerServiceImpl.class);
    private static final int FAILED_EVENT_RETRY_LIMIT = 20;

    private final SceneMapper sceneMapper;
    private final SceneScheduleLeaseService leaseService;
    private final ScheduleEventService scheduleEventService;
    private final TaskService taskService;
    private final SceneScheduleTimeResolver sceneScheduleTimeResolver = new SceneScheduleTimeResolver();

    public SceneSchedulerServiceImpl(
            SceneMapper sceneMapper,
            SceneScheduleLeaseService leaseService,
            ScheduleEventService scheduleEventService,
            TaskService taskService) {
        this.sceneMapper = sceneMapper;
        this.leaseService = leaseService;
        this.scheduleEventService = scheduleEventService;
        this.taskService = taskService;
    }

    /** 触发所有到期场景：重试失败事件 → 初始化遗留场景 → 触发到期场景。 */
    @Override
    @Transactional
    public void triggerDueScenes(LocalDateTime now) {
        retryFailedScheduleEvents(now);
        initializeNextRunAtForLegacyScenes(now);
        for (SceneEntity scene : sceneMapper.findDueScheduledScenes(now)) {
            LocalDateTime plannedFireAt = scene.getNextRunAt();
            if (plannedFireAt == null) {
                continue;
            }
            if (!leaseService.tryAcquire(scene.getId(), plannedFireAt)) {
                continue;
            }
            String cronExpression = scene.getCronExpression();
            Optional<ScheduleEventEntity> scheduleEvent = scheduleEventService.createAcquiredEvent(
                    scene.getId(),
                    plannedFireAt,
                    "cron:" + cronExpression);
            if (scheduleEvent.isEmpty()) {
                continue;
            }
            scene.setNextRunAt(sceneScheduleTimeResolver.resolveNextRunAfter(cronExpression, plannedFireAt));
            sceneMapper.update(scene);
            try {
                var task = taskService.createScheduledTask(scene.getId(), "cron:" + cronExpression);
                scheduleEventService.markTaskCreated(scheduleEvent.orElseThrow().getId(), task.getId());
                leaseService.markTriggered(scene.getId(), plannedFireAt, task.getId(), now);
            } catch (RuntimeException exception) {
                scheduleEventService.markFailed(
                        scheduleEvent.orElseThrow().getId(),
                        exception.getMessage(),
                        classifyFailureCategory(exception));
                log.warn(
                        "Failed to create scheduled task. sceneId={}, cron={}, reason={}",
                        scene.getId(),
                        cronExpression,
                        exception.getMessage());
            }
        }
    }

    /** 重试失败的调度事件（最多 20 条），成功创建任务后标记触发。 */
    private void retryFailedScheduleEvents(LocalDateTime now) {
        for (ScheduleEventEntity event : scheduleEventService.listRetryableFailedEvents(FAILED_EVENT_RETRY_LIMIT, now)) {
            Optional<ScheduleEventEntity> claimed = scheduleEventService.startRetry(event.getId());
            if (claimed.isEmpty()) {
                continue;
            }
            try {
                var retryingEvent = claimed.orElseThrow();
                var task = taskService.createScheduledTask(retryingEvent.getSceneId(), retryingEvent.getTriggerReason());
                scheduleEventService.markTaskCreated(retryingEvent.getId(), task.getId());
                leaseService.markTriggered(retryingEvent.getSceneId(), retryingEvent.getPlannedFireAt(), task.getId(), now);
            } catch (RuntimeException exception) {
                scheduleEventService.markFailed(
                        event.getId(),
                        exception.getMessage(),
                        classifyFailureCategory(exception));
                log.warn(
                        "Failed to retry schedule event. eventId={}, sceneId={}, reason={}",
                        event.getId(),
                        event.getSceneId(),
                        exception.getMessage());
            }
        }
    }

    /** 根据异常消息分类失败原因：可重试系统繁忙/冲突、不可重试配置/资源问题。 */
    static String classifyFailureCategory(RuntimeException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage().trim();
        String lowerCaseMessage = message.toLowerCase();
        if (lowerCaseMessage.contains("system busy")
                || lowerCaseMessage.contains("队列已满")
                || lowerCaseMessage.contains("系统繁忙")) {
            return ScheduleEventServiceImpl.FAILURE_CATEGORY_RETRYABLE_SYSTEM;
        }
        if (lowerCaseMessage.contains("已有执行中的任务")
                || lowerCaseMessage.contains("active task")
                || lowerCaseMessage.contains("conflict")) {
            return ScheduleEventServiceImpl.FAILURE_CATEGORY_RETRYABLE_CONFLICT;
        }
        if (lowerCaseMessage.contains("not found")
                || lowerCaseMessage.contains("不存在")
                || lowerCaseMessage.contains("停用")
                || lowerCaseMessage.contains("invalid")
                || lowerCaseMessage.contains("cron")) {
            return ScheduleEventServiceImpl.FAILURE_CATEGORY_NON_RETRYABLE_CONFIG;
        }
        return ScheduleEventServiceImpl.FAILURE_CATEGORY_NON_RETRYABLE_RESOURCE;
    }

    /** 为遗留的已启用调度场景初始化 next_run_at。 */
    private void initializeNextRunAtForLegacyScenes(LocalDateTime now) {
        for (SceneEntity scene : sceneMapper.findAllByScheduleEnabledTrueAndNextRunAtIsNullOrderByIdAsc()) {
            LocalDateTime nextRunAt = sceneScheduleTimeResolver.resolveNextRunAt(
                    scene.getScheduleEnabled(),
                    scene.getCronExpression(),
                    now);
            if (nextRunAt != null) {
                scene.setNextRunAt(nextRunAt);
                sceneMapper.update(scene);
            }
        }
    }
}
