package com.example.platform.evaluation.abtest;

import org.springframework.stereotype.Service;

/**
 * C22 纯计算流量分配 —— 不依赖 DB/Redis，纯 hash。
 * 确定性：同一 (userId, experimentId) 永远落在同一 bucket。
 * 分布：10000 用户 × 30% 流量 → 约 3000 TREATMENT。
 */
@Service
public class ABTrafficAllocator {

    public enum ABGroup { CONTROL, TREATMENT }

    /**
     * 分配用户到 CONTROL / TREATMENT。
     * @param trafficPercentage 分配给 TREATMENT 的百分比（0-100）
     */
    public ABGroup allocate(long userId, long experimentId, int trafficPercentage) {
        long bucket = (Math.abs(userId * 31L + experimentId * 17L)) % 100;
        return bucket < trafficPercentage ? ABGroup.TREATMENT : ABGroup.CONTROL;
    }
}
