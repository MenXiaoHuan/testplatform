package com.example.platform.scene.service;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;

/**
 * 场景调度时间解析器 —— 基于 Spring CronExpression 解析 cron 表达式，计算下次运行时间。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #resolveNextRunAt} —— 根据调度启用状态和 cron 表达式计算下次运行时间</li>
 *   <li>{@link #resolveNextRunAfter} —— 解析 cron 表达式获取基准时间之后的下次触发时间</li>
 * </ul>
 */
final class SceneScheduleTimeResolver {
    private static final Logger log = LoggerFactory.getLogger(SceneScheduleTimeResolver.class);

    /** 根据调度启用状态和 cron 表达式计算下次运行时间，未启用则返回 null。 */
    LocalDateTime resolveNextRunAt(Boolean scheduleEnabled, String cronExpression, LocalDateTime baseTime) {
        if (!Boolean.TRUE.equals(scheduleEnabled)) {
            return null;
        }
        return resolveNextRunAfter(cronExpression, baseTime);
    }

    /** 解析 cron 表达式，返回基准时间之后的下次触发时间。无效表达式返回 null。 */
    LocalDateTime resolveNextRunAfter(String cronExpression, LocalDateTime baseTime) {
        if (cronExpression == null || cronExpression.isBlank()) {
            return null;
        }
        try {
            LocalDateTime nextRunAt = CronExpression.parse(cronExpression).next(baseTime.withNano(0));
            return nextRunAt == null ? null : nextRunAt.withNano(0);
        } catch (IllegalArgumentException exception) {
            log.warn("Invalid cron expression for scheduler: {}", cronExpression);
            return null;
        }
    }
}
