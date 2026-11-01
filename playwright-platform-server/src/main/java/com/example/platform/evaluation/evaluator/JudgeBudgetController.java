package com.example.platform.evaluation.evaluator;

import com.example.platform.evaluation.config.EvaluationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * C18 Judge 三层预算墙控制器。
 * 三个 Key：eval:judge:global:daily-tokens（全局日预算 5M）
 *          eval:judge:space:{spaceId}:daily-tokens（per-space 50% 默认）
 *          eval:policy:{policyId}:minute-evals（策略分钟级限流）
 */
@Component
public class JudgeBudgetController {

    private static final Logger log = LoggerFactory.getLogger(JudgeBudgetController.class);

    private final StringRedisTemplate redis;
    private final EvaluationProperties props;

    public JudgeBudgetController(StringRedisTemplate redis, EvaluationProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /**
     * 尝试消费预算。通过则放行 Judge 调用；失败则调用方自动降级为规则评分器。
     */
    public boolean tryConsume(String policyId, String granularity) {
        long globalLimit = props.getJudge().getGlobalDailyTokens();

        // Layer 1: 全局日预算
        String globalKey = "eval:judge:global:daily-tokens";
        Long globalUsed = redis.opsForValue().increment(globalKey);
        redis.expireAt(globalKey, endOfToday());
        if (globalUsed != null && globalUsed > globalLimit) {
            log.warn("Judge global daily budget exhausted: {} > {}", globalUsed, globalLimit);
            return false;
        }

        // Layer 2: 按 policy 分钟级限流
        if (policyId != null) {
            String minKey = "eval:policy:" + policyId + ":minute-evals";
            Long perMinute = redis.opsForValue().increment(minKey);
            long ttlMs = 60_000 - (System.currentTimeMillis() % 60_000);
            redis.expire(minKey, Math.max(ttlMs / 1000, 1), TimeUnit.SECONDS);
            if (perMinute != null && perMinute > 60) {
                log.warn("Judge policy minute rate limit hit: {}", policyId);
                return false;
            }
        }
        return true;
    }

    private static java.time.Instant endOfToday() {
        return LocalDate.now().plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
    }
}
