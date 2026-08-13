package com.example.platform.ai.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
            estimatedTokens = 0;
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
                this.estimatedTokens + estimateTokens(message.content())
        );
    }

    public ChatSession withMessages(List<ChatMessage> newMessages) {
        return new ChatSession(
                this.sessionId,
                new ArrayList<>(newMessages),
                this.systemPrompt,
                this.createdAt,
                Instant.now(),
                estimateTotalTokens(newMessages)
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

    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    private static int estimateTotalTokens(List<ChatMessage> msgs) {
        int total = 0;
        for (ChatMessage msg : msgs) {
            total += estimateTokens(msg.content());
        }
        return total;
    }
}