package com.example.platform.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Centralizes Redis-backed detail caching for read-heavy detail endpoints.
 *
 * <p>The cache stores normal hits and short-lived null values, adds TTL jitter,
 * and uses a Redis mutex plus an in-process lock to reduce cache penetration,
 * breakdown, and avalanche risk.
 */
@Service
@EnableConfigurationProperties(CacheProperties.class)
public class DetailCacheService {
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties properties;
    private final ConcurrentMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    public DetailCacheService(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            CacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Returns a cached detail value or loads it once from the database when the key is missing.
     */
    public <T> Optional<T> getOrLoad(String detailType, Long id, Class<T> valueType, Supplier<Optional<T>> loader) {
        String key = detailKey(detailType, id);
        Optional<T> cached = readCached(key, valueType);
        if (cached != null) {
            return cached;
        }

        Object lock = keyLocks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            try {
                cached = readCached(key, valueType);
                if (cached != null) {
                    return cached;
                }
                return loadAndCache(key, valueType, loader);
            } finally {
                keyLocks.remove(key, lock);
            }
        }
    }

    /**
     * Removes one detail cache entry after a write transaction changes its source row.
     */
    public void invalidate(String detailType, Long id) {
        redisTemplate.delete(detailKey(detailType, id));
    }

    private <T> Optional<T> readCached(String key, Class<T> valueType) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.path("empty").asBoolean(false)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.treeToValue(root.get("value"), valueType));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read detail cache: " + key, ex);
        }
    }

    private <T> Optional<T> loadAndCache(String key, Class<T> valueType, Supplier<Optional<T>> loader) {
        String lockKey = key + ":lock";
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", properties.getMutexTtl());
        if (!Boolean.TRUE.equals(locked)) {
            return waitForCachedValue(key, valueType);
        }
        try {
            Optional<T> loaded = loader.get();
            writeCached(key, loaded);
            return loaded;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private <T> Optional<T> waitForCachedValue(String key, Class<T> valueType) {
        int retryTimes = Math.max(0, properties.getLockRetryTimes());
        long waitMillis = Math.max(0, properties.getLockWaitMillis());
        for (int attempt = 0; attempt < retryTimes; attempt++) {
            if (waitMillis > 0) {
                sleep(waitMillis);
            }
            Optional<T> cached = readCached(key, valueType);
            if (cached != null) {
                return cached;
            }
        }
        return Optional.empty();
    }

    private void sleep(long waitMillis) {
        try {
            Thread.sleep(waitMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for detail cache lock", ex);
        }
    }

    private <T> void writeCached(String key, Optional<T> value) {
        CachedValue cachedValue = value.<CachedValue>map(CachedValue::hit).orElseGet(CachedValue::nullValue);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(cachedValue), ttl(value.isEmpty()));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write detail cache: " + key, ex);
        }
    }

    private Duration ttl(boolean emptyValue) {
        Duration base = emptyValue ? properties.getNullTtl() : properties.getDetailTtl();
        int jitterSeconds = Math.max(0, properties.getJitterSeconds());
        if (jitterSeconds == 0) {
            return base;
        }
        return base.plusSeconds(ThreadLocalRandom.current().nextInt(jitterSeconds + 1));
    }

    private String detailKey(String detailType, Long id) {
        return "detail:" + detailType + ":" + id;
    }
}
