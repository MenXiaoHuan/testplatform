package com.example.platform.evaluation.langfuse;

import java.util.List;

/** Langfuse 监控聚合结果。 */
public record LangfuseMetricsResult(
        LatencyMetrics latency,
        TokenMetrics tokens,
        AutoScoreMetrics autoScore,
        List<TimeSeriesPoint> timeSeries) {

    public record LatencyMetrics(
            long p50Ms,
            long p95Ms,
            long p99Ms,
            double avgMs,
            double stdDevMs) {}

    public record TokenMetrics(
            long inputTotal,
            long outputTotal,
            double avgPerRequest,
            long requestCount) {}

    public record AutoScoreMetrics(
            Double avgScore,
            Double p50,
            Double p95,
            long scoreCount) {}

    public record TimeSeriesPoint(
            long bucketStartMillis,
            long requestCount,
            double avgLatencyMs) {}
}
