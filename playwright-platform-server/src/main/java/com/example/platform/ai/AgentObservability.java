package com.example.platform.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent 可观测性组件 —— 在会话维度记录调用次数、错误次数、工具调用、Token 用量。
 *
 * <p>核心职责：
 * <ul>
 *   <li>记录每次 Agent 调用、错误、工具调用、Token 消耗</li>
 *   <li>提供全局统计 {@link #getGlobalStats()} 与会话级统计 {@link #getSessionStats(String)}</li>
 *   <li>会话结束时打印汇总日志并清理内存（{@link #recordConversationEnd}）</li>
 * </ul>
 *
 * <p>线程安全：所有计数器使用 {@link AtomicLong}，所有 Map 使用 {@link ConcurrentHashMap}。
 */
@Component
public class AgentObservability {

    private static final Logger log = LoggerFactory.getLogger(AgentObservability.class);

    private final Map<String, AtomicLong> sessionCallCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> sessionErrorCounts = new ConcurrentHashMap<>();
    private final Map<String, List<ToolCallRecord>> sessionToolCalls = new ConcurrentHashMap<>();
    private final Map<String, TokenUsage> sessionTokenUsage = new ConcurrentHashMap<>();

    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalInputTokens = new AtomicLong(0);
    private final AtomicLong totalOutputTokens = new AtomicLong(0);

    public void recordCall(String sessionId) {
        totalCalls.incrementAndGet();
        sessionCallCounts.computeIfAbsent(sessionId, k -> new AtomicLong(0))
                .incrementAndGet();
    }

    public void recordCall(String traceId, String sessionId) {
        recordCall(sessionId);
        log.info("[TRACE:{}] Agent call recorded: sessionId={}", traceId, sessionId);
    }

    public void recordError(String traceId, String errorType, String errorMessage) {
        totalErrors.incrementAndGet();
        log.error("[TRACE:{}] Agent error: type={}, message={}", traceId, errorType, errorMessage);
    }

    public void recordToolCall(String sessionId, String toolName, long durationMs, boolean success) {
        ToolCallRecord record = new ToolCallRecord(toolName, durationMs, success, Instant.now());
        sessionToolCalls.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(record);

        log.debug("Tool call: sessionId={}, tool={}, duration={}ms, success={}",
                sessionId, toolName, durationMs, success);
    }

    public void recordTokenUsage(String sessionId, long inputTokens, long outputTokens) {
        totalInputTokens.addAndGet(inputTokens);
        totalOutputTokens.addAndGet(outputTokens);

        sessionTokenUsage.computeIfAbsent(sessionId, k -> new TokenUsage(0, 0))
                .add(inputTokens, outputTokens);
    }

    public void recordConversationStart(String sessionId, String userMessage) {
        log.info("Conversation started: sessionId={}, messageLength={}",
                sessionId, userMessage != null ? userMessage.length() : 0);
    }

    public void recordConversationStart(String traceId, String sessionId, String userMessage) {
        recordConversationStart(sessionId, userMessage);
        log.info("[TRACE:{}] Conversation started: sessionId={}, messageLength={}",
                traceId, sessionId, userMessage != null ? userMessage.length() : 0);
    }

    public void recordConversationEnd(String sessionId, Duration totalTime,
                                       int messageCount, boolean hadErrors) {
        long calls = sessionCallCounts.getOrDefault(sessionId, new AtomicLong(0)).get();
        long errors = sessionErrorCounts.getOrDefault(sessionId, new AtomicLong(0)).get();
        TokenUsage tokens = sessionTokenUsage.getOrDefault(sessionId, new TokenUsage(0, 0));

        List<ToolCallRecord> toolCalls = sessionToolCalls.getOrDefault(sessionId, List.of());
        Map<String, Long> toolSummary = new HashMap<>();
        for (ToolCallRecord tc : toolCalls) {
            toolSummary.merge(tc.toolName(), 1L, Long::sum);
        }

        log.info("Conversation ended: sessionId={}, totalTime={}ms, messages={}, " +
                        "agentCalls={}, errors={}, inputTokens={}, outputTokens={}, tools={}",
                sessionId, totalTime.toMillis(), messageCount,
                calls, errors, tokens.inputTokens(), tokens.outputTokens(), toolSummary);

        cleanupSession(sessionId);
    }

    public void recordConversationEnd(String traceId, String sessionId, Duration totalTime,
                                       int messageCount, boolean hadErrors) {
        recordConversationEnd(sessionId, totalTime, messageCount, hadErrors);
        log.info("[TRACE:{}] Conversation ended: sessionId={}, totalTime={}ms, messages={}, hadErrors={}",
                traceId, sessionId, totalTime.toMillis(), messageCount, hadErrors);
    }

    public AgentStats getGlobalStats() {
        return new AgentStats(
                totalCalls.get(),
                totalErrors.get(),
                totalInputTokens.get(),
                totalOutputTokens.get(),
                sessionCallCounts.size()
        );
    }

    public SessionStats getSessionStats(String sessionId) {
        long calls = sessionCallCounts.getOrDefault(sessionId, new AtomicLong(0)).get();
        long errors = sessionErrorCounts.getOrDefault(sessionId, new AtomicLong(0)).get();
        TokenUsage tokens = sessionTokenUsage.getOrDefault(sessionId, new TokenUsage(0, 0));
        List<ToolCallRecord> toolCalls = sessionToolCalls.getOrDefault(sessionId, List.of());

        return new SessionStats(sessionId, calls, errors, tokens, toolCalls);
    }

    private void cleanupSession(String sessionId) {
        sessionCallCounts.remove(sessionId);
        sessionErrorCounts.remove(sessionId);
        sessionToolCalls.remove(sessionId);
        sessionTokenUsage.remove(sessionId);
    }

    public void reset() {
        sessionCallCounts.clear();
        sessionErrorCounts.clear();
        sessionToolCalls.clear();
        sessionTokenUsage.clear();
        totalCalls.set(0);
        totalErrors.set(0);
        totalInputTokens.set(0);
        totalOutputTokens.set(0);
        log.info("Agent observability stats reset");
    }

    public record ToolCallRecord(
            String toolName,
            long durationMs,
            boolean success,
            Instant timestamp
    ) {}

    public record TokenUsage(
            long inputTokens,
            long outputTokens
    ) {
        public TokenUsage add(long input, long output) {
            return new TokenUsage(this.inputTokens + input, this.outputTokens + output);
        }
    }

    public record AgentStats(
            long totalCalls,
            long totalErrors,
            long totalInputTokens,
            long totalOutputTokens,
            long activeSessions
    ) {}

    public record SessionStats(
            String sessionId,
            long agentCalls,
            long errors,
            TokenUsage tokenUsage,
            List<ToolCallRecord> toolCalls
    ) {}
}