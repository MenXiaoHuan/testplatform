package com.example.platform.ai;

/**
 * Agent 链路追踪上下文 —— 基于 {@link ThreadLocal} 在当前线程透传 traceId。
 *
 * <p>用途：异步线程池切换、SSE 流式输出时，通过手动 set/clear 保留 traceId，
 * 使 {@link AgentTraceLogService} 能把日志归并到同一条 trace 链路下。
 *
 * <p>注意：使用完毕必须 {@link #clear()}，避免线程复用导致的 traceId 泄漏。
 */
public final class AgentTraceContext {

    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();

    private AgentTraceContext() {
    }

    public static void setTraceId(String traceId) {
        TRACE_ID_HOLDER.set(traceId);
    }

    public static String getTraceId() {
        return TRACE_ID_HOLDER.get();
    }

    public static void clear() {
        TRACE_ID_HOLDER.remove();
    }
}