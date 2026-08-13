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

@Service
public class AgentTraceLogService {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceLogService.class);

    private static final Duration TTL_90_DAYS = Duration.ofDays(90);
    private static final String KEY_TRACE_PREFIX = "agent:trace:";
    private static final String KEY_TRACE_INDEX = "agent:trace:index";
    private static final int MAX_INDEX_SIZE = 10_000;

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
                    message,
                    metadata != null ? metadata : Map.of()
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

    public List<TraceLogEntry> queryByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
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

    public List<TraceSummary> queryRecentTraces(int limit) {
        try {
            int count = Math.min(limit, 50);
            Set<ZSetOperations.TypedTuple<String>> index = redis.opsForZSet()
                    .reverseRangeWithScores(KEY_TRACE_INDEX, 0, count - 1);

            if (index == null || index.isEmpty()) {
                return List.of();
            }

            List<TraceSummary> summaries = new ArrayList<>();
            for (ZSetOperations.TypedTuple<String> entry : index) {
                if (entry.getValue() == null) continue;
                String traceId = entry.getValue();
                String key = KEY_TRACE_PREFIX + traceId;
                Long size = redis.opsForList().size(key);
                Double score = entry.getScore();

                summaries.add(new TraceSummary(
                        traceId,
                        size != null ? size : 0,
                        score != null ? Instant.ofEpochMilli(score.longValue()) : Instant.now()
                ));
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