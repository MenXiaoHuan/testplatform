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
 * 详情缓存服务 —— 基于 Redis 为读多写少的详情端点提供集中缓存。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #getOrLoad(String, Long, Class, Supplier)} —— 获取缓存或加载并缓存详情数据</li>
 *   <li>{@link #invalidate(String, Long)} —— 失效指定详情的缓存条目</li>
 * </ul>
 *
 * <p>缓存策略说明：
 * <ul>
 *   <li>存储正常命中值与短寿命空值占位，防止缓存穿透</li>
 *   <li>TTL 随机抖动，防止缓存雪崩</li>
 *   <li>Redis 分布式互斥锁 + 进程内对象锁双重保护，防止缓存击穿</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link org.springframework.data.redis.core.RedisTemplate} —— Redis 操作客户端</li>
 *   <li>{@link com.fasterxml.jackson.databind.ObjectMapper} —— JSON 序列化/反序列化</li>
 *   <li>{@link CacheProperties} —— 缓存配置参数</li>
 * </ul>
 */
@Service
@EnableConfigurationProperties(CacheProperties.class)
public class DetailCacheService {

    /** Redis 操作客户端 */
    private final RedisTemplate<String, String> redisTemplate;

    /** JSON 序列化/反序列化工具 */
    private final ObjectMapper objectMapper;

    /** 缓存配置参数 */
    private final CacheProperties properties;

    /** 进程内按 key 粒度的本地锁，减少同一 JVM 内的并发回源 */
    private final ConcurrentMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    /**
     * 构造详情缓存服务。
     *
     * @param redisTemplate  Redis 操作客户端
     * @param objectMapper   JSON 序列化工具
     * @param properties     缓存配置属性
     */
    public DetailCacheService(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            CacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 获取缓存中的详情值，若缓存未命中则通过加载器从数据源加载并写入缓存。
     *
     * <p>流程：先查 Redis → 若命中直接返回 → 未命中则获取本地锁 → 再次检查（避免并发重复加载）
     * → 若仍未命中则加载并写入缓存。
     *
     * @param detailType 详情类型标识（如 "scene"、"task"）
     * @param id         详情主键 ID
     * @param valueType  返回值的类型
     * @param loader     缓存未命中时的数据加载器
     * @param <T>        返回值泛型
     * @return 缓存或加载到的值，若数据不存在则返回 {@link Optional#empty()}
     */
    public <T> Optional<T> getOrLoad(String detailType, Long id, Class<T> valueType, Supplier<Optional<T>> loader) {
        String key = detailKey(detailType, id);
        // 第一次尝试从 Redis 读取缓存
        Optional<T> cached = readCached(key, valueType);
        if (cached != null) {
            return cached;
        }

        // 获取进程内 key 粒度的本地锁，确保同一 JVM 内只有一个线程去回源加载
        Object lock = keyLocks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            try {
                // 第二次检查：其他线程可能已在持锁期间完成了缓存写入
                cached = readCached(key, valueType);
                if (cached != null) {
                    return cached;
                }
                // 缓存确实未命中，执行加载并写入缓存
                return loadAndCache(key, valueType, loader);
            } finally {
                // 释放本地锁，移除 key 对应的锁对象
                keyLocks.remove(key, lock);
            }
        }
    }

    /**
     * 失效指定详情的缓存条目，应在写事务变更源数据后调用。
     *
     * @param detailType 详情类型标识
     * @param id         详情主键 ID
     */
    public void invalidate(String detailType, Long id) {
        redisTemplate.delete(detailKey(detailType, id));
    }

    /**
     * 从 Redis 读取并反序列化缓存值。
     *
     * @param key       缓存键
     * @param valueType 目标值类型
     * @return 缓存值的 {@link Optional} 包装；若键不存在返回 {@code null}
     */
    private <T> Optional<T> readCached(String key, Class<T> valueType) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            // 检查是否为空值占位（用于防止缓存穿透）
            if (root.path("empty").asBoolean(false)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.treeToValue(root.get("value"), valueType));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read detail cache: " + key, ex);
        }
    }

    /**
     * 加载数据并写入缓存，使用 Redis 分布式互斥锁防止缓存击穿。
     *
     * @param key       缓存键
     * @param valueType 目标值类型
     * @param loader    数据加载器
     * @return 加载到的值
     */
    private <T> Optional<T> loadAndCache(String key, Class<T> valueType, Supplier<Optional<T>> loader) {
        // Redis 分布式互斥锁键，确保集群中只有一个节点执行回源
        String lockKey = key + ":lock";
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", properties.getMutexTtl());
        // 未获取到分布式锁，说明其他节点正在加载，等待其写入缓存
        if (!Boolean.TRUE.equals(locked)) {
            return waitForCachedValue(key, valueType);
        }
        try {
            Optional<T> loaded = loader.get();
            writeCached(key, loaded);
            return loaded;
        } finally {
            // 释放分布式锁
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 等待其他节点完成缓存写入，通过重试轮询检测缓存是否就绪。
     *
     * @param key       缓存键
     * @param valueType 目标值类型
     * @return 缓存值；若重试耗尽仍未就绪则返回 {@link Optional#empty()}
     */
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

    /**
     * 使当前线程休眠指定时间。
     *
     * @param waitMillis 休眠毫秒数
     */
    private void sleep(long waitMillis) {
        try {
            Thread.sleep(waitMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for detail cache lock", ex);
        }
    }

    /**
     * 将值写入 Redis 缓存，根据值是否为空选择不同的 TTL。
     *
     * @param key   缓存键
     * @param value 要缓存的值
     */
    private <T> void writeCached(String key, Optional<T> value) {
        // 将业务值包装为 CachedValue，空值使用 nullValue() 占位
        CachedValue cachedValue = value.<CachedValue>map(CachedValue::hit).orElseGet(CachedValue::nullValue);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(cachedValue), ttl(value.isEmpty()));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write detail cache: " + key, ex);
        }
    }

    /**
     * 计算缓存 TTL，空值使用较短 TTL，正常值使用较长 TTL，并附加随机抖动防止雪崩。
     *
     * @param emptyValue 是否为空值
     * @return 最终 TTL 时长
     */
    private Duration ttl(boolean emptyValue) {
        Duration base = emptyValue ? properties.getNullTtl() : properties.getDetailTtl();
        int jitterSeconds = Math.max(0, properties.getJitterSeconds());
        if (jitterSeconds == 0) {
            return base;
        }
        // 在基础 TTL 上叠加 0 ~ jitterSeconds 秒的随机抖动
        return base.plusSeconds(ThreadLocalRandom.current().nextInt(jitterSeconds + 1));
    }

    /**
     * 构建 Redis 缓存键。
     *
     * @param detailType 详情类型标识
     * @param id         详情主键 ID
     * @return 缓存键字符串，格式为 {@code detail:{type}:{id}}
     */
    private String detailKey(String detailType, Long id) {
        return "detail:" + detailType + ":" + id;
    }
}
