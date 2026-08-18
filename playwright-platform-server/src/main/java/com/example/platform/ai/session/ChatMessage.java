package com.example.platform.ai.session;

import java.time.Instant;

/**
 * 会话消息 record —— 对应一次对话消息（用户/助手/工具）。
 *
 * @param role      角色：user / assistant / tool
 * @param content   消息文本内容
 * @param timestamp 时间戳，空则取当前时间
 * @param toolName  仅 role=tool 时有值，标识是哪个工具返回的
 *
 * <p>工厂方法 {@link #user} / {@link #assistant} / {@link #tool} 简化构造。
 */
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