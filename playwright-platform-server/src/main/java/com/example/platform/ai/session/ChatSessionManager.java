package com.example.platform.ai.session;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class ChatSessionManager {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionManager.class);

    private final Cache<String, ChatSession> sessionCache;

    public ChatSessionManager() {
        this.sessionCache = Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .removalListener((key, value, cause) -> {
                    log.debug("Session removed: sessionId={}, cause={}", key, cause);
                })
                .build();
    }

    public ChatSession createSession() {
        String sessionId = UUID.randomUUID().toString();
        return createSession(sessionId, null);
    }

    public ChatSession createSession(String sessionId, String systemPrompt) {
        ChatSession session = new ChatSession(
                sessionId,
                new java.util.ArrayList<>(),
                systemPrompt,
                Instant.now(),
                Instant.now(),
                0
        );
        sessionCache.put(sessionId, session);
        log.info("Session created: sessionId={}", sessionId);
        return session;
    }

    public Optional<ChatSession> getSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        ChatSession session = sessionCache.getIfPresent(sessionId);
        if (session != null) {
            session = session.touch();
            sessionCache.put(sessionId, session);
        }
        return Optional.ofNullable(session);
    }

    public ChatSession getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return createSession();
        }
        return getSession(sessionId)
                .orElseGet(() -> createSession(sessionId, null));
    }

    public ChatSession appendMessage(String sessionId, ChatMessage message) {
        ChatSession session = getOrCreateSession(sessionId);
        ChatSession updated = session.withMessage(message);
        sessionCache.put(sessionId, updated);
        log.debug("Message appended: sessionId={}, role={}, totalMessages={}",
                sessionId, message.role(), updated.messageCount());
        return updated;
    }

    public ChatSession appendMessages(String sessionId, List<ChatMessage> messages) {
        ChatSession session = getOrCreateSession(sessionId);
        ChatSession updated = session;
        for (ChatMessage msg : messages) {
            updated = updated.withMessage(msg);
        }
        sessionCache.put(sessionId, updated);
        return updated;
    }

    public void updateMessages(String sessionId, List<ChatMessage> messages) {
        ChatSession session = getOrCreateSession(sessionId);
        ChatSession updated = session.withMessages(messages);
        sessionCache.put(sessionId, updated);
    }

    public void updateSystemPrompt(String sessionId, String systemPrompt) {
        ChatSession session = getOrCreateSession(sessionId);
        ChatSession updated = session.withSystemPrompt(systemPrompt);
        sessionCache.put(sessionId, updated);
    }

    public void clearSession(String sessionId) {
        sessionCache.invalidate(sessionId);
        log.info("Session cleared: sessionId={}", sessionId);
    }

    public void clearAll() {
        sessionCache.invalidateAll();
        log.info("All sessions cleared");
    }

    public long getActiveSessionCount() {
        return sessionCache.estimatedSize();
    }

    public boolean sessionExists(String sessionId) {
        return sessionCache.getIfPresent(sessionId) != null;
    }
}