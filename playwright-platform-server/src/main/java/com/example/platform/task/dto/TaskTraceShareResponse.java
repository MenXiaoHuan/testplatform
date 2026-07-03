package com.example.platform.task.dto;

import java.time.Instant;

public record TaskTraceShareResponse(
        String shareUrl,
        Instant expiresAt) {
}
