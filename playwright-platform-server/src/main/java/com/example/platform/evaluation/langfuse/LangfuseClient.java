package com.example.platform.evaluation.langfuse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Langfuse REST API 封装 —— 评测模块所有 Trace/Observation/Score 操作的唯一出入口。
 *
 * <p>读写 Key 分离由实现层负责（C12），并发闸门由 {@link LangfuseGatekeeper} 负责（C21）。
 * 写入操作不可阻塞评测主链路：实现层应捕获 HTTP 异常后仅日志告警。
 */
public interface LangfuseClient {

    /** 写入一条 Trace 头部（生产调用自动埋点 + 离线评测同步用）。 */
    void writeTrace(TraceWriteRequest request);

    /** 写入一条 Observation（Span）。 */
    void writeObservation(ObservationWriteRequest request);

    /** 写入 Score（在线策略自动评分 + 手动评分 + LLM-as-Judge 回写）。 */
    void writeScore(ScoreWriteRequest request);

    /**
     * 查询 Trace 列表（C12 只读 Key）。
     * 内部通过 LangfuseGatekeeper 控制并发（C21）。
     */
    Page<LangfuseTrace> queryTraces(LangfuseTraceFilter filter, Pageable pageable);

    /** 查询单条 Trace 详情（仅 Trace 头部）。 */
    Optional<LangfuseTrace> getTrace(String langfuseTraceId);

    /** 查询 Trace 详情 + 完整 Observation 链路（Trace 浏览器用）。 */
    Optional<LangfuseTraceDetail> getTraceDetail(String langfuseTraceId);

    /** 聚合监控指标（延迟 P50/P95/Token/评分分布）。结果带 60s 本地缓存。 */
    LangfuseMetricsResult aggregateMetrics(MetricsQuery query);

    /** 健康检查（用于策略引擎检测 Langfuse 不可用 → 自动暂停）。 */
    boolean isAvailable();

    /** 查询早于 cutoff 创建的 Trace（按时间升序，供过期清理使用）。 */
    List<LangfuseTrace> listTracesBefore(Instant cutoff, int limit);

    /** 删除单条 Trace（含其 observations），供过期清理使用；成功返回 true。 */
    boolean deleteTrace(String langfuseTraceId);
}
