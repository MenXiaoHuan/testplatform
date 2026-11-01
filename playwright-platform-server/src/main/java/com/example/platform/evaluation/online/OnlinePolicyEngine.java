package com.example.platform.evaluation.online;

import com.example.platform.evaluation.config.EvaluationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 在线评测策略引擎主循环 —— 每分钟检查一次 eval_online_policy，
 * 挑出 status=ACTIVE 且 next_scan_at ≤ now 的策略，交给 {@link OnlinePolicyScanner} 扫描。
 * 扫描频率由每条策略的 filter/next_scan_at 控制。
 */
@Component
public class OnlinePolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(OnlinePolicyEngine.class);

    private final EvaluationProperties props;
    private final EvalOnlinePolicyMapper policyMapper;
    private final OnlinePolicyScanner scanner;

    public OnlinePolicyEngine(EvaluationProperties props,
                              EvalOnlinePolicyMapper policyMapper,
                              OnlinePolicyScanner scanner) {
        this.props = props;
        this.policyMapper = policyMapper;
        this.scanner = scanner;
    }

    @Scheduled(fixedDelay = 60_000)
    public void scanOnce() {
        List<EvalOnlinePolicyEntity> duePolicies = policyMapper.listDuePolicies(LocalDateTime.now());
        if (duePolicies.isEmpty()) return;
        for (EvalOnlinePolicyEntity policy : duePolicies) {
            try {
                scanner.scanNow(policy);
            } catch (Exception e) {
                log.warn("Online policy {} scan failed: {}", policy.getId(), e.getMessage());
            }
        }
    }

    /** C17 抖动：interval ± 20%，基于 (policyId + now.truncatedTo(HOUR)) hash。 */
    public Instant computeNextScanAt(long policyId, Instant baseTime) {
        int intervalMin = props.getOnline().getPolicyScanBaseIntervalMinutes();
        int jitterPct = props.getOnline().getPolicyScanJitterPercent();
        long jitter = ((policyId * 9301L + baseTime.toEpochMilli() / 3600_000L) % (jitterPct * 2L)) - jitterPct;
        return baseTime.plus(intervalMin + jitter, ChronoUnit.MINUTES);
    }
}
