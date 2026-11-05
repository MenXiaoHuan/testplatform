package com.example.platform.patrol.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 巡页运行预算：总时长、Token 消耗统计、降级/停止原因与可执行建议（不设任务级 Token 上限）。 */
public class PatrolBudget {

    private final int maxDurationMinutes;
    private final long startedAtMillis;

    private int tokensUsed;
    private final Set<String> stopReasons = new LinkedHashSet<>();
    private final Set<String> advice = new LinkedHashSet<>();
    private volatile boolean degraded;

    public PatrolBudget(int maxDurationMinutes, long startedAtMillis) {
        this.maxDurationMinutes = maxDurationMinutes <= 0 ? 20 : maxDurationMinutes;
        this.startedAtMillis = startedAtMillis <= 0 ? System.currentTimeMillis() : startedAtMillis;
    }

    public int getMaxDurationMinutes() { return maxDurationMinutes; }
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

    /** 记录可执行的解决建议（去重保序），供任务概览展示。 */
    public void addAdvice(String suggestion) {
        if (suggestion == null || suggestion.isBlank()) {
            return;
        }
        synchronized (advice) {
            advice.add(suggestion.trim());
        }
    }

    public void addAdvice(List<String> suggestions) {
        if (suggestions == null) {
            return;
        }
        for (String suggestion : suggestions) {
            addAdvice(suggestion);
        }
    }

    public List<String> getAdvice() {
        synchronized (advice) {
            return new ArrayList<>(advice);
        }
    }
}
