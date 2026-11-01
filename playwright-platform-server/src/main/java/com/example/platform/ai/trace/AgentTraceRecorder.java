package com.example.platform.ai.trace;

import com.example.platform.ai.AgentTraceContext;
import com.example.platform.evaluation.langfuse.LangfuseClient;
import com.example.platform.evaluation.langfuse.ObservationWriteRequest;
import com.example.platform.evaluation.langfuse.TraceWriteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内置 Agent 链路埋点器 —— 把对话阶段日志转换成 Langfuse Trace / Observation。
 *
 * <p>写入全部异步、单线程有序执行：主链路（对话）不受 Langfuse 延迟影响；
 * 队列有界（{@value #MAX_PENDING}），满时静默丢弃；Langfuse 故障由 {@link LangfuseClient} 吞异常，
 * 因此 trace 可能静默丢失（产品已确认的可接受行为）。
 *
 * <p>脱敏与截断策略：URL userinfo、api-key/token/secret/password、Bearer Token 脱敏；
 * 字符串统一截断到 20 万字符。
 */
@Service
public class AgentTraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceRecorder.class);

    private static final String SOURCE_TAG = "source=builtin_agent";
    private static final String TARGET_TAG = "target_id=builtin_agent";
    private static final int MAX_PENDING = 2000;
    private static final int MAX_METADATA_VALUE_LENGTH = 200_000;

    private static final java.util.regex.Pattern URL_USERINFO =
            java.util.regex.Pattern.compile("(?i)(https?://)[^/@\\s]+@");
    private static final java.util.regex.Pattern COMMON_SECRET =
            java.util.regex.Pattern.compile("(?i)(api[_-]?key|token|secret|password)\\s*[=:]\\s*[^\\s,;]+");
    private static final java.util.regex.Pattern BEARER_TOKEN =
            java.util.regex.Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._-]{12,}");

    private final LangfuseClient langfuse;
    private final ExecutorService executor;
    private final AtomicInteger pending = new AtomicInteger();

    public AgentTraceRecorder(LangfuseClient langfuse) {
        this.langfuse = langfuse;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "agent-trace-recorder");
            thread.setDaemon(true);
            return thread;
        });
    }

    // ===== Trace 生命周期 =====

    /** 对话开始：写 trace 头部（同名 trace 在 Langfuse 侧是 upsert）。 */
    public void startTrace(String traceId, String sessionId, Long userId, Long spaceId, String userMessage) {
        if (blank(traceId)) return;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "builtin_agent");
        if (sessionId != null) metadata.put("sessionId", sessionId);
        if (userId != null) metadata.put("userId", userId.toString());
        if (spaceId != null) metadata.put("spaceId", spaceId);

        TraceWriteRequest request = TraceWriteRequest.builder()
                .traceId(traceId)
                .name("agent.chat")
                .sessionId(sessionId)
                .userId(userId != null ? userId.toString() : null)
                .input(truncate(redact(userMessage)))
                .tags(List.of(SOURCE_TAG, TARGET_TAG))
                .metadata(metadata)
                .timestamp(Instant.now())
                .build();
        submit(() -> langfuse.writeTrace(request));
    }

    /** 对话结束：用同一 traceId upsert 输出与成功/失败标记。 */
    public void finishTrace(String traceId, String output, boolean success, String errorMessage) {
        if (blank(traceId)) return;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "builtin_agent");
        if (!success && errorMessage != null) metadata.put("errorMessage", truncate(redact(errorMessage)));
        List<String> tags = success
                ? List.of(SOURCE_TAG, TARGET_TAG)
                : List.of(SOURCE_TAG, TARGET_TAG, "error=true");

        TraceWriteRequest request = TraceWriteRequest.builder()
                .traceId(traceId)
                .name("agent.chat")
                .output(output != null ? truncate(redact(output)) : null)
                .tags(tags)
                .metadata(metadata)
                .timestamp(Instant.now())
                .build();
        submit(() -> langfuse.writeTrace(request));
    }

    // ===== Observation =====

    /** 记录一个阶段事件（原 Redis 日志条目），父节点取当前线程的观测栈顶。 */
    public void recordStage(String traceId, String level, String stage, String message, Map<String, Object> metadata) {
        if (blank(traceId)) return;
        String parentId = AgentTraceContext.getParentObservationId();
        String obsId = newObservationId();
        Instant now = Instant.now();

        String normalizedLevel = normalizeLevel(level);
        ObservationWriteRequest request = ObservationWriteRequest.builder()
                .traceId(traceId)
                .observationId(obsId)
                .parentObservationId(parentId)
                .name(stage != null ? stage.toLowerCase(Locale.ROOT) : "stage")
                .input(truncate(redact(message)))
                .startTime(now)
                .endTime(now)
                .success(!"ERROR".equals(normalizedLevel))
                .level(normalizedLevel)
                .metadata(truncateMetadata(metadata))
                .build();
        submit(() -> langfuse.writeObservation(request));
    }

    public void recordStage(String traceId, String level, String stage, String message) {
        recordStage(traceId, level, stage, message, Map.of());
    }

    /** 记录一个带耗时的观测（模型调用 / 工具调用）。 */
    public void recordSpan(String traceId, String name, String input, String output,
                           Instant startTime, Instant endTime,
                           boolean success, String level,
                           Long inputTokens, Long outputTokens, String model,
                           Map<String, Object> metadata) {
        if (blank(traceId)) return;
        String parentId = AgentTraceContext.getParentObservationId();
        String obsId = newObservationId();
        String normalizedLevel = normalizeLevel(level);

        ObservationWriteRequest request = ObservationWriteRequest.builder()
                .traceId(traceId)
                .observationId(obsId)
                .parentObservationId(parentId)
                .name(name)
                .input(truncate(redact(input)))
                .output(truncate(redact(output)))
                .startTime(startTime)
                .endTime(endTime)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(inputTokens != null || outputTokens != null
                        ? (inputTokens != null ? inputTokens : 0L) + (outputTokens != null ? outputTokens : 0L)
                        : null)
                .model(model)
                .success(success)
                .level(success ? normalizedLevel : "ERROR")
                .metadata(truncateMetadata(metadata))
                .build();
        submit(() -> langfuse.writeObservation(request));
    }

    public String newObservationId() {
        return UUID.randomUUID().toString();
    }

    // ===== 内部 =====

    private void submit(Runnable task) {
        if (pending.get() >= MAX_PENDING) {
            log.debug("Agent trace queue full, dropping event");
            return;
        }
        pending.incrementAndGet();
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    log.debug("Agent trace write failed silently: {}", e.getMessage());
                } finally {
                    pending.decrementAndGet();
                }
            });
        } catch (Exception e) {
            pending.decrementAndGet();
            log.debug("Agent trace enqueue failed silently: {}", e.getMessage());
        }
    }

    private static String normalizeLevel(String level) {
        if (level == null || level.isBlank()) return "DEFAULT";
        return switch (level.toUpperCase(Locale.ROOT)) {
            case "ERROR" -> "ERROR";
            case "WARN", "WARNING" -> "WARNING";
            case "DEBUG" -> "DEBUG";
            default -> "DEFAULT";
        };
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Map<String, Object> truncateMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return Map.of();
        Map<String, Object> truncated = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String str) {
                truncated.put(entry.getKey(), truncate(redact(str)));
            } else if (value instanceof List<?> list) {
                truncated.put(entry.getKey(), list.size() > 200
                        ? list.subList(0, 200) + "...(total:" + list.size() + ")"
                        : value);
            } else {
                truncated.put(entry.getKey(), value);
            }
        }
        return truncated;
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_METADATA_VALUE_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_METADATA_VALUE_LENGTH) + "...[truncated]";
    }

    private static String redact(String text) {
        if (text == null || text.isBlank()) return text;
        String redacted = URL_USERINFO.matcher(text).replaceAll("$1***@");
        redacted = COMMON_SECRET.matcher(redacted).replaceAll("$1=***");
        return BEARER_TOKEN.matcher(redacted).replaceAll("$1***");
    }
}
