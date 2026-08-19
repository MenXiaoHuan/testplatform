package com.example.platform.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 缓存配置属性 —— 详情端点 Redis 缓存键的可调保护参数。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #detailTtl} —— 详情缓存正常命中的存活时间（默认 5 分钟）</li>
 *   <li>{@link #nullTtl} —— 缓存空值的存活时间（默认 1 分钟），防止缓存穿透</li>
 *   <li>{@link #jitterSeconds} —— TTL 随机抖动秒数，防止缓存雪崩</li>
 *   <li>{@link #mutexTtl} —— Redis 分布式互斥锁的存活时间</li>
 *   <li>{@link #lockRetryTimes} —— 等待互斥锁的重试次数</li>
 *   <li>{@link #lockWaitMillis} —— 每次重试的间隔毫秒数</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link org.springframework.boot.context.properties.ConfigurationProperties} —— 绑定前缀为 {@code platform.cache} 的配置项</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "platform.cache")
public class CacheProperties {

    /** 详情缓存正常命中的存活时间 */
    private Duration detailTtl = Duration.ofMinutes(5);

    /** 缓存空值的存活时间，用于防止缓存穿透 */
    private Duration nullTtl = Duration.ofMinutes(1);

    /** TTL 随机抖动秒数，用于防止缓存雪崩 */
    private int jitterSeconds = 60;

    /** Redis 分布式互斥锁的存活时间 */
    private Duration mutexTtl = Duration.ofSeconds(5);

    /** 等待互斥锁的最大重试次数 */
    private int lockRetryTimes = 3;

    /** 每次重试的间隔毫秒数 */
    private long lockWaitMillis = 50;

    /** 获取详情缓存存活时间 */
    public Duration getDetailTtl() {
        return detailTtl;
    }

    /** 设置详情缓存存活时间 */
    public void setDetailTtl(Duration detailTtl) {
        this.detailTtl = detailTtl;
    }

    /** 获取空值缓存存活时间 */
    public Duration getNullTtl() {
        return nullTtl;
    }

    /** 设置空值缓存存活时间 */
    public void setNullTtl(Duration nullTtl) {
        this.nullTtl = nullTtl;
    }

    /** 获取 TTL 抖动秒数 */
    public int getJitterSeconds() {
        return jitterSeconds;
    }

    /** 设置 TTL 抖动秒数 */
    public void setJitterSeconds(int jitterSeconds) {
        this.jitterSeconds = jitterSeconds;
    }

    /** 获取互斥锁存活时间 */
    public Duration getMutexTtl() {
        return mutexTtl;
    }

    /** 设置互斥锁存活时间 */
    public void setMutexTtl(Duration mutexTtl) {
        this.mutexTtl = mutexTtl;
    }

    /** 获取锁重试次数 */
    public int getLockRetryTimes() {
        return lockRetryTimes;
    }

    /** 设置锁重试次数 */
    public void setLockRetryTimes(int lockRetryTimes) {
        this.lockRetryTimes = lockRetryTimes;
    }

    /** 获取锁等待间隔毫秒数 */
    public long getLockWaitMillis() {
        return lockWaitMillis;
    }

    /** 设置锁等待间隔毫秒数 */
    public void setLockWaitMillis(long lockWaitMillis) {
        this.lockWaitMillis = lockWaitMillis;
    }
}
