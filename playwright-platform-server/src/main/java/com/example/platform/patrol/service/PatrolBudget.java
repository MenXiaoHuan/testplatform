package com.example.platform.patrol.service;

import java.util.LinkedHashSet;
import java.util.Set;

/** 巡页运行预算：总时长、LLM Token 与降级/停止原因。 */
public class PatrolBudget {

    private final int maxDurationMinutes;
    private final int maxLlmTokens;
    private final long startedAtMillis;

    private int tokensUsed;
    private final Set<String> stopReasons = new LinkedHashSet<>();
    private volatile boolean degraded;

    public PatrolBudget(int maxDurationMinutes, int maxLlmTokens, long startedAtMillis) {
        this.maxDurationMinutes = maxDurationMinutes <= 0 ? 15 : maxDurationMinutes;
        this.maxLlmTokens = maxLlmTokens <= 0 ? 300000 : maxLlmTokens;
        this.startedAtMillis = startedAtMillis <= 0 ? System.currentTimeMillis() : startedAtMillis;
    }

    public int getMaxDurationMinutes() { return maxDurationMinutes; }
    public int getMaxLlmTokens() { return maxLlmTokens; }
    public int getTokensUsed() { return tokensUsed; }
    public boolean isDegraded() { return degraded; }

    public long getElapsedMs() {
        return Math.max(0L, System.currentTimeMillis() - startedAtMillis);
    }

    public long getRemainingDurationMs() {
        return Math.max(0L, (long) maxDurationMinutes * 60_000L - getElapsedMs());
    }

    public boolean isDurationExhausted() {
        return getRemainingDurationMs() <= 0L;
    }

    public int getRemainingTokens() {
        return Math.max(0, maxLlmTokens - tokensUsed);
    }

    public boolean canAfford(int estimatedTokens) {
        return getRemainingTokens() >= Math.max(0, estimatedTokens);
    }

    public void consumeTokens(int tokens) {
        if (tokens > 0) {
            tokensUsed += tokens;
        }
    }

    public void markDegraded() {
        degraded = true;
    }

    public void addStopReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return;
        }
        synchronized (stopReasons) {
            stopReasons.add(reason.trim());
        }
    }

    public String stopReasonText() {
        synchronized (stopReasons) {
            return stopReasons.isEmpty() ? "" : String.join("；", stopReasons);
        }
    }
}
