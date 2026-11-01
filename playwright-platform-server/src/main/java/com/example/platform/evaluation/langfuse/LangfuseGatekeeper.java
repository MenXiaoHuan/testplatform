package com.example.platform.evaluation.langfuse;

import com.example.platform.evaluation.config.EvaluationProperties;
import com.example.platform.evaluation.exception.LangfuseIntegrationException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;

/**
 * C21 Langfuse 全局并发闸门 + 本地查询缓存。
 *
 * <p>并发闸门：控制同时 queryTraces / aggregateMetrics 的 HTTP 请求数不超过
 * {@code platform.evaluation.online.langfuse-gatekeeper-max-concurrent}（默认 8），
 * 超过部分在 Semaphore 上排队，避免把 Langfuse 实例打挂。
 *
 * <p>缓存：aggregateMetrics 结果按 targetId + 时间窗口 hash 做 key，
 * TTL 默认 60s（platform.evaluation.online.langfuse-query-cache-ttl-seconds）。
 */
@Component
public class LangfuseGatekeeper {

    private static final Logger log = LoggerFactory.getLogger(LangfuseGatekeeper.class);

    private final Semaphore semaphore;
    private final Cache<String, LangfuseMetricsResult> metricsCache;
    private final long acquireTimeoutSeconds;

    public LangfuseGatekeeper(EvaluationProperties props) {
        int maxConcurrent = props.getOnline().getLangfuseGatekeeperMaxConcurrent();
        long ttl = props.getOnline().getLangfuseQueryCacheTtlSeconds();
        this.semaphore = new Semaphore(maxConcurrent);
        this.metricsCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttl))
                .maximumSize(512)
                .build();
        this.acquireTimeoutSeconds = 30;
        log.info("LangfuseGatekeeper initialized: maxConcurrent={}, cacheTtl={}s",
                maxConcurrent, ttl);
    }

    /**
     * 包装 Langfuse 查询操作，控制并发。超时后抛出 LangfuseIntegrationException，
     * 由上层决定如何处理（在线策略 → 暂停；离线评测 → 用上次缓存）。
     */
    public <T> T acquire(CheckedSupplier<T> action) {
        boolean acquired = false;
        try {
            acquired = semaphore.tryAcquire(acquireTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            if (!acquired) {
                throw new LangfuseIntegrationException(
                        "Langfuse 查询并发闸门等待超时（> " + acquireTimeoutSeconds + "s），已达最大并发");
            }
            return action.get();
        } catch (LangfuseIntegrationException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LangfuseIntegrationException("Langfuse 查询被中断", e);
        } catch (Exception e) {
            throw new LangfuseIntegrationException("Langfuse 查询异常: " + e.getMessage(), e);
        } finally {
            if (acquired) semaphore.release();
        }
    }

    /**
     * 带缓存的 metrics 查询 —— 先查缓存，miss 再走 acquire。
     */
    public LangfuseMetricsResult queryWithCache(MetricsQuery query, CheckedSupplier<LangfuseMetricsResult> loader) {
        String key = cacheKey(query);
        LangfuseMetricsResult cached = metricsCache.getIfPresent(key);
        if (cached != null) return cached;

        LangfuseMetricsResult fresh = acquire(loader);
        metricsCache.put(key, fresh);
        return fresh;
    }

    private String cacheKey(MetricsQuery q) {
        return q.targetId() + "|"
                + (q.from() != null ? q.from().toEpochMilli() : "") + "|"
                + (q.to() != null ? q.to().toEpochMilli() : "") + "|"
                + q.granularity();
    }

    /** 清除所有缓存（策略引擎恢复时调用）。 */
    public void evictAll() {
        metricsCache.invalidateAll();
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
