package com.example.platform.ai.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话会话 record —— 不可变，所有变更通过 {@code with*} 方法返回新实例。
 *
 * @param sessionId         会话 ID
 * @param messages          消息列表
 * @param systemPrompt      系统提示词
 * @param createdAt         创建时间
 * @param lastAccessedAt    最近访问时间
 * @param estimatedTokens   预估 token 数（中文 1.5x、英文 0.25x 估算）
 *
 * <p>token 估算见 {@link #estimateTextTokens}，用于触发 {@link ContextCompressionService} 压缩。
 */
public record ChatSession(
        String sessionId,
        List<ChatMessage> messages,
        String systemPrompt,
        Instant createdAt,
        Instant lastAccessedAt,
        int estimatedTokens
) {
    public ChatSession {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (lastAccessedAt == null) {
            lastAccessedAt = Instant.now();
        }
        if (estimatedTokens <= 0) {
            estimatedTokens = estimateTotalTokens(messages, systemPrompt);
        }
    }

    public ChatSession withMessage(ChatMessage message) {
        List<ChatMessage> newMessages = new ArrayList<>(this.messages);
        newMessages.add(message);
        return new ChatSession(
                this.sessionId,
                newMessages,
                this.systemPrompt,
                this.createdAt,
                Instant.now(),
                estimateTotalTokens(newMessages, this.systemPrompt)
        );
    }

    public ChatSession withMessages(List<ChatMessage> newMessages) {
        return new ChatSession(
                this.sessionId,
                new ArrayList<>(newMessages),
                this.systemPrompt,
                this.createdAt,
                Instant.now(),
                estimateTotalTokens(newMessages, this.systemPrompt)
        );
    }

    public ChatSession withSystemPrompt(String systemPrompt) {
        return new ChatSession(
                this.sessionId,
                this.messages,
                systemPrompt,
                this.createdAt,
                Instant.now(),
                estimateTotalTokens(this.messages, systemPrompt)
        );
    }

    public ChatSession touch() {
        return new ChatSession(
                this.sessionId,
                this.messages,
                this.systemPrompt,
                this.createdAt,
                Instant.now(),
                this.estimatedTokens
        );
    }

    public int messageCount() {
        return messages.size();
    }

    public int systemPromptTokens() {
        return estimateTextTokens(systemPrompt);
    }

    public int messageTokens() {
        int total = 0;
        for (ChatMessage msg : messages) {
            total += estimateTextTokens(msg.content());
        }
        return total;
    }

    public static int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chineseChars = 0;
        int otherChars = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4e00 && c <= 0x9fff) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return Math.max(1, (int) (chineseChars * 1.5 + otherChars * 0.25));
    }

    public static int estimateTotalTokens(List<ChatMessage> messages, String systemPrompt) {
        int total = estimateTextTokens(systemPrompt);
        if (messages != null) {
            for (ChatMessage msg : messages) {
                total += estimateTextTokens(msg.content());
            }
        }
        return total;
    }
}
