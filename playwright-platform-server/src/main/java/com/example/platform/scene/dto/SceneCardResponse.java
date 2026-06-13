package com.example.platform.scene.dto;

import java.time.LocalDateTime;

public record SceneCardResponse(
        Long id,
        Long repoId,
        String name,
        String description,
        String branch,
        boolean scheduleEnabled,
        String cronExpression,
        String lastTaskStatus,
        LocalDateTime lastRunAt,
        int environmentVariableCount) {
}
