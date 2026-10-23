package com.example.platform.ai;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Agent 请求上下文：把“当前用户 + 当前空间”绑定到 traceId 上。
 *
 * <p>为什么不用 ThreadLocal：ReAct 的模型调用会被 {@code AgentCallManager} 切换到
 * 自己的线程池执行，普通 ThreadLocal 不会自动传播。traceId 已经由
 * {@link AgentTraceContext} 在跨线程时显式透传，所以这里用 traceId 作为 key
 * 维护请求上下文，工具线程可以安全地取到服务端校验过的 spaceId。
 *
 * <p>安全边界：工具参数里的 spaceId 来自 LLM，是不可信输入；工具必须只使用这里
 * 注册的值，不能相信模型自己传的 spaceId。
 */
public final class AgentRequestContext {
    public record Context(Long userId, Long spaceId) {
    }

    private static final ConcurrentMap<String, Context> CONTEXTS = new ConcurrentHashMap<>();

    private AgentRequestContext() {
    }

    public static void register(String traceId, Long userId, Long spaceId) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        CONTEXTS.put(traceId, new Context(userId, spaceId));
    }

    public static Context require() {
        String traceId = AgentTraceContext.getTraceId();
        Context context = traceId == null ? null : CONTEXTS.get(traceId);
        if (context == null) {
            throw new IllegalStateException("AI request context is missing");
        }
        return context;
    }

    public static Long requireSpaceId() {
        Long spaceId = require().spaceId();
        if (spaceId == null) {
            throw new IllegalStateException("当前对话没有绑定空间，无法查询平台数据");
        }
        return spaceId;
    }

    public static Long userId() {
        return require().userId();
    }

    public static void clear(String traceId) {
        if (traceId != null) {
            CONTEXTS.remove(traceId);
        }
    }
}
