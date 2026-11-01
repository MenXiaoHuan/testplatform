package com.example.platform.evaluation.online;

import com.example.platform.evaluation.common.EvaluatorType;
import com.example.platform.evaluation.langfuse.LangfuseClient;
import com.example.platform.evaluation.langfuse.LangfuseTrace;
import com.example.platform.evaluation.offline.OfflineEvaluatorService;
import com.example.platform.evaluation.target.EvalTargetMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** OnlinePolicyScanner 单测 —— 扫描、断点续跑、低分判定与 Langfuse Score 回写。 */
@ExtendWith(MockitoExtension.class)
class OnlinePolicyScannerTest {

    @Mock
    private EvalOnlinePolicyMapper policyMapper;

    @Mock
    private EvalOnlineScoringMapper scoringMapper;

    @Mock
    private LangfuseClient langfuse;

    @Mock
    private OfflineEvaluatorService evaluatorService;

    @Mock
    private EvalTargetMapper targetMapper;

    private OnlinePolicyScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new OnlinePolicyScanner(policyMapper, scoringMapper, langfuse,
                evaluatorService, targetMapper, new ObjectMapper());
    }

    @Test
    void shouldScoreUnseenTraceAndWriteLangfuseScore() {
        EvalOnlinePolicyEntity policy = policy(10L, 1L, 60.0);
        when(evaluatorService.parseSpecs(policy.getEvaluatorConfigs()))
                .thenReturn(List.of(new OfflineEvaluatorService.EvaluatorSpec(
                        EvaluatorType.RULE_MATCH, Map.of(), 1.0)));
        when(langfuse.queryTraces(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(trace()), PageRequest.of(0, 20), 1));
        when(scoringMapper.findIdByPolicyAndTrace(10L, "trace-1")).thenReturn(Optional.empty());
        when(langfuse.getTraceDetail("trace-1")).thenReturn(Optional.empty());
        when(evaluatorService.evaluate(any(), any(), any(), any(), any(), any(), any(), anyString(), any()))
                .thenReturn(new OfflineEvaluatorService.EvaluationOutcome("[]", 55.0));

        OnlinePolicyScanner.ScanResult result = scanner.scanNow(policy);

        assertEquals(1, result.scanned());
        assertEquals(1, result.scored());
        assertEquals(1, result.lowScore());
        assertEquals(0, result.skipped());

        ArgumentCaptor<EvalOnlineScoringEntity> captor =
                ArgumentCaptor.forClass(EvalOnlineScoringEntity.class);
        verify(scoringMapper).insert(captor.capture());
        assertEquals(10L, captor.getValue().getPolicyId());
        assertEquals("trace-1", captor.getValue().getLangfuseTraceId());
        assertEquals(true, captor.getValue().getLowScore());
        assertEquals(55.0, captor.getValue().getWeightedScore().doubleValue());
        verify(langfuse).writeScore(any());
        verify(policyMapper).updateNextScanAt(any(), any());
    }

    @Test
    void shouldSkipAlreadyScoredTrace() {
        EvalOnlinePolicyEntity policy = policy(10L, 1L, 60.0);
        when(evaluatorService.parseSpecs(anyString()))
                .thenReturn(List.of(new OfflineEvaluatorService.EvaluatorSpec(
                        EvaluatorType.RULE_MATCH, Map.of(), 1.0)));
        when(langfuse.queryTraces(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(trace()), PageRequest.of(0, 20), 1));
        when(scoringMapper.findIdByPolicyAndTrace(10L, "trace-1"))
                .thenReturn(Optional.of(1L));

        OnlinePolicyScanner.ScanResult result = scanner.scanNow(policy);

        assertEquals(1, result.scanned());
        assertEquals(1, result.skipped());
        assertEquals(0, result.scored());
    }

    private static EvalOnlinePolicyEntity policy(long id, long targetId, double threshold) {
        EvalOnlinePolicyEntity policy = new EvalOnlinePolicyEntity();
        policy.setId(id);
        policy.setSpaceId(1L);
        policy.setTargetId(targetId);
        policy.setName("policy-" + id);
        policy.setStatus("ACTIVE");
        policy.setEvaluatorConfigs("[{\"type\":\"RULE_MATCH\",\"config\":{\"keywords\":[\"ok\"]}}]");
        policy.setFilterConfig("{\"lookbackHours\":24,\"limit\":20}");
        policy.setAlertingConfig("{\"lowScoreThreshold\":" + threshold + "}");
        policy.setScanIntervalMinutes(5);
        return policy;
    }

    private static LangfuseTrace trace() {
        return new LangfuseTrace("trace-1", "name", "1", "session", null, "ok", "ok",
                10L, 20L, 100L, Instant.now(), Instant.now(),
                List.of("target_id=1", "source=online"), true, Map.of());
    }
}
