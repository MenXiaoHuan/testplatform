package com.example.platform.ai;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * AI 用量保护：每日次数限制 + 全局并发限制。
 *
 * <p>个人部署最怕的不是被攻击，而是 API key 被脚本刷爆。这里不引入配额系统，
 * 只做两件最便宜的事：
 * <ul>
 *   <li>Redis 按用户/天计数，超过 daily-limit 直接拒绝；Redis 不可用时 fail-open，不影响主流程；</li>
 *   <li>全局 Semaphore 限制同时进行的模型调用，避免大量 SSE 连接同时打到模型服务。</li>
 * </ul>
 */
@Service
public class AgentQuotaService {
    private static final Logger log = LoggerFactory.getLogger(AgentQuotaService.class);

    private final StringRedisTemplate redisTemplate;
    private final int dailyLimit;
    private final Semaphore concurrency;

    public AgentQuotaService(
            StringRedisTemplate redisTemplate,
            @Value("${platform.ai.quota.daily-limit:50}") int dailyLimit,
            @Value("${platform.ai.quota.max-concurrent:4}") int maxConcurrent) {
        this.redisTemplate = redisTemplate;
        this.dailyLimit = Math.max(0, dailyLimit);
        this.concurrency = new Semaphore(Math.max(1, maxConcurrent), true);
    }

    public Permit acquire(Long userId) {
        checkDailyLimit(userId);
        try {
            if (!concurrency.tryAcquire(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("AI 助手当前繁忙，请稍后再试");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI 调用被中断，请重试", exception);
        }
        return new Permit(concurrency);
    }

    private void checkDailyLimit(Long userId) {
        if (dailyLimit <= 0 || userId == null) {
            return;
        }
        String key = "agent:quota:%d:%s".formatted(userId, LocalDate.now());
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofDays(2));
            }
            if (count != null && count > dailyLimit) {
                throw new IllegalStateException("今日 AI 调用次数已达上限（" + dailyLimit + " 次），请明天再试");
            }
        } catch (DataAccessException exception) {
            // 配额是保护措施，不应在 Redis 抖动时把 AI 完全打挂。
            log.warn("Failed to check AI daily quota, allowing request. userId={}, reason={}",
                    userId, exception.getMessage());
        }
    }

    /** 释放并发许可；关闭多次也只释放一次。 */
    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private final AtomicBoolean released = new AtomicBoolean(false);

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                semaphore.release();
            }
        }
    }
}
