package com.example.platform.evaluation.offline;

import com.example.platform.common.BusinessException;
import com.example.platform.evaluation.common.EvalRunStatus;
import com.example.platform.evaluation.common.EvaluationErrorCode;
import com.example.platform.evaluation.common.TargetType;
import com.example.platform.evaluation.dataset.EvalDatasetSampleEntity;
import com.example.platform.evaluation.dataset.EvalDatasetSampleMapper;
import com.example.platform.evaluation.exception.EvalRunConcurrencyException;
import com.example.platform.evaluation.langfuse.LangfuseClient;
import com.example.platform.evaluation.langfuse.LangfuseObservation;
import com.example.platform.evaluation.langfuse.LangfuseTrace;
import com.example.platform.evaluation.langfuse.LangfuseTraceDetail;
import com.example.platform.evaluation.langfuse.ObservationWriteRequest;
import com.example.platform.evaluation.langfuse.TraceWriteRequest;
import com.example.platform.evaluation.target.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 离线评测主入口 —— 创建 Run → 调度执行 → 聚合报告。
 * 核心实现：C15 双层并发护栏 + C16 per-Target 护栏 + C19 幂等 + C20 乐观锁。
 */
@Service
public class OfflineEvaluationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OfflineEvaluationOrchestrator.class);

    private final EvalRunMapper runMapper;
    private final EvalDatasetSampleMapper sampleMapper;
    private final EvalRunCaseResultMapper caseResultMapper;
    private final TargetResolver targetResolver;
    private final OfflineConcurrencyGuard concurrencyGuard;
    private final OfflineRunIdempotentService idempotentService;
    private final OfflineRunStatusMutator statusMutator;
    private final LangfuseClient langfuse;
    private final OfflineEvaluatorService evaluatorService;
    private final ExecutorService globalExecutor;

    public OfflineEvaluationOrchestrator(EvalRunMapper runMapper,
                                         EvalDatasetSampleMapper sampleMapper,
                                         EvalRunCaseResultMapper caseResultMapper,
                                         TargetResolver targetResolver,
                                         OfflineConcurrencyGuard concurrencyGuard,
                                         OfflineRunIdempotentService idempotentService,
                                         OfflineRunStatusMutator statusMutator,
                                         LangfuseClient langfuse,
                                         OfflineEvaluatorService evaluatorService) {
        this.runMapper = runMapper;
        this.sampleMapper = sampleMapper;
        this.caseResultMapper = caseResultMapper;
        this.targetResolver = targetResolver;
        this.concurrencyGuard = concurrencyGuard;
        this.idempotentService = idempotentService;
        this.statusMutator = statusMutator;
        this.langfuse = langfuse;
        this.evaluatorService = evaluatorService;
        this.globalExecutor = Executors.newFixedThreadPool(8,
                r -> { Thread t = new Thread(r, "offline-run-worker"); t.setDaemon(true); return t; });
    }

    /**
     * 创建并启动一条 EvalRun。
     * 顺序：C19 幂等 → C15/C16 护栏 → DB 建 PENDING → 提交线程池 → 异步执行。
     */
    public EvalRunEntity startRun(StartRunRequest req) {
        // 先确认目标存在且可用，避免创建一条注定失败的 Run
        TargetResolver.ResolvedTarget resolved = targetResolver.resolve(req.targetId());
        String endpoint = resolved.config().getString("endpoint");
        if (resolved.config().targetType() == TargetType.HTTP_AGENT
                && (endpoint == null || endpoint.isBlank())) {
            throw new BusinessException(
                    EvaluationErrorCode.BAD_REQUEST,
                    "该外部 Agent 未配置离线评测 Endpoint，仅支持在线 Trace");
        }

        // C19
        if (!idempotentService.tryAcquire(req.spaceId(), req.datasetId(), req.promptVersionId(), req.targetId())) {
            throw new EvalRunConcurrencyException("RUN_DEDUPLICATED",
                    "相同配置的评测已在 60s 内启动，请稍后再试");
        }
        // C15 + C16
        concurrencyGuard.checkAndAcquire(req.spaceId(), req.targetId());

        // 建 PENDING
        EvalRunEntity run = new EvalRunEntity();
        run.setSpaceId(req.spaceId());
        run.setTargetId(req.targetId());
        run.setPromptVersionId(req.promptVersionId());
        run.setDatasetId(req.datasetId());
        run.setStatus(EvalRunStatus.PENDING.name());
        run.setEvaluatorConfigs(req.evaluatorConfigs());
        run.setMaxConcurrency(req.maxConcurrency());
        run.setTriggerUser(req.triggerUser());
        // 只评测用户选中的用例；未选择时执行评测集全部样本
        List<Long> selectedIds = req.sampleIds() == null ? List.of()
                : req.sampleIds().stream().filter(Objects::nonNull).distinct().toList();
        List<EvalDatasetSampleEntity> selectedSamples = null;
        int total;
        if (selectedIds.isEmpty()) {
            total = sampleMapper.countByDataset(req.datasetId());
        } else {
            selectedSamples = sampleMapper.listByDatasetAndIds(req.datasetId(), selectedIds);
            if (selectedSamples.isEmpty()) {
                throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "所选样本不存在或不属于该评测集");
            }
            total = selectedSamples.size();
        }
        run.setTotalSamples(total);
        run.setCompletedSamples(0);
        run.setFailedSamples(0);
        run.setVersion(0L);
        runMapper.insert(run);

        // 异步执行（选中的样本直接透传，避免异步线程重复查询）
        List<EvalDatasetSampleEntity> preloaded = selectedSamples;
        globalExecutor.submit(() -> execute(run.getId(), preloaded));
        return run;
    }

    /** 执行循环 —— PENDING→RUNNING→COMPLETED/FAILED，乐观锁保证状态机正确。 */
    private void execute(Long runId, List<EvalDatasetSampleEntity> preloadedSamples) {
        try {
            // 1. PENDING → RUNNING（乐观锁）
            if (!statusMutator.transition(runId, EvalRunStatus.PENDING.name(), EvalRunStatus.RUNNING.name())) {
                log.warn("runId={} failed to transition PENDING→RUNNING (version conflict)", runId);
                return;
            }

            // 2. 读样本 + 解析被评目标（按 eval_target.target_type 路由 Adapter，读取真实 adapter_config / runtime_options）
            EvalRunEntity run = runMapper.findById(runId).get();
            List<EvalDatasetSampleEntity> samples = preloadedSamples != null
                    ? preloadedSamples
                    : sampleMapper.listByDataset(run.getDatasetId());

            TargetResolver.ResolvedTarget resolvedTarget;
            try {
                resolvedTarget = targetResolver.resolve(run.getTargetId());
            } catch (Exception e) {
                log.error("runId={} target resolve failed: {}", runId, e.getMessage());
                runMapper.forceStatusUpdate(runId, EvalRunStatus.FAILED.name(), 0, 0, null);
                return;
            }
            TargetAdapter adapter = resolvedTarget.adapter();
            TargetConfig config = resolvedTarget.config();
            List<OfflineEvaluatorService.EvaluatorSpec> evaluatorSpecs =
                    evaluatorService.parseSpecs(run.getEvaluatorConfigs());
            log.info("runId={} target={} type={} timeoutMs={} maxRetries={} evaluators={}",
                    runId, config.targetName(), config.targetType(), config.timeoutMs(),
                    config.maxRetries(), evaluatorSpecs.size());

            // 3. 并发执行（semaphore 控制 per-run 并发）
            int perRunConcurrency = run.getMaxConcurrency() != null ? run.getMaxConcurrency() : 2;
            Semaphore sem = new Semaphore(perRunConcurrency);
            AtomicInteger completed = new AtomicInteger();
            AtomicInteger failed = new AtomicInteger();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < samples.size(); i++) {
                EvalDatasetSampleEntity sample = samples.get(i);
                int sampleIndex = sample.getSampleIndex() != null ? sample.getSampleIndex() : (i + 1);
                sem.acquire();
                CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                    boolean success = false;
                    try {
                        success = executeSample(run, sample, sampleIndex, adapter, config, evaluatorSpecs);
                    } catch (Exception e) {
                        log.warn("runId={} sampleIndex={} failed: {}", runId, sampleIndex, e.getMessage());
                    } finally {
                        if (success) completed.incrementAndGet();
                        else failed.incrementAndGet();
                        runMapper.updateProgress(runId, completed.get(), failed.get());
                        sem.release();
                    }
                }, globalExecutor);
                futures.add(f);
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 4. RUNNING → COMPLETED / FAILED
            EvalRunEntity finalRun = runMapper.findById(runId).get();
            String finalStatus = failed.get() == finalRun.getTotalSamples()
                    ? EvalRunStatus.FAILED.name() : EvalRunStatus.COMPLETED.name();
            runMapper.forceStatusUpdate(runId, finalStatus, completed.get(), failed.get(),
                    averageWeightedScore(runId, completed.get(), finalRun.getTotalSamples()));
        } catch (Exception e) {
            log.error("runId={} execution error", runId, e);
            runMapper.forceStatusUpdate(runId, EvalRunStatus.FAILED.name(), 0, 0, null);
        }
    }

    /**
     * 执行单个样本，并完成「Trace 采集 + 样本结果落库」：
     * <ol>
     *   <li>为样本生成 traceId / sessionId，先写 Trace 头部（带 target_id / source=offline / run_id 标签）</li>
     *   <li>带 TraceContext 调用 Adapter（HTTP Agent 会收到 traceparent，可把内部 span 接进同一条 trace）</li>
     *   <li>写平台侧 Observation（LLM_API 为 generation，其余为 span）</li>
     *   <li>落 eval_run_case_result，保存 langfuse_trace_id，供报告页打开完整链路</li>
     * </ol>
     */
    private boolean executeSample(EvalRunEntity run, EvalDatasetSampleEntity sample, int sampleIndex,
                                  TargetAdapter adapter, TargetConfig config,
                                  List<OfflineEvaluatorService.EvaluatorSpec> evaluatorSpecs) {
        String traceId = TraceContext.newTraceId();
        String sessionId = "eval-" + run.getId() + "-" + sampleIndex;
        TraceContext traceContext = new TraceContext(
                traceId, sessionId, run.getId(), sampleIndex, run.getTargetId(), run.getSpaceId());
        Instant start = Instant.now();

        Map<String, Object> traceMetadata = new LinkedHashMap<>();
        traceMetadata.put("source", "offline");
        traceMetadata.put("runId", run.getId());
        traceMetadata.put("sampleIndex", sampleIndex);
        traceMetadata.put("sampleId", sample.getId());

        List<String> tags = List.of(
                "space_id=" + run.getSpaceId(),
                "target_id=" + run.getTargetId(),
                "source=offline",
                "eval_run_id=" + run.getId());

        langfuse.writeTrace(TraceWriteRequest.builder()
                .traceId(traceId)
                .name("offline-" + config.targetName())
                .sessionId(sessionId)
                .tags(tags)
                .metadata(traceMetadata)
                .timestamp(start)
                .build());

        AgentResponse resp = null;
        String errorMessage = null;
        try {
            resp = adapter.invoke(PromptInput.user(sample.getInputText()), config, null, traceContext);
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.debug("runId={} sampleIndex={} invoke failed: {}", run.getId(), sampleIndex, e.getMessage());
        }

        Instant end = Instant.now();
        boolean success = resp != null && resp.success() && errorMessage == null;
        long durationMs = resp != null && resp.durationMs() != null && resp.durationMs() > 0
                ? resp.durationMs() : Duration.between(start, end).toMillis();

        String model = config.targetType() == TargetType.LLM_API ? config.getString("model") : null;
        String observationId = UUID.randomUUID().toString();
        langfuse.writeObservation(ObservationWriteRequest.builder()
                .traceId(traceId)
                .observationId(observationId)
                .name(config.targetName())
                .input(sample.getInputText())
                .output(resp != null ? resp.output() : null)
                .startTime(start)
                .endTime(end)
                .inputTokens(resp != null ? resp.inputTokens() : null)
                .outputTokens(resp != null ? resp.outputTokens() : null)
                .model(model)
                .success(success)
                .level(success ? null : "ERROR")
                .build());

        String agentTraceId = resp != null ? resp.langfuseTraceId() : null;
        // Agent 返回自己的 traceId 时优先用它（内部链路可能由 Agent 自行上报）；否则用平台生成的 traceId
        String effectiveTraceId =
                agentTraceId != null && !agentTraceId.isBlank() ? agentTraceId : traceId;
        String agentOutput = resp != null && resp.output() != null ? resp.output() : "";
        String failureMessage = errorMessage != null ? errorMessage
                : (resp != null ? resp.errorMessage() : "调用失败");

        LangfuseTraceDetail localDetail = buildLocalTraceDetail(
                traceId, sessionId, run, config, sample, agentOutput, resp,
                success, durationMs, start, end, tags, traceMetadata, observationId, model);

        OfflineEvaluatorService.EvaluationOutcome evaluation;
        try {
            evaluation = evaluatorService.evaluate(
                    evaluatorSpecs, sample.getInputText(), agentOutput, sample.getGoldenAnswer(),
                    sample.getKeywords(), sample.getReferenceContext(), sample.getMetadata(),
                    effectiveTraceId, localDetail);
        } catch (Exception e) {
            log.warn("runId={} sampleIndex={} evaluator failed: {}",
                    run.getId(), sampleIndex, e.getMessage());
            evaluation = new OfflineEvaluatorService.EvaluationOutcome("[]", null);
        }

        EvalRunCaseResultEntity caseResult = new EvalRunCaseResultEntity();
        caseResult.setRunId(run.getId());
        caseResult.setSampleId(sample.getId());
        caseResult.setSampleIndex(sampleIndex);
        caseResult.setAgentOutput(agentOutput);
        caseResult.setGoldenAnswer(sample.getGoldenAnswer());
        caseResult.setEvaluatorScores(evaluation.evaluatorScoresJson());
        caseResult.setWeightedScore(evaluation.weightedScore());
        caseResult.setTokensIn(resp != null ? resp.inputTokens() : null);
        caseResult.setTokensOut(resp != null ? resp.outputTokens() : null);
        caseResult.setDurationMs(durationMs);
        caseResult.setExternalTraceId(agentTraceId);
        caseResult.setLangfuseTraceId(effectiveTraceId);
        caseResult.setStatus(success ? "SUCCESS" : "FAILED");
        caseResult.setErrorMessage(success ? null : truncate(failureMessage, 1000));
        caseResultMapper.insert(caseResult);

        return success;
    }

    /**
     * 构造平台侧刚写入的 Trace 视图，供 Trace 断言立即使用；
     * 其中只包含平台边界 observation，外部 Agent 的内部 span 需从 Langfuse 远端补齐。
     */
    private static LangfuseTraceDetail buildLocalTraceDetail(
            String traceId, String sessionId, EvalRunEntity run, TargetConfig config,
            EvalDatasetSampleEntity sample, String agentOutput, AgentResponse resp,
            boolean success, long durationMs, Instant start, Instant end,
            List<String> tags, Map<String, Object> metadata, String observationId, String model) {
        Long inputTokens = resp != null ? resp.inputTokens() : null;
        Long outputTokens = resp != null ? resp.outputTokens() : null;
        Long totalTokens = (inputTokens != null || outputTokens != null)
                ? (inputTokens != null ? inputTokens : 0L) + (outputTokens != null ? outputTokens : 0L)
                : null;

        LangfuseObservation observation = new LangfuseObservation(
                observationId, traceId, null,
                model != null ? "GENERATION" : "SPAN",
                config.targetName(), model,
                sample.getInputText(), agentOutput,
                inputTokens, outputTokens, totalTokens,
                durationMs, start, end,
                success ? "DEFAULT" : "ERROR",
                success ? null : (resp != null ? resp.errorMessage() : null),
                Map.of());

        LangfuseTrace trace = new LangfuseTrace(
                traceId, "offline-" + config.targetName(),
                String.valueOf(run.getTargetId()), sessionId, null,
                sample.getInputText(), agentOutput,
                inputTokens, outputTokens, durationMs, start, end,
                tags, success, metadata);

        return new LangfuseTraceDetail(trace, List.of(observation));
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /** Run 总分 = 有评分样本的 weightedScore 平均值；没有评分器时回退到完成率。 */
    private Double averageWeightedScore(Long runId, int completed, int total) {
        List<EvalRunCaseResultEntity> caseResults = caseResultMapper.listByRun(runId);
        double sum = 0;
        int scored = 0;
        for (EvalRunCaseResultEntity result : caseResults) {
            if (result.getWeightedScore() != null) {
                sum += result.getWeightedScore();
                scored++;
            }
        }
        if (scored == 0) {
            return computeOverallScore(completed, total);
        }
        return Math.round(10.0 * sum / scored) / 10.0;
    }

    private Double computeOverallScore(int completed, int total) {
        if (total == 0) return 0.0;
        return 100.0 * completed / total;
    }

    /** 启动请求 DTO。 */
    public record StartRunRequest(
            Long spaceId,
            Long targetId,
            Long promptVersionId,
            Long datasetId,
            String evaluatorConfigs,
            Integer maxConcurrency,
            String triggerUser,
            /** 仅执行的用例 ID 列表；为空时执行评测集全部样本。 */
            List<Long> sampleIds) {}
}
