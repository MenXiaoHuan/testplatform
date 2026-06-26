package com.example.platform.task.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

final class TaskTimeMapper {
    private TaskTimeMapper() {
    }

    static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
