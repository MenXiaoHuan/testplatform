package com.example.platform.evaluation.abtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** ABTrafficAllocator 单测 —— C22 纯 hash 确定性 + 流量分布。 */
class ABTrafficAllocatorTest {

    private ABTrafficAllocator allocator;

    @BeforeEach
    void setUp() {
        allocator = new ABTrafficAllocator();
    }

    // ========== 确定性：同输入永远同输出 ==========

    @Test
    void shouldReturnSameGroupForSameUser() {
        long userId = 12345L;
        long expId = 42L;
        var first = allocator.allocate(userId, expId, 50);
        for (int i = 0; i < 100; i++) {
            assertThat(allocator.allocate(userId, expId, 50)).isEqualTo(first);
        }
    }

    @Test
    void shouldDifferWhenExperimentChanges() {
        // 大量用户下，换 expId 应该打乱分布
        int sameBucket = 0;
        for (long u = 1; u <= 1000; u++) {
            var g1 = allocator.allocate(u, 1L, 50);
            var g2 = allocator.allocate(u, 2L, 50);
            if (g1 == g2) sameBucket++;
        }
        // 理论上约 50% 相同，允许 30%~70% 区间
        assertThat(sameBucket).isBetween(300, 700);
    }

    // ========== 边界：0% / 100% ==========

    @Test
    void shouldAlwaysReturnControlWhenTrafficIsZero() {
        for (long u = 1; u <= 500; u++) {
            assertThat(allocator.allocate(u, 7L, 0))
                    .isEqualTo(ABTrafficAllocator.ABGroup.CONTROL);
        }
    }

    @Test
    void shouldAlwaysReturnTreatmentWhenTrafficIs100() {
        for (long u = 1; u <= 500; u++) {
            assertThat(allocator.allocate(u, 7L, 100))
                    .isEqualTo(ABTrafficAllocator.ABGroup.TREATMENT);
        }
    }

    // ========== 分布均匀性（大数定律） ==========

    @Test
    void shouldDistributeAroundTrafficPercentage() {
        int trafficPct = 30;
        Map<ABTrafficAllocator.ABGroup, Integer> counts = new HashMap<>();
        counts.put(ABTrafficAllocator.ABGroup.CONTROL, 0);
        counts.put(ABTrafficAllocator.ABGroup.TREATMENT, 0);

        for (long u = 1; u <= 10_000; u++) {
            var g = allocator.allocate(u, 99L, trafficPct);
            counts.merge(g, 1, Integer::sum);
        }

        double treatmentPct = counts.get(ABTrafficAllocator.ABGroup.TREATMENT) / 100_00.0 * 100;
        assertThat(treatmentPct).isCloseTo(trafficPct, within(2.0)); // ±2% 误差可接受
    }
}
