package com.example.platform.scene.dto;

public record ScheduleEventRetryRequest(
        String operatorName,
        String operatorId,
        String comment) {
}
