package com.example.platform.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 链路追踪日志服务 —— 把每次 Agent 调用的完整执行链路写入 Redis 持久化。
 *
 * <p>存储结构：
 * <ul>
 *   <li>Redis List {@code agent:trace:{traceId}} —— 该 trace 的所有日志条目（时间升序），TTL 90 天</li>
 *   <li>Redis ZSet {@code agent:trace:index} —— 所有 traceId 按时间戳排序的索引，便于查询最近 N 条，上限 1 万条</li>
 * </ul>
 *
 * <p>核心 API：
 * <ul>
 *   <li>{@link #log} —— 追加一条日志（含 metadata，自动截断到 20 万字符）</li>
 *   <li>{@link #queryByTraceId} —— 查询某条 trace 的完整时间线</li>
 *   <li>{@link #queryRecentTraces} —— 查询最近的 trace 摘要列表</li>
 * </ul>
 *
 * <p>使用场景：前端「调度事件列表」、{@link TraceQueryTool}（让 LLM 自查链路）、后端排障。
 */
@Service
public class AgentTraceLogService {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceLogService.class);

    private static final Duration TTL_90_DAYS = Duration.ofDays(90);
    private static final String KEY_TRACE_PREFIX = "agent:trace:";
    private static final String KEY_TRACE_INDEX = "agent:trace:index";
    private static final String KEY_TRACE_OWNER = "agent:trace:owner";
    private static final int MAX_INDEX_SIZE = 10_000;
    private static final java.util.regex.Pattern URL_USERINFO =
            java.util.regex.Pattern.compile("(?i)(https?://)[^/@\\s]+@");
    private static final java.util.regex.Pattern COMMON_SECRET =
            java.util.regex.Pattern.compile("(?i)(api[_-]?key|token|secret|password)\\s*[=:]\\s*[^\\s,;]+");
    private static final java.util.regex.Pattern BEARER_TOKEN =
            java.util.regex.Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._-]{12,}");
    /** 所有 metadata 字符串值统一截断上限（不再区分 Preview/Full，全部保留原文）。 */
    private static final int MAX_METADATA_VALUE_LENGTH = 200_000;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public AgentTraceLogService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void log(String traceId, String level, String stage, String message, Map<String, Object> metadata) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }

        try {
            TraceLogEntry entry = new TraceLogEntry(
                    UUID.randomUUID().toString(),
                    traceId,
                    Instant.now(),
                    level,
                    stage,
                    redact(message),
                    truncateMetadata(metadata != null ? metadata : Map.of())
            );

            String key = KEY_TRACE_PREFIX + traceId;
            String json = objectMapper.writeValueAsString(entry);

            redis.opsForList().rightPush(key, json);
            redis.expire(key, TTL_90_DAYS);

            redis.opsForZSet().add(KEY_TRACE_INDEX, traceId, System.currentTimeMillis());
            redis.expire(KEY_TRACE_INDEX, TTL_90_DAYS);

            Long indexSize = redis.opsForZSet().size(KEY_TRACE_INDEX);
            if (indexSize != null && indexSize > MAX_INDEX_SIZE) {
                redis.opsForZSet().removeRange(KEY_TRACE_INDEX, 0, indexSize - MAX_INDEX_SIZE - 1);
            }

        } catch (JsonProcessingException e) {
            AgentTraceLogService.log.error("Failed to serialize trace log entry: traceId={}", traceId, e);
        } catch (Exception e) {
            AgentTraceLogService.log.warn("Failed to write trace log to Redis: traceId={}", traceId, e);
        }
    }

    /**
     * 记录 trace 归属。列表/详情查询用它做用户隔离；没有归属的旧 trace 不返回。
     */
    public void bindOwner(String traceId, Long userId, Long spaceId) {
        if (traceId == null || traceId.isBlank() || userId == null) {
            return;
        }
        try {
            String value = userId + ":" + (spaceId == null ? 0L : spaceId);
            redis.opsForHash().put(KEY_TRACE_OWNER, traceId, value);
            redis.expire(KEY_TRACE_OWNER, TTL_90_DAYS);
        } catch (Exception exception) {
            log.warn("Failed to bind trace owner: traceId={}, userId={}", traceId, userId, exception);
        }
    }

    private boolean ownedBy(String traceId, Long userId) {
        if (traceId == null || userId == null) {
            return false;
        }
        try {
            Object value = redis.opsForHash().get(KEY_TRACE_OWNER, traceId);
            if (value == null) {
                return false;
            }
            String ownerUserId = String.valueOf(value).split(":", 2)[0];
            return ownerUserId.equals(String.valueOf(userId));
        } catch (Exception exception) {
            log.warn("Failed to read trace owner: traceId={}", traceId, exception);
            return false;
        }
    }

    private Map<String, Object> truncateMetadata(Map<String, Object> metadata) {
        Map<String, Object> truncated = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String str) {
                truncated.put(key, truncate(redact(str), MAX_METADATA_VALUE_LENGTH));
            } else if (value instanceof List<?> list) {
                if (list.size() > 200) {
                    truncated.put(key, list.subList(0, 200) + "...(total:" + list.size() + ")");
                } else {
                    truncated.put(key, value);
                }
            } else {
                truncated.put(key, value);
            }
        }
        return truncated;
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...[truncated]";
    }

    /**
     * trace 会被页面和 LLM 读取，写入前对常见凭据做最小化脱敏。
     * 这不是一个完整的 DLP，只是防止 Git URL / Authorization 头把 token 原样留在 Redis。
     */
    private String redact(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String redacted = URL_USERINFO.matcher(text).replaceAll("$1***@");
        redacted = COMMON_SECRET.matcher(redacted).replaceAll("$1=***");
        return BEARER_TOKEN.matcher(redacted).replaceAll("$1***");
    }

    public void log(String traceId, String level, String stage, String message) {
        log(traceId, level, stage, message, Map.of());
    }

    public void logInfo(String traceId, String stage, String message) {
        log(traceId, "INFO", stage, message, null);
    }

    public void logError(String traceId, String stage, String message) {
        log(traceId, "ERROR", stage, message, null);
    }

    public void logWithMetadata(String traceId, String level, String stage, String message, String metaKey, Object metaValue) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(metaKey, metaValue);
        log(traceId, level, stage, message, metadata);
    }

    public List<TraceLogEntry> queryByTraceId(String traceId, Long userId) {
        if (traceId == null || traceId.isBlank() || !ownedBy(traceId, userId)) {
            return List.of();
        }

        try {
            String key = KEY_TRACE_PREFIX + traceId;
            List<String> entries = redis.opsForList().range(key, 0, -1);
            if (entries == null || entries.isEmpty()) {
                return List.of();
            }

            return entries.stream()
                    .map(json -> {
                        try {
                            return objectMapper.readValue(json, TraceLogEntry.class);
                        } catch (JsonProcessingException e) {
                            AgentTraceLogService.log.warn("Failed to deserialize trace log entry: traceId={}", traceId);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(TraceLogEntry::timestamp))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            AgentTraceLogService.log.warn("Failed to query trace logs: traceId={}", traceId, e);
            return List.of();
        }
    }

    public List<TraceSummary> queryRecentTraces(Long userId, int limit) {
        if (userId == null) {
            return List.of();
        }
        try {
            int count = Math.min(Math.max(limit, 1), 50);
            // 多取一些候选，因为别人的 trace 会被过滤掉。
            int scanSize = Math.min(500, Math.max(count * 5, count));
            Set<ZSetOperations.TypedTuple<String>> index = redis.opsForZSet()
                    .reverseRangeWithScores(KEY_TRACE_INDEX, 0, scanSize - 1);

            if (index == null || index.isEmpty()) {
                return List.of();
            }

            List<TraceSummary> summaries = new ArrayList<>();
            for (ZSetOperations.TypedTuple<String> entry : index) {
                if (entry.getValue() == null) continue;
                String traceId = entry.getValue();
                if (!ownedBy(traceId, userId)) {
                    continue;
                }
                String key = KEY_TRACE_PREFIX + traceId;
                Long size = redis.opsForList().size(key);
                Double score = entry.getScore();

                summaries.add(new TraceSummary(
                        traceId,
                        size != null ? size : 0,
                        score != null ? Instant.ofEpochMilli(score.longValue()) : Instant.now()
                ));
                if (summaries.size() >= count) {
                    break;
                }
            }

            return summaries;

        } catch (Exception e) {
            AgentTraceLogService.log.warn("Failed to query recent traces", e);
            return List.of();
        }
    }

    public long getTraceCount() {
        try {
            Long size = redis.opsForZSet().size(KEY_TRACE_INDEX);
            return size != null ? size : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean traceExists(String traceId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(KEY_TRACE_PREFIX + traceId));
        } catch (Exception e) {
            return false;
        }
    }

    public void deleteTrace(String traceId) {
        try {
            redis.delete(KEY_TRACE_PREFIX + traceId);
            redis.opsForZSet().remove(KEY_TRACE_INDEX, traceId);
            redis.opsForHash().delete(KEY_TRACE_OWNER, traceId);
        } catch (Exception e) {
            AgentTraceLogService.log.warn("Failed to delete trace: traceId={}", traceId, e);
        }
    }

    public record TraceLogEntry(
            String id,
            String traceId,
            Instant timestamp,
            String level,
            String stage,
            String message,
            Map<String, Object> metadata
    ) {}

    public record TraceSummary(
            String traceId,
            long entryCount,
            Instant lastUpdatedAt
    ) {}
}