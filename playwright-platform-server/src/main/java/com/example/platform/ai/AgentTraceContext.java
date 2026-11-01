package com.example.platform.ai;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Agent 链路追踪上下文 —— 基于 {@link ThreadLocal} 在当前线程透传 traceId 与观测栈。
 *
 * <p>用途：异步线程池切换、SSE 流式输出时，通过手动 set/clear 保留 traceId，
 * 使 {@link com.example.platform.ai.trace.AgentTraceRecorder} 能把日志归并到同一条 trace 链路下；
 * 观测栈用于给模型/工具调用挂父子关系（parentObservationId）。
 *
 * <p>注意：使用完毕必须 {@link #clear()}，避免线程复用导致的 traceId / 观测栈泄漏。
 */
public final class AgentTraceContext {

    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Deque<String>> OBSERVATION_STACK = new ThreadLocal<>();

    private AgentTraceContext() {
    }

    public static void setTraceId(String traceId) {
        TRACE_ID_HOLDER.set(traceId);
    }

    public static String getTraceId() {
        return TRACE_ID_HOLDER.get();
    }

    /** 进入一个子观测（模型调用 / 工具调用），后续事件以它为父节点。 */
    public static void pushObservation(String observationId) {
        if (observationId == null || observationId.isBlank()) {
            return;
        }
        Deque<String> stack = OBSERVATION_STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            OBSERVATION_STACK.set(stack);
        }
        stack.push(observationId);
    }

    /** 退出当前观测，返回其 id；栈为空时返回 null。 */
    public static String popObservation() {
        Deque<String> stack = OBSERVATION_STACK.get();
        return stack == null || stack.isEmpty() ? null : stack.pop();
    }

    /** 当前父观测 id；没有时返回 null。 */
    public static String getParentObservationId() {
        Deque<String> stack = OBSERVATION_STACK.get();
        return stack == null || stack.isEmpty() ? null : stack.peek();
    }

    public static void clear() {
        TRACE_ID_HOLDER.remove();
        OBSERVATION_STACK.remove();
    }
}
