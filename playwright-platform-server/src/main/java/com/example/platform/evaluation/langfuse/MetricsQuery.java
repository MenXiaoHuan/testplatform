package com.example.platform.evaluation.langfuse;

import java.time.Instant;

/** 监控聚合查询条件。 */
public record MetricsQuery(
        String targetId,
        Instant from,
        Instant to,
        Granularity granularity) {

    public enum Granularity { MINUTE, HOUR, DAY }
}
