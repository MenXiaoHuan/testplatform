package com.example.platform.ai;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Component
public class ToolTraceAspect {

    private static final Logger log = LoggerFactory.getLogger(ToolTraceAspect.class);

    private final AgentTraceLogService traceLogService;

    public ToolTraceAspect(AgentTraceLogService traceLogService) {
        this.traceLogService = traceLogService;
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

        Map<String, Object> callMeta = new LinkedHashMap<>();
        callMeta.put("toolName", toolName);
        callMeta.put("args", summarizeArgs(args));
        callMeta.put("argsCount", args.length);
        callMeta.put("targetClass", joinPoint.getTarget().getClass().getSimpleName());

        traceLogService.log(traceId, "INFO", "TOOL_CALL_STARTING",
                "Tool call starting: " + toolName, callMeta);

        Instant start = Instant.now();
        try {
            Object result = joinPoint.proceed();
            long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();

            Map<String, Object> resultMeta = new LinkedHashMap<>();
            resultMeta.put("toolName", toolName);
            resultMeta.put("durationMs", durationMs);
            resultMeta.put("resultPreview", summarizeResult(result));
            resultMeta.put("resultType", result != null ? result.getClass().getSimpleName() : "null");
            resultMeta.put("resultLength", result instanceof String s ? s.length() : 0);

            traceLogService.log(traceId, "INFO", "TOOL_CALL_COMPLETED",
                    "Tool call completed: " + toolName + " in " + durationMs + "ms", resultMeta);

            return result;
        } catch (Throwable t) {
            long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();

            Map<String, Object> errorMeta = new LinkedHashMap<>();
            errorMeta.put("toolName", toolName);
            errorMeta.put("durationMs", durationMs);
            errorMeta.put("errorType", t.getClass().getSimpleName());
            errorMeta.put("errorMessage", t.getMessage());
            errorMeta.put("args", summarizeArgs(args));

            traceLogService.log(traceId, "ERROR", "TOOL_CALL_FAILED",
                    "Tool call failed: " + toolName + " - " + t.getMessage(), errorMeta);

            throw t;
        }
    }

    private String summarizeArgs(Object[] args) {
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
                sb.append("\"").append(truncate(s, 100)).append("\"");
            } else {
                sb.append(truncate(arg.toString(), 80));
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private String summarizeResult(Object result) {
        if (result == null) {
            return "null";
        }
        if (result instanceof String s) {
            return truncate(s, 300);
        }
        return truncate(result.toString(), 200);
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...[truncated]";
    }
}