package com.example.platform.evaluation.langfuse;

import com.example.platform.evaluation.config.LangfuseProperties;
import com.example.platform.evaluation.exception.LangfuseIntegrationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Langfuse 禁用时的兜底实现（platform.evaluation.langfuse.enabled=false）。
 * 所有方法返回空结果 / 静默吞掉，让评测主流程继续。
 */
@Component
@Primary
@ConditionalOnProperty(name = "platform.evaluation.langfuse.enabled", havingValue = "false", matchIfMissing = true)
public class NoopLangfuseClient implements LangfuseClient {

    @Override public void writeTrace(TraceWriteRequest request) {}
    @Override public void writeObservation(ObservationWriteRequest request) {}
    @Override public void writeScore(ScoreWriteRequest request) {}
    @Override public Page<LangfuseTrace> queryTraces(LangfuseTraceFilter filter, Pageable pageable) { return Page.empty(); }
    @Override public Optional<LangfuseTrace> getTrace(String langfuseTraceId) { return Optional.empty(); }
    @Override public Optional<LangfuseTraceDetail> getTraceDetail(String langfuseTraceId) { return Optional.empty(); }
    @Override public LangfuseMetricsResult aggregateMetrics(MetricsQuery query) {
        return new LangfuseMetricsResult(
                new LangfuseMetricsResult.LatencyMetrics(0, 0, 0, 0, 0),
                new LangfuseMetricsResult.TokenMetrics(0, 0, 0, 0),
                new LangfuseMetricsResult.AutoScoreMetrics(null, null, null, 0),
                List.of());
    }
    @Override public boolean isAvailable() { return false; }
}
