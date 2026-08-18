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

/**
 * 会话管理器 —— 基于 Caffeine 缓存维护会话与终止状态。
 *
 * <p>两个缓存：
 * <ul>
 *   <li>{@code sessionCache} —— 会话本体，30 分钟过期，上限 1 万条</li>
 *   <li>{@code terminatedSessions} —— 被主动终止的会话标记，30 分钟过期，用于让 AgentService
 *       在流式输出循环中检查是否应停止</li>
 * </ul>
 *
 * <p>核心 API：
 * <ul>
 *   <li>{@link #getOrCreateSession} —— 获取或创建会话</li>
 *   <li>{@link #appendMessage} / {@link #appendMessages} —— 追加消息</li>
 *   <li>{@link #markTerminated} / {@link #isTerminated} —— 会话终止标记与检查</li>
 *   <li>{@link #clearSession} —— 清空会话</li>
 * </ul>
 */
@Component
public class ChatSessionManager {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionManager.class);

    private final Cache<String, ChatSession> sessionCache;
    private final Cache<String, Boolean> terminatedSessions;

    public ChatSessionManager() {
        this.sessionCache = Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .removalListener((key, value, cause) -> {
                    log.debug("Session removed: sessionId={}, cause={}", key, cause);
                })
                .build();
        this.terminatedSessions = Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(10_000)
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

    public void markTerminated(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            terminatedSessions.put(sessionId, Boolean.TRUE);
            log.info("Session marked as terminated: sessionId={}", sessionId);
        }
    }

    public boolean isTerminated(String sessionId) {
        return sessionId != null && Boolean.TRUE.equals(terminatedSessions.getIfPresent(sessionId));
    }

    public void clearTerminated(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            terminatedSessions.invalidate(sessionId);
        }
    }
}