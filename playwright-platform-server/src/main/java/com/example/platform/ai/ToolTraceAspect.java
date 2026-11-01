package com.example.platform.ai;

import com.example.platform.ai.trace.AgentTraceRecorder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具调用链路切面 —— 拦截所有标注 {@code @Tool} 的方法，写入 Langfuse Observation。
 *
 * <p>记录内容：工具名、入参完整文本、返回结果完整文本、调用耗时、异常类型与消息。
 * 入参和返回结果不在此处截断，由 {@link AgentTraceRecorder} 统一脱敏并截断。
 */
@Aspect
@Component
public class ToolTraceAspect {

    private final AgentTraceRecorder traceRecorder;

    public ToolTraceAspect(AgentTraceRecorder traceRecorder) {
        this.traceRecorder = traceRecorder;
    }

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object traceToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String traceId = AgentTraceContext.getTraceId();
        if (traceId == null || traceId.isBlank()) {
            return joinPoint.proceed();
        }

        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String toolName = method.getName();
        Object[] args = joinPoint.getArgs();
        String argsText = argsToString(args);

        Map<String, Object> callMeta = new LinkedHashMap<>();
        callMeta.put("toolName", toolName);
        callMeta.put("argsCount", args.length);
        callMeta.put("targetClass", joinPoint.getTarget().getClass().getSimpleName());

        Instant start = Instant.now();
        AgentTraceContext.pushObservation(traceRecorder.newObservationId());
        try {
            Object result = joinPoint.proceed();
            long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();

            Map<String, Object> resultMeta = new LinkedHashMap<>();
            resultMeta.put("toolName", toolName);
            resultMeta.put("durationMs", durationMs);
            resultMeta.put("resultType", result != null ? result.getClass().getSimpleName() : "null");
            resultMeta.put("resultLength", result instanceof String s ? s.length() : 0);

            AgentTraceContext.popObservation();
            traceRecorder.recordSpan(traceId, "tool." + toolName, argsText,
                    resultToString(result), start, Instant.now(), true, null,
                    null, null, null, resultMeta);

            return result;
        } catch (Throwable t) {
            Map<String, Object> errorMeta = new LinkedHashMap<>();
            errorMeta.put("toolName", toolName);
            errorMeta.put("errorType", t.getClass().getSimpleName());
            errorMeta.put("argsCount", args.length);

            AgentTraceContext.popObservation();
            traceRecorder.recordSpan(traceId, "tool." + toolName, argsText,
                    t.getMessage(), start, Instant.now(), false, "ERROR",
                    null, null, null, errorMeta);

            throw t;
        }
    }

    /** 工具调用入参完整文本（不截断，由 recorder 统一截断到上限）。 */
    private String argsToString(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            Object arg = args[i];
            if (arg == null) {
                sb.append("null");
            } else if (arg instanceof String s) {
                sb.append("\"").append(s).append("\"");
            } else {
                sb.append(arg.toString());
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /** 工具返回结果完整文本（不截断，由 recorder 统一截断到上限）。 */
    private String resultToString(Object result) {
        if (result == null) {
            return "null";
        }
        if (result instanceof String s) {
            return s;
        }
        return result.toString();
    }
}
