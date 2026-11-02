package com.example.platform.patrol.service;

import com.example.platform.common.CredentialCipher;
import com.example.platform.patrol.mapper.PatrolCaseMapper;
import com.example.platform.patrol.mapper.PatrolCaseStepMapper;
import com.example.platform.patrol.mapper.PatrolFindingMapper;
import com.example.platform.patrol.mapper.PatrolSceneMapper;
import com.example.platform.patrol.mapper.PatrolTaskMapper;
import com.example.platform.patrol.model.AiModelConfigEntity;
import com.example.platform.patrol.model.PatrolCaseEntity;
import com.example.platform.patrol.model.PatrolCaseStepEntity;
import com.example.platform.patrol.model.PatrolFindingEntity;
import com.example.platform.patrol.model.PatrolSceneEntity;
import com.example.platform.patrol.model.PatrolTaskEntity;
import com.example.platform.patrol.service.PatrolEventParser.CaseRun;
import com.example.platform.patrol.service.PatrolEventParser.ParseResult;
import com.example.platform.patrol.service.PatrolEventParser.Snapshot;
import com.example.platform.patrol.service.PatrolEventParser.StepRun;
import com.example.platform.patrol.service.PatrolJudgeService.FindingDraft;
import com.example.platform.patrol.service.PatrolJudgeService.JudgeResult;
import com.example.platform.patrol.service.PatrolPlannerService.PlanResult;
import com.example.platform.patrol.service.PatrolPlannerService.PlannedCase;
import com.example.platform.patrol.service.PatrolPlannerService.PlannedStep;
import com.example.platform.runner.service.RunnerCommandResult;
import com.example.platform.runner.service.RunnerExecutionService;
import com.example.platform.storage.service.ObjectStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 巡页执行编排 —— 入口页快照 → LLM 规划 → 一次性审批 → 用例执行 → 判定 → 归档。
 *
 * <p>每次执行只覆盖入口页；跳转类用例仅验证落点正确性。
 * 每条用例独立浏览器 Context（录屏），断言步骤截图，失败用例额外保留控制台与 DOM。
 */
@Service
public class PatrolExecutionService {
    private static final Logger log = LoggerFactory.getLogger(PatrolExecutionService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String INSTALL_COMMAND =
            "npm install --no-audit --no-fund --loglevel=error playwright-core@1.61.1";
    private static final String EXPLORE_COMMAND = "node patrol.js";
    private static final String EXPLORE_COMMAND_WITH_DISPLAY = "xvfb-run -a node patrol.js";
    private static final Duration INSTALL_TIMEOUT = Duration.ofSeconds(300);
    private static final long SNAPSHOT_TIMEOUT_MS = 300_000L;
    private static final int PAGE_TIMEOUT_MS = 20_000;
    private static final int ACTION_TIMEOUT_MS = 5_000;

    private final PatrolTaskMapper taskMapper;
    private final PatrolSceneMapper sceneMapper;
    private final PatrolCaseMapper caseMapper;
    private final PatrolCaseStepMapper caseStepMapper;
    private final PatrolFindingMapper findingMapper;
    private final PatrolWorkspaceService workspaceService;
    private final PatrolEventParser eventParser;
    private final PatrolPlannerService plannerService;
    private final PatrolJudgeService judgeService;
    private final PatrolAiClient aiClient;
    private final RunnerExecutionService runnerExecutionService;
    private final ObjectStorageService objectStorageService;
    private final CredentialCipher credentialCipher;
    private final Executor executor;
    private final String storageBucket;
    private final int retentionDays;

    private final Map<Long, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();

    public PatrolExecutionService(
            PatrolTaskMapper taskMapper,
            PatrolSceneMapper sceneMapper,
            PatrolCaseMapper caseMapper,
            PatrolCaseStepMapper caseStepMapper,
            PatrolFindingMapper findingMapper,
            PatrolWorkspaceService workspaceService,
            PatrolEventParser eventParser,
            PatrolPlannerService plannerService,
            PatrolJudgeService judgeService,
            PatrolAiClient aiClient,
            RunnerExecutionService runnerExecutionService,
            ObjectStorageService objectStorageService,
            CredentialCipher credentialCipher,
            @Qualifier("taskExecutionExecutor") Executor executor,
            @Value("${platform.storage.bucket}") String storageBucket,
            @Value("${platform.patrol.retention-days:90}") int retentionDays) {
        this.taskMapper = taskMapper;
        this.sceneMapper = sceneMapper;
        this.caseMapper = caseMapper;
        this.caseStepMapper = caseStepMapper;
        this.findingMapper = findingMapper;
        this.workspaceService = workspaceService;
        this.eventParser = eventParser;
        this.plannerService = plannerService;
        this.judgeService = judgeService;
        this.aiClient = aiClient;
        this.runnerExecutionService = runnerExecutionService;
        this.objectStorageService = objectStorageService;
        this.credentialCipher = credentialCipher;
        this.executor = executor;
        this.storageBucket = storageBucket;
        this.retentionDays = retentionDays <= 0 ? 90 : retentionDays;
    }

    /** 服务重启后回收中断任务，保留待审批任务以便恢复。 */
    @org.springframework.context.event.EventListener(
            org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() {
        try {
            int recovered = taskMapper.failInterruptedTasks(retentionDays);
            if (recovered > 0) {
                log.warn("Marked {} interrupted patrol tasks as FAILED after restart", recovered);
            }
        } catch (Exception exception) {
            log.warn("Recover interrupted patrol tasks failed: {}", exception.getMessage());
        }
    }

    /** 异步执行“快照 + 规划”，必要时停在审批。 */
    public void start(Long taskId) {
        cancellationFlags.put(taskId, new AtomicBoolean(false));
        executor.execute(() -> prepareAndPlan(taskId));
    }

    /** 审批通过后异步恢复执行。 */
    public void resume(Long taskId) {
        cancellationFlags.computeIfAbsent(taskId, ignored -> new AtomicBoolean(false));
        executor.execute(() -> executeAndJudge(taskId));
    }

    public void requestCancel(Long taskId) {
        cancellationFlags.computeIfAbsent(taskId, ignored -> new AtomicBoolean(false)).set(true);
        taskMapper.updateCancelRequested(taskId, true);
    }

    // ───────────────────────── 阶段 1-3：准备、快照、规划 ─────────────────────────

    private void prepareAndPlan(Long taskId) {
        Optional<PatrolTaskEntity> optional = taskMapper.findById(taskId);
        if (optional.isEmpty()) {
            cancellationFlags.remove(taskId);
            return;
        }
        PatrolTaskEntity task = optional.get();
        AtomicBoolean cancelFlag = cancellationFlags.computeIfAbsent(taskId, ignored -> new AtomicBoolean(false));
        SceneSnapshot scene = readScene(task);
        PatrolBudget budget = new PatrolBudget(task.getBudgetDurationMinutes() == null ? 15 : task.getBudgetDurationMinutes(),
                task.getBudgetLlmTokens() == null ? 300000 : task.getBudgetLlmTokens(),
                System.currentTimeMillis());

        task.setStatus("RUNNING");
        task.setCurrentStage("PREPARING");
        task.setStartedAt(LocalDateTime.now());
        task.setCancelRequested(false);
        taskMapper.update(task);

        try {
            Map<String, String> env = runnerEnv();
            Path workspace = workspaceService.prepareWorkspace(taskId,
                    buildConfig(task, scene, "SNAPSHOT", budget, false, false, List.of()));

            RunnerCommandResult install = runnerExecutionService.runStage(
                    workspaceService.getWorkspaceRoot(), workspace, "PREPARING", INSTALL_COMMAND,
                    env, INSTALL_TIMEOUT, cancelFlag::get);
            if (finishIfCanceled(task, install, "安装阶段已取消")) {
                return;
            }
            if (install.timedOut()) {
                finish(task, "FAILED", "安装依赖超时");
                return;
            }
            if (install.exitCode() != 0) {
                finish(task, "FAILED", "依赖安装失败，退出码=" + install.exitCode());
                return;
            }

            task.setCurrentStage("SNAPSHOTTING");
            taskMapper.update(task);
            long snapshotMs = Math.max(30_000L, Math.min(SNAPSHOT_TIMEOUT_MS, budget.getRemainingDurationMs()));
            workspaceService.writeConfig(taskId,
                    buildConfig(task, scene, "SNAPSHOT", budget, false, false, List.of()));
            RunnerCommandResult snapshotRun = runnerExecutionService.runStage(
                    workspaceService.getWorkspaceRoot(), workspace, "SNAPSHOTTING", command(scene),
                    env, Duration.ofMillis(snapshotMs), cancelFlag::get);
            workspaceService.removeConfig(taskId);

            Map<String, String> uploaded = uploadArtifacts(task.getSpaceId(), taskId, workspace);
            ParseResult parsed = eventParser.parse(workspace.resolve("events.jsonl"));
            if (finishIfCanceled(task, snapshotRun, "快照阶段已取消")) {
                return;
            }
            if (snapshotRun.timedOut()) {
                finish(task, "FAILED", "入口页快照超时");
                return;
            }
            if (parsed.snapshot() == null) {
                finish(task, "FAILED", "未能获取入口页快照"
                        + (snapshotRun.exitCode() != 0 ? "，退出码=" + snapshotRun.exitCode() : ""));
                return;
            }
            Snapshot snapshot = parsed.snapshot();
            task.setSnapshotJson(snapshotJson(snapshot));
            task.setSnapshotScreenshotKey(mapKey(uploaded, snapshot.screenshot()));
            taskMapper.update(task);

            // ── 规划 ──
            task.setCurrentStage("PLANNING");
            taskMapper.update(task);
            JsonNode snapshotNode = MAPPER.readTree(snapshotJson(snapshot));
            AiModelConfigEntity planModel = modelFromSnapshot(task, "plan");
            PlanResult plan = plannerService.plan(task.getId(), task.getTaskBrief(), snapshotNode, budget, planModel,
                    scene.visibleTextLimit());
            plan = supplementCoverage(task, snapshotNode, plan, budget, planModel);
            String coverage = PatrolCoverage.coverageText(snapshotNode, coverageKeysOf(plan.cases()));
            persistCases(task, plan, snapshot);
            task.setLlmTokenUsed(budget.getTokensUsed());
            task.setDegraded(budget.isDegraded() || plan.degraded());
            refreshStats(task);
            String stops = budget.stopReasonText();
            String planningNote = stops.isBlank() ? coverage : stops + "；" + coverage;
            task.setPlanningNote(trimMessage(planningNote));
            task.setResultMessage(trimMessage("用例规划完成，共 " + plan.cases().size() + " 条；" + planningNote));
            taskMapper.update(task);

            List<PatrolCaseEntity> cases = caseMapper.findByTaskId(taskId);
            boolean needsApproval = cases.stream()
                    .anyMatch(item -> !"READ".equals(item.getWorstRisk()));
            if (!needsApproval || autoApproved(scene, cases)) {
                task.setApprovalJson(autoApprovalJson(cases, scene));
                task.setApprovedBy("AUTO");
                task.setApprovedAt(LocalDateTime.now());
                task.setResultMessage("未命中写操作，直接执行");
                taskMapper.update(task);
                executeAndJudge(taskId);
                return;
            }

            task.setStatus("WAITING_APPROVAL");
            task.setCurrentStage("WAITING_APPROVAL");
            task.setResultMessage("等待执行前审批：请确认写操作/破坏性操作用例");
            taskMapper.update(task);
        } catch (Exception exception) {
            log.error("Patrol prepare/plan failed. taskId={}", taskId, exception);
            safeFinish(task, "FAILED", "巡页准备或规划失败: " + exception.getMessage());
        } finally {
            if (!"WAITING_APPROVAL".equals(task.getStatus())) {
                workspaceService.cleanupWorkspace(taskId);
            }
        }
    }

    // ───────────────────────── 阶段 4-7：执行、判定、归档 ─────────────────────────

    private void executeAndJudge(Long taskId) {
        Optional<PatrolTaskEntity> optional = taskMapper.findById(taskId);
        if (optional.isEmpty()) {
            return;
        }
        PatrolTaskEntity task = optional.get();
        if (!"RUNNING".equals(task.getStatus()) && !"WAITING_APPROVAL".equals(task.getStatus())) {
            return;
        }
        AtomicBoolean cancelFlag = cancellationFlags.computeIfAbsent(taskId, ignored -> new AtomicBoolean(false));
        SceneSnapshot scene = readScene(task);
        Approval approval = readApproval(task);
        PatrolBudget budget = new PatrolBudget(task.getBudgetDurationMinutes() == null ? 15 : task.getBudgetDurationMinutes(),
                task.getBudgetLlmTokens() == null ? 300000 : task.getBudgetLlmTokens(),
                System.currentTimeMillis());
        budget.consumeTokens(task.getLlmTokenUsed() == null ? 0 : task.getLlmTokenUsed());

        task.setStatus("RUNNING");
        task.setCurrentStage("EXECUTING");
        taskMapper.update(task);

        try {
            List<PatrolCaseEntity> cases = caseMapper.findByTaskId(taskId);
            if (cases.isEmpty()) {
                finish(task, "COMPLETED", "未规划出可执行用例");
                return;
            }
            Path workspace = workspaceService.prepareWorkspace(taskId,
                    buildConfig(task, scene, "EXECUTE", budget,
                            approval.allowWrite, approval.allowDestructive, approval.caseIds));
            workspaceService.writeCases(taskId, casePayload(cases));

            long executeMs = Math.max(30_000L, budget.getRemainingDurationMs());
            RunnerCommandResult execution = runnerExecutionService.runStage(
                    workspaceService.getWorkspaceRoot(), workspace, "EXECUTING", command(scene),
                    runnerEnv(), Duration.ofMillis(executeMs), cancelFlag::get);
            workspaceService.removeConfig(taskId);

            Map<String, String> uploaded = uploadArtifacts(task.getSpaceId(), taskId, workspace);
            ParseResult parsed = eventParser.parse(workspace.resolve("events.jsonl"));
            applyExecution(task, cases, parsed, uploaded);
            if (finishIfCanceled(task, execution, "执行阶段已取消")) {
                return;
            }
            if (execution.timedOut()) {
                budget.addStopReason("执行阶段达到时间上限，已归档已完成用例");
            } else if (execution.exitCode() != 0) {
                budget.addStopReason("执行器异常退出，退出码=" + execution.exitCode());
            }

            // ── 判定 ──
            task.setCurrentStage("JUDGING");
            taskMapper.update(task);
            AiModelConfigEntity judgeModel = modelFromSnapshot(task, "judge");
            judgeCases(task, cases, parsed, uploaded, judgeModel, budget, scene.visibleTextLimit());
            task.setLlmTokenUsed(budget.getTokensUsed());
            task.setDegraded(task.getDegraded() || budget.isDegraded());

            String status = execution.exitCode() != 0 && !execution.timedOut() ? "FAILED" : "COMPLETED";
            String message = buildMessage(status, budget, task.getPlanningNote());
            finish(task, status, message);
        } catch (Exception exception) {
            log.error("Patrol execute/judge failed. taskId={}", taskId, exception);
            safeFinish(task, "FAILED", "巡页执行失败: " + exception.getMessage());
        } finally {
            cancellationFlags.remove(taskId);
            workspaceService.removeConfig(taskId);
            workspaceService.cleanupWorkspace(taskId);
        }
    }

    private void applyExecution(PatrolTaskEntity task, List<PatrolCaseEntity> cases,
                                ParseResult parsed, Map<String, String> uploaded) {
        Set<Long> caseIds = cases.stream().map(PatrolCaseEntity::getId).collect(Collectors.toSet());
        for (StepRun step : parsed.steps()) {
            if (!caseIds.contains(step.caseId())) {
                continue;
            }
            caseStepMapper.updateResult(step.caseId(), step.seq(),
                    step.status() == null || step.status().isBlank() ? "FAILED" : step.status(),
                    step.message(), mapKey(uploaded, step.screenshot()),
                    step.requestJson(), step.approved(), step.durationMs());
        }
        Map<Long, CaseRun> runs = parsed.cases().stream()
                .collect(Collectors.toMap(CaseRun::caseId, item -> item, (first, second) -> first));
        for (PatrolCaseEntity caseEntity : cases) {
            CaseRun run = runs.get(caseEntity.getId());
            if (run == null) {
                caseMapper.updateResult(caseEntity.getId(), "SKIP", "SKIPPED",
                        "未执行（任务提前结束）", null, null, false);
                continue;
            }
            String execStatus = run.status() == null || run.status().isBlank() ? "FAILED" : run.status();
            caseMapper.updateResult(caseEntity.getId(), "PENDING", execStatus,
                    run.message(), mapKey(uploaded, run.screenshot()), run.durationMs(), false);
            caseMapper.updateEvidence(caseEntity.getId(),
                    mapKey(uploaded, run.video()), mapKey(uploaded, run.consoleKey()),
                    mapKey(uploaded, run.domKey()), mapKey(uploaded, run.screenshot()));
        }
        if (parsed.runStatus() != null && parsed.runStatus().equalsIgnoreCase("FAILED")) {
            log.warn("Patrol runner reported FAILED. taskId={}, reason={}", task.getId(), parsed.runReason());
        }
    }

    private void judgeCases(PatrolTaskEntity task, List<PatrolCaseEntity> cases, ParseResult parsed,
                            Map<String, String> uploaded, AiModelConfigEntity judgeModel,
                            PatrolBudget budget, int visibleTextLimit) {
        Map<Long, CaseRun> runs = parsed.cases().stream()
                .collect(Collectors.toMap(CaseRun::caseId, item -> item, (first, second) -> first));
        Snapshot snapshot = parsed.snapshot();
        for (PatrolCaseEntity caseEntity : cases) {
            List<PatrolCaseStepEntity> steps = caseStepMapper.findByCaseId(caseEntity.getId());
            CaseRun run = runs.get(caseEntity.getId());
            String screenshot = run == null ? task.getSnapshotScreenshotKey() : mapKey(uploaded, run.screenshot());
            if (screenshot == null) {
                screenshot = task.getSnapshotScreenshotKey();
            }
            JudgeResult judged = judgeService.judge(caseEntity, steps, run, snapshot, screenshot,
                    judgeModel, budget, visibleTextLimit);
            String execStatus = run == null
                    ? "PENDING"
                    : (run.status() == null || run.status().isBlank() ? "FAILED" : run.status());
            caseMapper.updateResult(caseEntity.getId(), judged.status(), execStatus,
                    judged.reason(), screenshot,
                    run == null ? null : run.durationMs(), judged.degraded());
            if (judged.tokens() > 0 || judged.modelId() != null) {
                caseMapper.updateModelUsage(caseEntity.getId(), judged.modelId(), judged.tokens());
            }
            for (FindingDraft draft : judged.findings()) {
                insertFinding(task.getId(), caseEntity.getId(), draft);
            }
        }
    }

    private void insertFinding(Long taskId, Long caseId, FindingDraft draft) {
        PatrolFindingEntity entity = new PatrolFindingEntity();
        entity.setTaskId(taskId);
        entity.setCaseId(caseId);
        entity.setStepSeq(draft.stepSeq());
        entity.setType(draft.type());
        entity.setSeverity(draft.severity());
        entity.setTitle(draft.title());
        entity.setDescription(draft.description());
        entity.setEvidenceJson(draft.reproSteps());
        entity.setStatus("OPEN");
        entity.setDegraded(draft.degraded());
        entity.setCreatedAt(LocalDateTime.now());
        findingMapper.insert(entity);
    }

    // ───────────────────────── 覆盖率补全 ─────────────────────────

    /**
     * 入口页元素覆盖率补全：初始规划未引用到的可交互元素，追加一次 LLM 规划。
     * 只受 Token 与时长预算约束。
     */
    private PlanResult supplementCoverage(PatrolTaskEntity task, JsonNode snapshotNode, PlanResult plan,
                                          PatrolBudget budget, AiModelConfigEntity planModel) {
        if (!"LLM".equals(plan.source())) {
            return plan;
        }
        Set<String> keys = coverageKeysOf(plan.cases());
        List<JsonNode> uncovered = PatrolCoverage.uncoveredElements(snapshotNode, keys);
        if (uncovered.isEmpty() || budget.isDurationExhausted()) {
            return plan;
        }
        List<String> existingNames = plan.cases().stream().map(PlannedCase::name).toList();
        PlanResult extra = plannerService.planSupplement(task.getId(), task.getTaskBrief(), snapshotNode, uncovered,
                existingNames, budget, planModel);
        if (extra.cases().isEmpty()) {
            if (extra.degraded()) {
                budget.markDegraded();
            }
            return plan;
        }
        List<PlannedCase> merged = new ArrayList<>(plan.cases());
        merged.addAll(extra.cases());
        log.info("Patrol coverage supplement added. taskId={}, extraCases={}, totalCases={}, uncoveredBefore={}",
                task.getId(), extra.cases().size(), merged.size(), uncovered.size());
        return new PlanResult(merged, plan.source(), plan.tokens() + extra.tokens(),
                plan.modelId() == null ? extra.modelId() : plan.modelId(),
                plan.degraded() || extra.degraded(), plan.reason());
    }

    private static Set<String> coverageKeysOf(List<PlannedCase> cases) {
        Set<String> keys = new LinkedHashSet<>();
        for (PlannedCase item : cases) {
            for (PlannedStep step : item.steps()) {
                String key = PatrolCoverage.keyOf(step.selector(), step.frame());
                if (!key.isEmpty()) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    // ───────────────────────── 持久化与统计 ─────────────────────────

    private void persistCases(PatrolTaskEntity task, PlanResult plan, Snapshot snapshot) {
        int seq = 1;
        for (PlannedCase planned : plan.cases()) {
            PatrolCaseEntity caseEntity = new PatrolCaseEntity();
            caseEntity.setTaskId(task.getId());
            caseEntity.setSeq(seq);
            caseEntity.setName(planned.name());
            caseEntity.setIntent(planned.intent());
            caseEntity.setExpected(planned.expected());
            caseEntity.setPriority(planned.priority());
            caseEntity.setWorstRisk(planned.worstRisk());
            caseEntity.setStatus("PENDING");
            caseEntity.setExecStatus("PENDING");
            caseEntity.setSource(plan.source());
            caseEntity.setDegraded(plan.degraded());
            caseEntity.setTokens(0);
            caseEntity.setCreatedAt(LocalDateTime.now());
            caseMapper.insert(caseEntity);

            int stepSeq = 1;
            for (PlannedStep step : planned.steps()) {
                PatrolCaseStepEntity stepEntity = new PatrolCaseStepEntity();
                stepEntity.setCaseId(caseEntity.getId());
                stepEntity.setSeq(stepSeq);
                stepEntity.setAction(step.action());
                stepEntity.setTargetSelector(step.selector());
                stepEntity.setLocatorJson(locatorJson(step));
                stepEntity.setTargetLabel(step.label());
                stepEntity.setInputValue(step.value());
                stepEntity.setOperation(step.operation());
                stepEntity.setRisk(step.risk());
                stepEntity.setApproved(false);
                stepEntity.setStatus("PENDING");
                stepEntity.setCreatedAt(LocalDateTime.now());
                caseStepMapper.insert(stepEntity);
                stepSeq += 1;
            }
            seq += 1;
        }
        log.info("Patrol cases persisted. taskId={}, cases={}, source={}, degraded={}",
                task.getId(), plan.cases().size(), plan.source(), plan.degraded());
    }

    private List<Map<String, Object>> casePayload(List<PatrolCaseEntity> cases) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (PatrolCaseEntity caseEntity : cases) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("caseId", caseEntity.getId());
            item.put("name", caseEntity.getName());
            item.put("worstRisk", caseEntity.getWorstRisk());
            List<Map<String, Object>> steps = new ArrayList<>();
            for (PatrolCaseStepEntity step : caseStepMapper.findByCaseId(caseEntity.getId())) {
                Map<String, Object> stepItem = new LinkedHashMap<>();
                stepItem.put("seq", step.getSeq());
                stepItem.put("action", step.getAction());
                stepItem.put("selector", step.getTargetSelector());
                stepItem.put("label", step.getTargetLabel());
                stepItem.put("value", step.getInputValue());
                stepItem.put("operation", step.getOperation());
                stepItem.put("risk", step.getRisk());
                appendLocatorFields(stepItem, step.getLocatorJson());
                steps.add(stepItem);
            }
            item.put("steps", steps);
            payload.add(item);
        }
        return payload;
    }

    /** 步骤的附加定位信息（iframe 链、拖拽目标、断言属性）序列化为 locator_json。 */
    private static String locatorJson(PlannedStep step) {
        Map<String, Object> locator = new LinkedHashMap<>();
        if (step.frame() != null && !step.frame().isEmpty()) {
            locator.put("frame", step.frame());
        }
        if (step.target() != null && !step.target().isBlank()) {
            locator.put("target", step.target());
        }
        if (step.attribute() != null && !step.attribute().isBlank()) {
            locator.put("attribute", step.attribute());
        }
        if (locator.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(locator);
        } catch (IOException exception) {
            return null;
        }
    }

    private static void appendLocatorFields(Map<String, Object> stepItem, String locatorJson) {
        if (locatorJson == null || locatorJson.isBlank()) {
            return;
        }
        try {
            JsonNode locator = MAPPER.readTree(locatorJson);
            JsonNode frame = locator.path("frame");
            if (frame.isArray() && frame.size() > 0) {
                stepItem.put("frame", MAPPER.convertValue(frame, List.class));
            }
            if (locator.hasNonNull("target")) {
                stepItem.put("target", locator.path("target").asText());
            }
            if (locator.hasNonNull("attribute")) {
                stepItem.put("attribute", locator.path("attribute").asText());
            }
        } catch (IOException ignored) {
            // locator_json 解析失败时按无附加定位处理。
        }
    }

    private void finish(PatrolTaskEntity task, String status, String message) {
        refreshStats(task);
        task.setStatus(status);
        task.setCurrentStage("FINISHED");
        task.setResultMessage(trimMessage(message));
        task.setFinishedAt(LocalDateTime.now());
        task.setDurationMs(task.getStartedAt() == null ? 0L
                : Duration.between(task.getStartedAt(), LocalDateTime.now()).toMillis());
        taskMapper.update(task);
        taskMapper.finish(task.getId(), status, trimMessage(message), retentionDays);
        sceneMapper.updateLastTask(task.getSceneId(), LocalDateTime.now(), task.getId(), status);
    }

    private void safeFinish(PatrolTaskEntity task, String status, String message) {
        try {
            finish(task, status, message);
        } catch (Exception exception) {
            log.warn("Patrol finish failed. taskId={}, reason={}", task.getId(), exception.getMessage());
        }
    }

    private void refreshStats(PatrolTaskEntity task) {
        List<PatrolCaseEntity> cases = caseMapper.findByTaskId(task.getId());
        List<PatrolFindingEntity> findings = findingMapper.findByTaskId(task.getId());
        int pass = 0;
        int fail = 0;
        int skip = 0;
        for (PatrolCaseEntity item : cases) {
            switch (item.getStatus() == null ? "" : item.getStatus()) {
                case "PASS" -> pass += 1;
                case "FAIL" -> fail += 1;
                case "SKIP" -> skip += 1;
                default -> {
                }
            }
        }
        int p0 = 0;
        int p1 = 0;
        int p2 = 0;
        boolean degraded = Boolean.TRUE.equals(task.getDegraded());
        for (PatrolFindingEntity finding : findings) {
            switch (finding.getSeverity() == null ? "" : finding.getSeverity()) {
                case "P0" -> p0 += 1;
                case "P1" -> p1 += 1;
                default -> p2 += 1;
            }
            degraded = degraded || Boolean.TRUE.equals(finding.getDegraded());
        }
        task.setCaseCount(cases.size());
        task.setCasePassCount(pass);
        task.setCaseFailCount(fail);
        task.setCaseSkipCount(skip);
        task.setFindingP0Count(p0);
        task.setFindingP1Count(p1);
        task.setFindingP2Count(p2);
        task.setDegraded(degraded || cases.stream().anyMatch(item -> Boolean.TRUE.equals(item.getDegraded())));
    }

    private boolean finishIfCanceled(PatrolTaskEntity task, RunnerCommandResult result, String message) {
        if (task.getCancelRequested() != null && task.getCancelRequested() || result != null && result.canceled()) {
            finish(task, "CANCELED", message);
            return true;
        }
        return false;
    }

    private String buildMessage(String status, PatrolBudget budget, String planningNote) {
        StringBuilder builder = new StringBuilder(status.equals("FAILED") ? "巡页执行失败，已归档已完成用例" : "巡页完成");
        if (planningNote != null && !planningNote.isBlank()) {
            builder.append("；").append(planningNote);
        }
        String stops = budget.stopReasonText();
        if (!stops.isBlank()) {
            builder.append("；").append(stops);
        }
        if (budget.isDegraded()) {
            builder.append("；报告已标记降级");
        }
        return builder.toString();
    }

    // ───────────────────────── Runner 与证据 ─────────────────────────

    private Map<String, String> uploadArtifacts(Long spaceId, Long taskId, Path workspace) {
        Map<String, String> uploaded = new LinkedHashMap<>();
        for (String sub : List.of("screenshots", "videos", "evidence")) {
            Path dir = workspace.resolve(sub);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (var paths = Files.walk(dir)) {
                paths.filter(Files::isRegularFile).forEach(file -> {
                    String name = dir.relativize(file).toString().replace('\\', '/');
                    String relative = sub + "/" + name;
                    String key = "patrol/" + spaceId + "/" + taskId + "/" + relative;
                    try {
                        objectStorageService.uploadFile(storageBucket, key, file);
                        uploaded.put(relative, key);
                    } catch (Exception exception) {
                        log.warn("Upload patrol artifact failed. taskId={}, file={}, reason={}",
                                taskId, relative, exception.getMessage());
                    }
                });
            } catch (IOException exception) {
                log.warn("Scan patrol artifacts failed. taskId={}, reason={}", taskId, exception.getMessage());
            }
        }
        return uploaded;
    }

    private static String mapKey(Map<String, String> uploaded, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (uploaded.containsKey(normalized)) {
            return uploaded.get(normalized);
        }
        for (String prefix : List.of("screenshots/", "videos/", "evidence/")) {
            if (uploaded.containsKey(prefix + normalized)) {
                return uploaded.get(prefix + normalized);
            }
        }
        return null;
    }

    private Map<String, Object> buildConfig(PatrolTaskEntity task, SceneSnapshot scene, String mode,
                                            PatrolBudget budget, boolean allowWrite, boolean allowDestructive,
                                            List<Long> approvedCaseIds) {
        Map<String, String> localStorage = Map.of();
        String cookie = null;
        String token = null;
        if ("COOKIE".equalsIgnoreCase(scene.authType())) {
            cookie = credentialCipher.decrypt(scene.authCookieEncrypted());
        } else if ("TOKEN".equalsIgnoreCase(scene.authType())) {
            token = credentialCipher.decrypt(scene.authTokenEncrypted());
        } else if ("LOCAL_STORAGE".equalsIgnoreCase(scene.authType())) {
            localStorage = parseLocalStorage(credentialCipher.decrypt(scene.authLocalStorageEncrypted()));
        }
        return PatrolWorkspaceService.config(
                task.getStartUrl(), scene.browser(), scene.headless(), mode,
                scene.authType(), cookie, token, scene.authTokenHeader(), localStorage,
                scene.scrollScan(), scene.recordVideo(), allowWrite, allowDestructive,
                approvedCaseIds, PAGE_TIMEOUT_MS, ACTION_TIMEOUT_MS,
                scene.visibleTextLimit(), budget.getRemainingDurationMs(), "");
    }

    private String command(SceneSnapshot scene) {
        return scene.headless() ? EXPLORE_COMMAND : EXPLORE_COMMAND_WITH_DISPLAY;
    }

    private Map<String, String> runnerEnv() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("PLAYWRIGHT_BROWSERS_PATH", "/ms-playwright");
        env.put("HOME", "/tmp");
        env.put("npm_config_cache", "/tmp/npm-cache");
        env.put("PATROL_CONFIG", "config.json");
        env.put("PATROL_EVENTS", "events.jsonl");
        env.put("PATROL_SCREENSHOT_DIR", "screenshots");
        env.put("PATROL_VIDEO_DIR", "videos");
        env.put("PATROL_EVIDENCE_DIR", "evidence");
        env.put("PATROL_CASES", "cases.json");
        return env;
    }

    // ───────────────────────── 快照模型与审批 ─────────────────────────

    private SceneSnapshot readScene(PatrolTaskEntity task) {
        try {
            JsonNode root = MAPPER.readTree(task.getSceneSnapshotJson());
            return new SceneSnapshot(
                    root.path("startUrl").asText(task.getStartUrl()),
                    root.path("browser").asText("chromium"),
                    root.path("headless").asBoolean(true),
                    root.path("authType").asText("NONE"),
                    root.path("authCookieEncrypted").asText(null),
                    root.path("authTokenEncrypted").asText(null),
                    root.path("authTokenHeader").asText("Authorization"),
                    root.path("authLocalStorageEncrypted").asText(null),
                    root.path("visibleTextLimit").asInt(1800),
                    root.path("scrollScan").asBoolean(true),
                    root.path("recordVideo").asBoolean(true),
                    root.path("autoApproveWrite").asBoolean(false),
                    root.path("autoApproveDestructive").asBoolean(false));
        } catch (Exception exception) {
            throw new IllegalStateException("任务场景快照解析失败: " + exception.getMessage(), exception);
        }
    }

    private Approval readApproval(PatrolTaskEntity task) {
        if (task.getApprovalJson() == null || task.getApprovalJson().isBlank()) {
            return new Approval(false, false, List.of());
        }
        try {
            JsonNode root = MAPPER.readTree(task.getApprovalJson());
            List<Long> caseIds = new ArrayList<>();
            if (root.path("caseIds").isArray()) {
                root.path("caseIds").forEach(node -> caseIds.add(node.asLong()));
            }
            return new Approval(root.path("allowWrite").asBoolean(false),
                    root.path("allowDestructive").asBoolean(false), caseIds);
        } catch (Exception exception) {
            return new Approval(false, false, List.of());
        }
    }

    private boolean autoApproved(SceneSnapshot scene, List<PatrolCaseEntity> cases) {
        for (PatrolCaseEntity caseEntity : cases) {
            String risk = caseEntity.getWorstRisk();
            if ("DESTRUCTIVE".equals(risk) && !scene.autoApproveDestructive()) {
                return false;
            }
            if (!"READ".equals(risk) && !"DESTRUCTIVE".equals(risk) && !scene.autoApproveWrite()) {
                return false;
            }
        }
        return true;
    }

    /** 自动审批仅覆盖场景显式开启的类别，破坏性不会跟随写授权自动放行。 */
    private String autoApprovalJson(List<PatrolCaseEntity> cases, SceneSnapshot scene) {
        boolean hasDestructive = cases.stream().anyMatch(item -> "DESTRUCTIVE".equals(item.getWorstRisk()));
        boolean hasWrite = cases.stream().anyMatch(item -> !"READ".equals(item.getWorstRisk()));
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("source", "AUTO");
        approval.put("allowWrite", hasWrite && scene.autoApproveWrite());
        approval.put("allowDestructive", hasDestructive && scene.autoApproveDestructive());
        approval.put("caseIds", List.of());
        return toJson(approval);
    }

    private AiModelConfigEntity modelFromSnapshot(PatrolTaskEntity task, String key) {
        if (task.getModelSnapshotJson() == null || task.getModelSnapshotJson().isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(task.getModelSnapshotJson()).path(key);
            if (!root.isObject()) {
                return null;
            }
            AiModelConfigEntity entity = new AiModelConfigEntity();
            entity.setName(key + "-snapshot");
            entity.setProviderType("OPENAI_COMPATIBLE");
            entity.setBaseUrl(root.path("baseUrl").asText(""));
            entity.setApiKeyEncrypted(root.path("apiKeyEncrypted").asText(""));
            entity.setModelId(root.path("modelId").asText(""));
            entity.setSupportsVision(root.path("vision").asBoolean(false));
            entity.setMaxTokens(root.path("maxTokens").asInt(4096));
            entity.setTemperature(new java.math.BigDecimal(root.path("temperature").asText("0.2")));
            entity.setEnabled(true);
            if (entity.getBaseUrl().isBlank() || entity.getApiKeyEncrypted().isBlank()
                    || entity.getModelId().isBlank()) {
                return null;
            }
            return entity;
        } catch (Exception exception) {
            log.warn("Model snapshot parse failed. taskId={}, key={}", task.getId(), key);
            return null;
        }
    }

    public static String sceneSnapshotJson(PatrolSceneEntity scene) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("startUrl", scene.getStartUrl());
        snapshot.put("browser", scene.getBrowser());
        snapshot.put("headless", !Boolean.FALSE.equals(scene.getHeadless()));
        snapshot.put("authType", scene.getAuthType());
        snapshot.put("authCookieEncrypted", scene.getAuthCookieEncrypted());
        snapshot.put("authTokenEncrypted", scene.getAuthTokenEncrypted());
        snapshot.put("authTokenHeader", scene.getAuthTokenHeader());
        snapshot.put("authLocalStorageEncrypted", scene.getAuthLocalStorageEncrypted());
        snapshot.put("planModelId", scene.getPlanModelId());
        snapshot.put("judgeModelId", scene.getJudgeModelId());
        snapshot.put("taskBrief", scene.getTaskBrief());
        snapshot.put("budgetDurationMinutes", scene.getBudgetDurationMinutes());
        snapshot.put("budgetLlmTokens", scene.getBudgetLlmTokens());
        snapshot.put("budgetCases", scene.getBudgetCases());
        snapshot.put("visibleTextLimit", scene.getVisibleTextLimit() == null ? 1800 : scene.getVisibleTextLimit());
        snapshot.put("scrollScan", !Boolean.FALSE.equals(scene.getScrollScan()));
        snapshot.put("recordVideo", !Boolean.FALSE.equals(scene.getRecordVideo()));
        snapshot.put("autoApproveWrite", Boolean.TRUE.equals(scene.getAutoApproveWrite()));
        snapshot.put("autoApproveDestructive", Boolean.TRUE.equals(scene.getAutoApproveDestructive()));
        return toJson(snapshot);
    }

    public static String modelSnapshotJson(AiModelConfigEntity plan, AiModelConfigEntity judge) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("plan", modelNode(plan));
        snapshot.put("judge", modelNode(judge));
        return toJson(snapshot);
    }

    private static Map<String, Object> modelNode(AiModelConfigEntity entity) {
        if (entity == null) {
            return Map.of();
        }
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("baseUrl", entity.getBaseUrl());
        node.put("apiKeyEncrypted", entity.getApiKeyEncrypted());
        node.put("modelId", entity.getModelId());
        node.put("vision", Boolean.TRUE.equals(entity.getSupportsVision()));
        node.put("maxTokens", entity.getMaxTokens());
        node.put("temperature", entity.getTemperature() == null ? "0.2" : entity.getTemperature().toPlainString());
        node.put("name", entity.getName());
        return node;
    }

    private static String snapshotJson(Snapshot snapshot) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("url", snapshot.url());
        node.put("title", snapshot.title());
        node.put("visibleText", snapshot.visibleText());
        node.put("consoleErrors", snapshot.consoleErrors());
        try {
            node.put("elements", MAPPER.readTree(snapshot.elementsJson() == null ? "[]" : snapshot.elementsJson()));
        } catch (Exception exception) {
            node.put("elements", List.of());
        }
        return toJson(node);
    }

    private static Map<String, String> parseLocalStorage(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (IOException exception) {
            return Map.of();
        }
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("JSON 序列化失败: " + exception.getMessage(), exception);
        }
    }

    private static String trimMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }

    private static List<String> splitKeywords(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
    }

    private record Approval(boolean allowWrite, boolean allowDestructive, List<Long> caseIds) {
    }

    private record SceneSnapshot(String startUrl, String browser, boolean headless, String authType,
                                 String authCookieEncrypted, String authTokenEncrypted,
                                 String authTokenHeader, String authLocalStorageEncrypted,
                                 int visibleTextLimit, boolean scrollScan, boolean recordVideo,
                                 boolean autoApproveWrite, boolean autoApproveDestructive) {
    }
}
