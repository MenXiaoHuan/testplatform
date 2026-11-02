package com.example.platform.evaluation.online;

import com.example.platform.evaluation.langfuse.LangfuseClient;
import com.example.platform.evaluation.langfuse.LangfuseTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Trace 过期清理：默认保留 90 天。
 *
 * <p>Langfuse 本身不会自动清理 Trace，长期运行会持续膨胀；这里每天删除早于
 * 保留期的 Trace（含其 observations）。平台 MySQL 中的评测结果与运行统计不受影响，
 * 只有观测明细会被清理。
 *
 * <p>可通过 {@code platform.evaluation.trace-retention.days} 调整保留天数（&lt;=0 关闭），
 * 通过 {@code platform.evaluation.trace-retention.cron} 调整执行时间。
 */
@Service
public class TraceRetentionService {

    private static final Logger log = LoggerFactory.getLogger(TraceRetentionService.class);
    private static final int DELETE_BATCH = 100;
    private static final int MAX_ROUNDS = 200;

    private final LangfuseClient langfuseClient;
    private final int retentionDays;

    public TraceRetentionService(LangfuseClient langfuseClient,
                                 @Value("${platform.evaluation.trace-retention.days:90}") int retentionDays) {
        this.langfuseClient = langfuseClient;
        this.retentionDays = retentionDays;
        log.info("TraceRetentionService initialized: retentionDays={}", retentionDays);
    }

    @Scheduled(cron = "${platform.evaluation.trace-retention.cron:0 30 2 * * *}")
    public void purgeExpiredTraces() {
        if (retentionDays <= 0) {
            log.info("Trace retention disabled (retentionDays <= 0), skip purge");
            return;
        }
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = 0;
        int failed = 0;
        // ClickHouse 的删除是异步的，删除后列表可能仍返回同一条，用 attempted 去重避免死循环
        Set<String> attempted = new HashSet<>();
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<LangfuseTrace> candidates = langfuseClient.listTracesBefore(cutoff, DELETE_BATCH);
            List<LangfuseTrace> pending = candidates.stream()
                    .filter(trace -> trace.traceId() != null && !attempted.contains(trace.traceId()))
                    .toList();
            if (pending.isEmpty()) {
                break;
            }
            for (LangfuseTrace trace : pending) {
                attempted.add(trace.traceId());
                if (langfuseClient.deleteTrace(trace.traceId())) {
                    deleted++;
                } else {
                    failed++;
                }
            }
        }
        log.info("Trace retention purge finished: cutoff={}, deleted={}, failed={}", cutoff, deleted, failed);
    }
}
