package com.example.platform.evaluation.offline;

import com.example.platform.evaluation.common.EvalRunStatus;
import com.example.platform.evaluation.exception.EvalRunConcurrencyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * C19 启动幂等 —— Redis NX 60s 内拦截相同 spaceId + datasetId + promptVersionId + targetId 的重复启动。
 */
@Service
public class OfflineRunIdempotentService {

    private final StringRedisTemplate redis;

    public OfflineRunIdempotentService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 尝试获取启动令牌；返回 true = 允许启动，false = 同配置 Run 已在 60s 内启动过。 */
    public boolean tryAcquire(Long spaceId, Long datasetId, Long promptVersionId, Long targetId) {
        String key = "eval:run:dedupe:" + spaceId + ":" + datasetId + ":" +
                (promptVersionId != null ? promptVersionId : "null") + ":" + targetId;
        Boolean acquired = redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(60));
        return Boolean.TRUE.equals(acquired);
    }
}
