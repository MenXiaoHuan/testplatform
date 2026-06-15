package com.example.platform.scene.service;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;

final class SceneScheduleTimeResolver {
    private static final Logger log = LoggerFactory.getLogger(SceneScheduleTimeResolver.class);

    LocalDateTime resolveNextRunAt(Boolean scheduleEnabled, String cronExpression, LocalDateTime baseTime) {
        if (!Boolean.TRUE.equals(scheduleEnabled)) {
            return null;
        }
        return resolveNextRunAfter(cronExpression, baseTime);
    }

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
