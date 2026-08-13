package com.example.platform.ai.session;

import java.time.Instant;

public record ChatMessage(
        String role,
        String content,
        Instant timestamp,
        String toolName
) {
    public ChatMessage {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, Instant.now(), null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, Instant.now(), null);
    }

    public static ChatMessage tool(String toolName, String content) {
        return new ChatMessage("tool", content, Instant.now(), toolName);
    }

    public boolean isUser() {
        return "user".equals(role);
    }

    public boolean isAssistant() {
        return "assistant".equals(role);
    }

    public boolean isTool() {
        return "tool".equals(role);
    }
}