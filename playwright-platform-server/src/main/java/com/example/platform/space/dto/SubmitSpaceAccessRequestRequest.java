package com.example.platform.space.dto;

public record SubmitSpaceAccessRequestRequest(
        String requestedRole,
        String reason) {
}
