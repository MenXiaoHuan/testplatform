package com.example.platform.evaluation.online;

import com.example.platform.evaluation.common.TargetType;
import com.example.platform.evaluation.langfuse.LangfuseClient;
import com.example.platform.evaluation.langfuse.LangfuseTrace;
import com.example.platform.evaluation.langfuse.LangfuseTraceDetail;
import com.example.platform.evaluation.langfuse.LangfuseTraceFilter;
import com.example.platform.evaluation.langfuse.ScoreWriteRequest;
import com.example.platform.evaluation.offline.OfflineEvaluatorService;
import com.example.platform.evaluation.target.EvalTargetMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 在线策略扫描器 —— 读取策略时间窗口内的 Langfuse Trace，逐条执行评分器并写入留痕。
 *
 * <p>C13 断点续跑：eval_online_scoring 以 (policy_id, langfuse_trace_id) 唯一，已评过直接跳过。
 * C18 预算由 LlmAsJudge 内部 JudgeBudgetController 控制。
 */
@Service
public class OnlinePolicyScanner {

    private static final Logger log = LoggerFactory.getLogger(OnlinePolicyScanner.class);
    private static final int DEFAULT_LOOKBACK_HOURS = 24;
    private static final int DEFAULT_LIMIT = 20;
    private static final double DEFAULT_LOW_SCORE_THRESHOLD = 60.0;

    private final EvalOnlinePolicyMapper policyMapper;
    private final EvalOnlineScoringMapper scoringMapper;
    private final LangfuseClient langfuse;
    private final OfflineEvaluatorService evaluatorService;
    private final EvalTargetMapper targetMapper;
    private final ObjectMapper objectMapper;

    public OnlinePolicyScanner(EvalOnlinePolicyMapper policyMapper,
                               EvalOnlineScoringMapper scoringMapper,
                               LangfuseClient langfuse,
                               OfflineEvaluatorService evaluatorService,
                               EvalTargetMapper targetMapper,
                               ObjectMapper objectMapper) {
        this.policyMapper = policyMapper;
        this.scoringMapper = scoringMapper;
        this.langfuse = langfuse;
        this.evaluatorService = evaluatorService;
        this.targetMapper = targetMapper;
        this.objectMapper = objectMapper;
    }

    public record ScanResult(int scanned, int skipped, int scored, int lowScore, int failed) {}

    public ScanResult scanNow(EvalOnlinePolicyEntity policy) {
        if (policy == null || policy.getTargetId() == null) {
            return new ScanResult(0, 0, 0, 0, 0);
        }

        List<OfflineEvaluatorService.EvaluatorSpec> specs =
                evaluatorService.parseSpecs(policy.getEvaluatorConfigs());
        if (specs.isEmpty()) {
            log.warn("Online policy {} has no evaluator configs, skip scan", policy.getId());
            updateNextScan(policy);
            return new ScanResult(0, 0, 0, 0, 0);
        }

        int lookbackHours = intValue(parseJson(policy.getFilterConfig()), "lookbackHours", DEFAULT_LOOKBACK_HOURS);
        int limit = Math.min(Math.max(intValue(parseJson(policy.getFilterConfig()), "limit", DEFAULT_LIMIT), 1), 100);
        double lowScoreThreshold = doubleValue(parseJson(policy.getAlertingConfig()),
                "lowScoreThreshold", DEFAULT_LOW_SCORE_THRESHOLD);

        Instant from = Instant.now().minus(lookbackHours, ChronoUnit.HOURS);
        Instant to = Instant.now();

        // 内置 Agent 的 Trace 归档为 target_id=builtin_agent + source=builtin_agent；外部 Agent 为数字 target_id + source=online
        String traceTargetId = resolveTraceTargetId(policy.getTargetId());
        List<String> sourceTags = "builtin_agent".equals(traceTargetId)
                ? null : List.of("source=online");

        Page<LangfuseTrace> traces = langfuse.queryTraces(
                new LangfuseTraceFilter(traceTargetId, from, to, sourceTags, null, null, null, null, null),
                PageRequest.of(0, limit));

        int scanned = 0;
        int skipped = 0;
        int scored = 0;
        int lowScore = 0;
        int failed = 0;

        for (LangfuseTrace trace : traces.getContent()) {
            scanned++;
            if (scoringMapper.findIdByPolicyAndTrace(policy.getId(), trace.traceId()).isPresent()) {
                skipped++;
                continue;
            }
            try {
                Optional<LangfuseTraceDetail> detail = langfuse.getTraceDetail(trace.traceId());
                LangfuseTraceDetail traceDetail = detail.orElse(null);
                String agentOutput = traceDetail != null && traceDetail.trace().output() != null
                        ? traceDetail.trace().output() : trace.output();
                String traceInput = traceDetail != null && traceDetail.trace().input() != null
                        ? traceDetail.trace().input() : trace.input();

                OfflineEvaluatorService.EvaluationOutcome outcome = evaluatorService.evaluate(
                        specs, traceInput, agentOutput, null, null, null, null, trace.traceId(), traceDetail);

                BigDecimal weighted = outcome.weightedScore() != null
                        ? BigDecimal.valueOf(outcome.weightedScore()) : null;
                boolean isLow = weighted != null && weighted.doubleValue() < lowScoreThreshold;

                EvalOnlineScoringEntity scoring = new EvalOnlineScoringEntity();
                scoring.setPolicyId(policy.getId());
                scoring.setLangfuseTraceId(trace.traceId());
                scoring.setEvaluatorScores(outcome.evaluatorScoresJson());
                scoring.setWeightedScore(weighted);
                scoring.setLowScore(isLow);
                scoringMapper.insert(scoring);

                if (weighted != null) {
                    langfuse.writeScore(ScoreWriteRequest.builder()
                            .traceId(trace.traceId())
                            .name("online_weighted")
                            .value(weighted.doubleValue())
                            .comment("策略: " + policy.getName())
                            .build());
                }
                scored++;
                if (isLow) {
                    lowScore++;
                    log.warn("Online policy '{}' low score: trace={}, score={}, threshold={}",
                            policy.getName(), trace.traceId(), weighted, lowScoreThreshold);
                }
            } catch (Exception e) {
                failed++;
                log.warn("Online policy {} scan trace {} failed: {}",
                        policy.getId(), trace.traceId(), e.getMessage());
            }
        }

        updateNextScan(policy);
        log.info("Online policy '{}' scanned={}, skipped={}, scored={}, lowScore={}, failed={}",
                policy.getName(), scanned, skipped, scored, lowScore, failed);
        return new ScanResult(scanned, skipped, scored, lowScore, failed);
    }

    /** 内置 Agent 的目标统一映射到归档标签 builtin_agent。 */
    private String resolveTraceTargetId(Long targetId) {
        return targetMapper.findById(targetId)
                .filter(target -> TargetType.BUILTIN_AGENT.name()
                        .equalsIgnoreCase(target.getTargetType()))
                .map(target -> "builtin_agent")
                .orElseGet(targetId::toString);
    }

    private void updateNextScan(EvalOnlinePolicyEntity policy) {
        int interval = policy.getScanIntervalMinutes() != null && policy.getScanIntervalMinutes() > 0
                ? policy.getScanIntervalMinutes() : 5;
        policyMapper.updateNextScanAt(policy.getId(), LocalDateTime.now().plusMinutes(interval));
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private static int intValue(JsonNode node, String field, int defaultValue) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asInt() : defaultValue;
    }

    private static double doubleValue(JsonNode node, String field, double defaultValue) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asDouble() : defaultValue;
    }
}
