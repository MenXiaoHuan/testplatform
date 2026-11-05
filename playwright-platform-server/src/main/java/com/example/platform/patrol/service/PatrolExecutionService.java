package com.example.platform.patrol.service;

import com.example.platform.common.CredentialCipher;
import com.example.platform.patrol.dto.PatrolSceneSupplementDraft;
import com.example.platform.patrol.dto.PatrolSceneSupplementDraft.ResolvedStep;
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
import com.example.platform.patrol.model.PatrolTaskStatus;
import com.example.platform.patrol.service.PatrolEventParser.CaseRun;
import com.example.platform.patrol.service.PatrolEventParser.ParseResult;
import com.example.platform.patrol.service.PatrolEventParser.Snapshot;
import com.example.platform.patrol.service.PatrolEventParser.StepRun;
import com.example.platform.patrol.service.PatrolJudgeService.FindingDraft;
import com.example.platform.patrol.service.PatrolJudgeService.JudgeResult;
import com.example.platform.patrol.service.PatrolPlannerService.PlanResult;
import com.example.platform.patrol.service.PatrolSupplementResolver.ResolveResult;
import com.example.platform.patrol.service.PatrolPlannerService.PlannedCase;
import com.example.platform.patrol.service.PatrolPlannerService.PlannedStep;
import com.example.platform.runner.service.RunnerCommandResult;
import com.example.platform.runner.service.RunnerExecutionService;
import com.example.platform.storage.service.ObjectStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
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
    /** 任务超过「时长预算 + 宽限期」仍无线程存活时，判定为脏 RUNNING 并收尾。 */
    private static final Duration STALE_GRACE = Duration.ofMinutes(10);
    /** 前置动作中会改变页面状态的动作；未命中写/危险关键字时按 WRITE 处理。 */
    private static final Set<String> PRECONDITION_MUTATING_ACTIONS = Set.of(
            "fill", "type", "clear", "press");
    /** 前置文案中的破坏性关键字：命中即需「自动批准危险操作」或人工审批。 */
    private static final List<String> DESTRUCTIVE_KEYWORDS = List.of(
            "删除", "清空", "移除", "注销", "作废", "撤销", "重置", "解绑", "禁用", "冻结",
            "支付", "付款", "退款", "转账", "退出登录", "取消订单");
    /** 前置文案中的写操作关键字：命中即需「自动批准写操作」或人工审批。 */
    private static final List<String> WRITE_KEYWORDS = List.of(
            "保存", "提交", "新增", "新建", "创建", "编辑", "修改", "更新", "导入", "上传",
            "发送", "确认", "应用", "同意", "启用", "绑定", "登录", "注册", "授权", "移入",
            "复制", "设置", "分配", "添加");

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
    private final PatrolSupplementResolver supplementResolver;
    private final RunnerExecutionService runnerExecutionService;
    private final ObjectStorageService objectStorageService;
    private final CredentialCipher credentialCipher;
    private final Executor executor;
    private final String storageBucket;
    private final int retentionDays;

    private final Map<Long, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();
    /** 当前 JVM 内真正有执行线程的任务；为空说明是重启/线程丢失后的孤儿任务。 */
    private final Set<Long> runningTaskIds = ConcurrentHashMap.newKeySet();
    /** 主任务把自身结果暂存，等补充入口全部结束后再统一收尾。 */
    private final Map<Long, PendingFinish> pendingParentFinishes = new ConcurrentHashMap<>();

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
            PatrolSupplementResolver supplementResolver,
            RunnerExecutionService runnerExecutionService,
            ObjectStorageService objectStorageService,
            CredentialCipher credentialCipher,
            @Qualifier("patrolExecutionExecutor") Executor executor,
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
        this.supplementResolver = supplementResolver;
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
            // 0 也要打日志：之前只在 >0 时输出，导致回收失效时完全静默。
            log.info("Patrol startup recovery finished, interrupted tasks marked FAILED={}", recovered);
        } catch (Exception exception) {
            log.warn("Recover interrupted patrol tasks failed: {}", exception.getMessage());
        }
    }

    /** 异步执行“快照 + 规划”，必要时停在审批。 */
    public void start(Long taskId) {
        cancellationFlags.put(taskId, new AtomicBoolean(false));
        submit(taskId, () -> prepareAndPlan(taskId));
    }

    /** 审批通过后异步恢复执行；队列满时保持待审批状态，由调用方提示重试。 */
    public void resume(Long taskId) {
        cancellationFlags.computeIfAbsent(taskId, ignored -> new AtomicBoolean(false));
        submit(taskId, () -> executeAndJudge(taskId));
    }

    private void submit(Long taskId, Runnable body) {
        runningTaskIds.add(taskId);
        try {
            executor.execute(() -> {
                try {
                    body.run();
                } finally {
                    runningTaskIds.remove(taskId);
                }
            });
        } catch (RejectedExecutionException exception) {
            runningTaskIds.remove(taskId);
            cancellationFlags.remove(taskId);
            log.error("Patrol task rejected by executor. taskId={}", taskId);
            if (taskMapper.findById(taskId).isPresent()) {
                taskMapper.finish(taskId, "FAILED", "巡页执行队列已满，请稍后再试", retentionDays);
            }
            throw exception;
        }
    }

    /**
     * 取消任务：有活跃线程时先协作停止（阶段边界/子进程检查取消标记）；
     * 没有活跃线程（服务重启、线程已退出）时直接落库为 CANCELED，避免永久 RUNNING。
     */
    public void requestCancel(Long taskId) {
        // 级联取消补充入口，避免主任务等待子任务时被卡住。
        for (PatrolTaskEntity child : taskMapper.findByParentId(taskId)) {
            if (PatrolTaskStatus.ACTIVE.contains(child.getStatus())) {
                requestCancel(child.getId());
            }
        }
        boolean live = runningTaskIds.contains(taskId);
        cancellationFlags.computeIfAbsent(taskId, ignored -> new AtomicBoolean(false)).set(true);
        taskMapper.updateCancelRequested(taskId, true);
        if (live) {
            return;
        }
        taskMapper.findById(taskId).ifPresent(task -> {
            if (PatrolTaskStatus.ACTIVE.contains(task.getStatus())) {
                safeFinish(task, "CANCELED", "已取消（未检测到活跃执行）");
            }
        });
        cancellationFlags.remove(taskId);
    }

    /**
     * 运行期兜底：定期回收孤儿任务。
     *
     * <p>1）用户已点取消但没有活跃线程 → 直接 CANCELED；
     * 2）RUNNING/QUEUED 且已超过「时长预算 + 宽限期」仍无活跃线程 → FAILED（执行中断）。
     */
    @Scheduled(fixedDelay = 60_000)
    public void sweepStaleTasks() {
        List<PatrolTaskEntity> active;
        try {
            active = taskMapper.findActiveRunningTasks();
        } catch (Exception exception) {
            log.warn("Patrol stale task scan failed: {}", exception.getMessage());
            return;
        }
        for (PatrolTaskEntity task : active) {
            if (runningTaskIds.contains(task.getId())) {
                continue;
            }
            try {
                // 主任务在等待补充入口时不参与超时回收，由子任务结束时统一收尾。
                if (taskMapper.existsActiveChild(task.getId())) {
                    continue;
                }
                if ("WAITING_RUNS".equals(task.getCurrentStage())) {
                    finalizeParentIfReady(task.getId());
                    continue;
                }
                if (Boolean.TRUE.equals(task.getCancelRequested())) {
                    safeFinish(task, "CANCELED", "已取消（执行线程已结束）");
                    cancellationFlags.remove(task.getId());
                    continue;
                }
                int budgetMinutes = task.getBudgetDurationMinutes() == null
                        ? 20 : task.getBudgetDurationMinutes();
                LocalDateTime reference = task.getStartedAt() != null
                        ? task.getStartedAt() : task.getCreatedAt();
                if (reference == null) {
                    continue;
                }
                if (reference.plusMinutes(budgetMinutes).plus(STALE_GRACE).isBefore(LocalDateTime.now())) {
                    safeFinish(task, "FAILED",
                            "任务长时间无进展（超过时长预算与宽限期），已标记中断；请重新执行");
                    cancellationFlags.remove(task.getId());
                }
            } catch (Exception exception) {
                log.warn("Patrol stale task recovery failed. taskId={}, reason={}",
                        task.getId(), exception.getMessage());
            }
        }
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
        PatrolBudget budget = new PatrolBudget(task.getBudgetDurationMinutes() == null ? 20 : task.getBudgetDurationMinutes(),
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
                budget.addAdvice("依赖安装超时：请检查服务器出网与 npm 源是否可用，或调大巡页「时长预算」后重试。");
                finish(task, "FAILED", "安装依赖超时", budget);
                return;
            }
            if (install.exitCode() != 0) {
                budget.addAdvice("依赖安装失败：请检查 Runner 环境与网络；修复后重新执行。");
                finish(task, "FAILED", "依赖安装失败，退出码=" + install.exitCode(), budget);
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
                budget.addAdvice("入口页快照超时：请检查入口 URL 是否可访问、认证配置是否正确，或调大「时长预算」。");
                finish(task, "FAILED", "入口页快照超时", budget);
                return;
            }
            if (parsed.snapshot() == null) {
                if ("SUPPLEMENT".equals(task.getEntryType()) && parsed.setupFailure() != null) {
                    budget.addAdvice("补充入口起始状态不可达：请检查前置描述或改用起始地址；该 Run 未执行用例。");
                    finish(task, "UNREACHABLE", "起始状态不可达：" + parsed.setupFailure(), budget);
                    return;
                }
                budget.addAdvice("入口页快照失败：请确认入口 URL 可访问、认证信息有效，并检查服务端到目标页面的网络。");
                finish(task, "FAILED", "未能获取入口页快照"
                        + (snapshotRun.exitCode() != 0 ? "，退出码=" + snapshotRun.exitCode() : ""), budget);
                return;
            }
            Snapshot snapshot = parsed.snapshot();
            task.setSnapshotJson(snapshotJson(snapshot));
            task.setSnapshotScreenshotKey(mapKey(uploaded, snapshot.screenshot()));
            taskMapper.update(task);

            // 补充入口：基于刚采集的入口页快照解析前置，并创建独立子任务并发执行。
            startSupplements(task, snapshot, scene.startUrl());

            // ── 规划 ──
            task.setCurrentStage("PLANNING");
            taskMapper.update(task);
            JsonNode snapshotNode = MAPPER.readTree(snapshotJson(snapshot));
            AiModelConfigEntity planModel = modelFromSnapshot(task, "plan");
            PlanResult plan = plannerService.plan(task.getId(), task.getSpaceId(), task.getTaskBrief(),
                    snapshotNode, budget, planModel, scene.visibleTextLimit());
            plan = supplementCoverage(task, snapshotNode, plan, budget);
            if ("SUPPLEMENT".equals(task.getEntryType())) {
                Approval approval = readApproval(task);
                plan = filterSupplementPlan(plan, scene,
                        approval.allowWrite(), approval.allowDestructive(), budget);
            }
            String coverage = PatrolCoverage.coverageText(snapshotNode, coverageKeysOf(plan.cases()));
            persistCases(task, plan, snapshot);
            task.setLlmTokenUsed(budget.getTokensUsed());
            task.setDegraded(budget.isDegraded() || plan.degraded());
            refreshStats(task);
            String stops = budget.stopReasonText();
            String planningNote = stops.isBlank() ? coverage : stops + "；" + coverage;
            if (snapshot.visibleTextTruncated()) {
                int kept = snapshot.visibleText() == null ? 0 : snapshot.visibleText().length();
                planningNote = planningNote + "；入口页可见文本已截断：仅前 " + kept
                        + " 字（原文 " + snapshot.visibleTextLength() + " 字），规划与判定可能遗漏内容";
            }
            task.setPlanningNote(trimMessage(planningNote));
            task.setResultMessage(trimMessage("用例规划完成，共 " + plan.cases().size() + " 条；" + planningNote));
            taskMapper.update(task);

            List<PatrolCaseEntity> cases = caseMapper.findByTaskId(taskId);
            // 已审批（主任务统一审批后启动的补充入口）：跳过二次审批，直接执行。
            boolean approved = task.getApprovalJson() != null && !task.getApprovalJson().isBlank();
            if (approved) {
                task.setResultMessage("审批通过，开始执行");
                taskMapper.update(task);
                executeAndJudge(taskId);
                return;
            }
            List<PatrolTaskEntity> gatedChildren = "MAIN".equals(task.getEntryType())
                    ? taskMapper.findByParentId(taskId).stream()
                    .filter(child -> "WAITING_APPROVAL".equals(child.getStatus())).toList()
                    : List.of();
            boolean needsApproval = !gatedChildren.isEmpty()
                    || cases.stream().anyMatch(item -> !"READ".equals(item.getWorstRisk()));
            if (!needsApproval || (gatedChildren.isEmpty() && autoApproved(scene, cases))) {
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
            String approvalMessage = "等待执行前审批：请确认写操作/破坏性操作用例";
            if (!gatedChildren.isEmpty()) {
                approvalMessage = approvalMessage + "；另有 " + gatedChildren.size()
                        + " 个补充入口含写操作前置，审批通过后才会执行前置并生成状态快照";
            }
            task.setResultMessage(approvalMessage);
            taskMapper.update(task);
        } catch (Exception exception) {
            log.error("Patrol prepare/plan failed. taskId={}", taskId, exception);
            budget.addAdvice("准备/规划阶段异常：请查看服务日志与「模型日志」；修复后重新执行。");
            safeFinish(task, "FAILED", "巡页准备或规划失败: " + exception.getMessage(), budget);
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
        PatrolBudget budget = new PatrolBudget(task.getBudgetDurationMinutes() == null ? 20 : task.getBudgetDurationMinutes(),
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
                budget.addAdvice(PatrolAdvice.forDuration("执行阶段达到时长预算"));
            } else if (execution.exitCode() != 0) {
                budget.addStopReason("执行器异常退出，退出码=" + execution.exitCode());
                budget.addAdvice("执行器异常退出：请查看失败用例的步骤日志与录屏定位问题；修复页面或调整用例后重试。");
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
            finish(task, status, message, budget);
        } catch (Exception exception) {
            log.error("Patrol execute/judge failed. taskId={}", taskId, exception);
            budget.addAdvice("执行/判定阶段异常：请查看服务日志、失败用例截图与录屏；修复后重新执行。");
            safeFinish(task, "FAILED", "巡页执行失败: " + exception.getMessage(), budget);
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
        boolean finalPageTruncated = parsed.cases().stream().anyMatch(CaseRun::visibleTextTruncated);
        if (finalPageTruncated) {
            budget.addStopReason("最终页面可见文本已截断，视觉判定依据不完整");
        }
        for (PatrolCaseEntity caseEntity : cases) {
            List<PatrolCaseStepEntity> steps = caseStepMapper.findByCaseId(caseEntity.getId());
            CaseRun run = runs.get(caseEntity.getId());
            String screenshot = run == null ? task.getSnapshotScreenshotKey() : mapKey(uploaded, run.screenshot());
            if (screenshot == null) {
                screenshot = task.getSnapshotScreenshotKey();
            }
            JudgeResult judged = judgeService.judge(task.getSpaceId(), caseEntity, steps, run, snapshot,
                    screenshot, run == null ? null : mapKey(uploaded, run.consoleKey()),
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
                                          PatrolBudget budget) {
        if (!"LLM".equals(plan.source())) {
            return plan;
        }
        Set<String> keys = coverageKeysOf(plan.cases());
        List<JsonNode> uncovered = PatrolCoverage.uncoveredElements(snapshotNode, keys);
        if (uncovered.isEmpty() || budget.isDurationExhausted()) {
            return plan;
        }
        AiModelConfigEntity supplementModel = modelFromSnapshot(task, "supplement");
        if (supplementModel == null) {
            supplementModel = modelFromSnapshot(task, "plan");
        }
        List<String> existingNames = plan.cases().stream().map(PlannedCase::name).toList();
        PlanResult extra = plannerService.planSupplement(task.getId(), task.getSpaceId(), task.getTaskBrief(),
                snapshotNode, uncovered, existingNames, budget, supplementModel);
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
        finish(task, status, message, null);
    }

    private void finish(PatrolTaskEntity task, String status, String message, PatrolBudget budget) {
        // 主任务：若已创建补充入口，先暂存自身结果，等全部子任务结束后统一汇总收尾。
        if ("MAIN".equals(task.getEntryType()) && !taskMapper.findByParentId(task.getId()).isEmpty()) {
            deferParentFinish(task, status, message, budget);
            return;
        }
        refreshStats(task);
        task.setStatus(status);
        task.setCurrentStage("FINISHED");
        task.setResultMessage(trimMessage(message));
        if (budget != null) {
            List<String> advice = budget.getAdvice();
            task.setAdviceJson(advice.isEmpty() ? null : adviceJson(advice));
        }
        task.setFinishedAt(LocalDateTime.now());
        task.setDurationMs(task.getStartedAt() == null ? 0L
                : Duration.between(task.getStartedAt(), LocalDateTime.now()).toMillis());
        taskMapper.update(task);
        taskMapper.finish(task.getId(), status, trimMessage(message), retentionDays);
        // 场景的「最近任务」只跟随主入口，补充入口不覆盖。
        if ("MAIN".equals(task.getEntryType())) {
            sceneMapper.updateLastTask(task.getSceneId(), LocalDateTime.now(), task.getId(), status);
        }
        // 补充入口结束：检查主任务是否已暂存结果并且全部子任务都已结束。
        if ("SUPPLEMENT".equals(task.getEntryType()) && task.getParentTaskId() != null) {
            finalizeParentIfReady(task.getParentTaskId());
        }
    }

    // ───────────────────────── 补充入口 Run：创建、过滤与汇总 ─────────────────────────

    /** 基于主入口快照解析补充入口前置，并为每个入口创建独立子任务并发执行。 */
    private void startSupplements(PatrolTaskEntity parent, Snapshot snapshot, String sceneStartUrl) {
        if (!"MAIN".equals(parent.getEntryType())) {
            return;
        }
        List<PatrolSceneSupplementDraft> drafts = supplementsOf(parent);
        if (drafts.isEmpty()) {
            return;
        }
        JsonNode snapshotNode;
        try {
            snapshotNode = MAPPER.readTree(snapshotJson(snapshot));
        } catch (Exception exception) {
            log.warn("Patrol supplements skipped: snapshot unreadable. taskId={}, reason={}",
                    parent.getId(), exception.getMessage());
            return;
        }
        AiModelConfigEntity resolveModel = modelFromSnapshot(parent, "resolve");
        SceneSnapshot scene = readScene(parent);
        int started = 0;
        int gated = 0;
        for (PatrolSceneSupplementDraft draft : drafts) {
            if (draft == null || Boolean.FALSE.equals(draft.enabled())) {
                continue;
            }
            String label = supplementLabel(draft);
            String startUrl = sceneStartUrl;
            String risk = "READ";
            try {
                String entryType = draft.entryType() == null
                        ? "ACTION" : draft.entryType().trim().toUpperCase(Locale.ROOT);
                List<Map<String, Object>> setupSteps = List.of();
                if ("URL".equals(entryType)) {
                    startUrl = supplementStartUrl(sceneStartUrl, draft);
                } else {
                    List<ResolvedStep> steps = draft.steps() == null ? List.of() : draft.steps();
                    if (steps.isEmpty()) {
                        ResolveResult resolved = supplementResolver.resolve(parent.getId(), parent.getSpaceId(),
                                draft, snapshotNode, resolveModel);
                        steps = resolved.steps();
                    }
                    if (steps.isEmpty()) {
                        String reason = draft.unresolvedReason() == null || draft.unresolvedReason().isBlank()
                                ? "前置未解析出可执行步骤，请修改前置描述或改用起始地址"
                                : draft.unresolvedReason();
                        createFailedSupplement(parent, label, startUrl, reason);
                        continue;
                    }
                    setupSteps = setupStepsPayload(steps);
                    risk = preconditionRiskOf(steps);
                }
                String brief = supplementBrief(parent.getTaskBrief(), draft.focusText());
                PatrolTaskEntity child = createSupplementTask(parent, label, startUrl, setupSteps, brief);
                if (preconditionNeedsApproval(risk, scene)) {
                    // 写/危险前置：审批前不执行前置、不采集状态快照，等待主任务统一审批。
                    gated += 1;
                    markSupplementGated(child, risk);
                    continue;
                }
                try {
                    start(child.getId());
                } catch (RejectedExecutionException exception) {
                    // submit 内部已把子任务落库为失败；这里只保证其它入口继续创建。
                    log.warn("Patrol supplement rejected by executor. parentTaskId={}, childTaskId={}",
                            parent.getId(), child.getId());
                    continue;
                }
                started += 1;
            } catch (Exception exception) {
                log.warn("Patrol supplement start failed. parentTaskId={}, label={}, reason={}",
                        parent.getId(), label, exception.getMessage());
            }
        }
        log.info("Patrol supplements created. parentTaskId={}, configured={}, started={}, gated={}",
                parent.getId(), drafts.size(), started, gated);
    }

    /** 从主任务场景快照读取补充入口配置。 */
    private List<PatrolSceneSupplementDraft> supplementsOf(PatrolTaskEntity parent) {
        if (parent.getSceneSnapshotJson() == null || parent.getSceneSnapshotJson().isBlank()) {
            return List.of();
        }
        try {
            String json = MAPPER.readTree(parent.getSceneSnapshotJson()).path("supplementsJson").asText("");
            if (json.isBlank()) {
                return List.of();
            }
            return MAPPER.readValue(json, new TypeReference<List<PatrolSceneSupplementDraft>>() {
            });
        } catch (Exception exception) {
            log.warn("Parse patrol supplements failed. taskId={}, reason={}",
                    parent.getId(), exception.getMessage());
            return List.of();
        }
    }

    private PatrolTaskEntity createSupplementTask(PatrolTaskEntity parent, String label, String startUrl,
                                                  List<Map<String, Object>> setupSteps, String brief) {
        PatrolTaskEntity child = new PatrolTaskEntity();
        child.setSpaceId(parent.getSpaceId());
        child.setSceneId(parent.getSceneId());
        child.setParentTaskId(parent.getId());
        child.setEntryType("SUPPLEMENT");
        child.setEntryLabel(label);
        child.setSetupStepsJson(setupSteps.isEmpty() ? null : toJson(setupSteps));
        child.setSceneName(parent.getSceneName());
        child.setTriggerType("SUPPLEMENT");
        child.setStatus("QUEUED");
        child.setCurrentStage("QUEUED");
        child.setStartUrl(startUrl);
        child.setTaskBrief(brief);
        child.setSceneSnapshotJson(parent.getSceneSnapshotJson());
        child.setModelSnapshotJson(parent.getModelSnapshotJson());
        child.setBudgetDurationMinutes(parent.getBudgetDurationMinutes());
        child.setCreatedBy(parent.getCreatedBy());
        child.setCreatedAt(LocalDateTime.now());
        // 非空统计列的初值；后续 update 会整体回写这些字段。
        child.setCaseCount(0);
        child.setCasePassCount(0);
        child.setCaseFailCount(0);
        child.setCaseSkipCount(0);
        child.setFindingP0Count(0);
        child.setFindingP1Count(0);
        child.setFindingP2Count(0);
        child.setLlmTokenUsed(0);
        child.setDegraded(false);
        child.setCancelRequested(false);
        taskMapper.insert(child);
        return child;
    }

    /** 前置无法解析时也落一条 UNREACHABLE 子任务，便于在任务详情里看到具体原因，且不影响主任务状态。 */
    private void createFailedSupplement(PatrolTaskEntity parent, String label, String startUrl, String reason) {
        try {
            PatrolTaskEntity child = createSupplementTask(parent, label, startUrl, List.of(),
                    supplementBrief(parent.getTaskBrief(), null));
            String message = "前置无法解析：" + reason;
            child.setStatus("UNREACHABLE");
            child.setCurrentStage("FINISHED");
            child.setResultMessage(trimMessage(message));
            child.setFinishedAt(LocalDateTime.now());
            child.setDurationMs(0L);
            taskMapper.update(child);
            taskMapper.finish(child.getId(), "UNREACHABLE", trimMessage(message), retentionDays);
        } catch (Exception exception) {
            log.warn("Patrol supplement failure record failed. parentTaskId={}, label={}, reason={}",
                    parent.getId(), label, exception.getMessage());
        }
    }

    /** 前置风险：破坏性关键字优先；填写类动作或写操作关键字为 WRITE；其余只读。 */
    private static String preconditionRiskOf(List<ResolvedStep> steps) {
        boolean write = false;
        for (ResolvedStep step : steps) {
            if (step == null) {
                continue;
            }
            String action = step.action() == null ? "" : step.action().trim().toLowerCase(Locale.ROOT);
            String target = step.target() == null ? "" : step.target();
            if (containsKeyword(target, DESTRUCTIVE_KEYWORDS)) {
                return "DESTRUCTIVE";
            }
            if (PRECONDITION_MUTATING_ACTIONS.contains(action) || containsKeyword(target, WRITE_KEYWORDS)) {
                write = true;
            }
        }
        return write ? "WRITE" : "READ";
    }

    /** 从任务的前置步骤快照复算风险，供统一审批判断该入口需要哪类授权。 */
    public static String preconditionRiskOf(PatrolTaskEntity task) {
        String json = task == null ? null : task.getSetupStepsJson();
        if (json == null || json.isBlank()) {
            return "READ";
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isArray()) {
                return "READ";
            }
            List<ResolvedStep> steps = new ArrayList<>();
            for (JsonNode node : root) {
                steps.add(new ResolvedStep(node.path("action").asText(""), node.path("selector").asText(""),
                        List.of(), node.path("label").asText(""), node.path("value").asText(""),
                        null, null));
            }
            return preconditionRiskOf(steps);
        } catch (Exception exception) {
            return "READ";
        }
    }

    private static boolean containsKeyword(String text, List<String> keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.replaceAll("\\s+", "");
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 场景未自动批准对应风险时，前置必须等待人工审批后才执行。 */
    private static boolean preconditionNeedsApproval(String risk, SceneSnapshot scene) {
        if ("READ".equals(risk)) {
            return false;
        }
        return !("DESTRUCTIVE".equals(risk) ? scene.autoApproveDestructive() : scene.autoApproveWrite());
    }

    private void markSupplementGated(PatrolTaskEntity child, String risk) {
        child.setStatus("WAITING_APPROVAL");
        child.setCurrentStage("WAITING_APPROVAL");
        child.setPlanningNote("前置风险：" + risk + "；审批前不执行前置、不采集状态快照");
        child.setResultMessage("该补充入口含" + ("DESTRUCTIVE".equals(risk) ? "破坏性" : "写")
                + "操作前置，审批通过后执行前置并生成状态快照与用例");
        taskMapper.update(child);
    }

    private String supplementStartUrl(String sceneStartUrl, PatrolSceneSupplementDraft draft) {
        String raw = draft.startUrl() == null ? "" : draft.startUrl().trim();
        if (raw.isEmpty()) {
            return sceneStartUrl;
        }
        try {
            return URI.create(sceneStartUrl).resolve(raw).toString();
        } catch (Exception exception) {
            return raw.startsWith("http") ? raw : sceneStartUrl;
        }
    }

    private static String supplementLabel(PatrolSceneSupplementDraft draft) {
        if (draft.name() != null && !draft.name().isBlank()) {
            return draft.name().trim();
        }
        String text = draft.preconditionText() != null && !draft.preconditionText().isBlank()
                ? draft.preconditionText() : draft.startUrl();
        if (text == null || text.isBlank()) {
            return "补充入口";
        }
        String trimmed = text.trim();
        return trimmed.length() <= 40 ? trimmed : trimmed.substring(0, 40);
    }

    private static String supplementBrief(String taskBrief, String focusText) {
        String focus = focusText == null ? "" : focusText.trim();
        String brief = taskBrief == null ? "" : taskBrief.trim();
        if (focus.isEmpty()) {
            return brief;
        }
        String merged = brief.isEmpty() ? "补充入口关注点：" + focus
                : brief + "；补充入口关注点：" + focus;
        return merged.length() <= 1024 ? merged : merged.substring(0, 1024);
    }

    private static List<Map<String, Object>> setupStepsPayload(List<ResolvedStep> steps) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (ResolvedStep step : steps) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("action", step.action());
            item.put("selector", step.selector());
            if (step.frame() != null && !step.frame().isEmpty()) {
                item.put("frame", step.frame());
            }
            if (step.target() != null && !step.target().isBlank()) {
                item.put("label", step.target());
            }
            if (step.value() != null && !step.value().isBlank()) {
                item.put("value", step.value());
            }
            payload.add(item);
        }
        return payload;
    }

    /**
     * 补充入口只执行已授权的用例：READ 始终允许；
     * 写/危险操作取「场景自动批准」与「主任务人工审批」的并集。
     */
    private PlanResult filterSupplementPlan(PlanResult plan, SceneSnapshot scene,
                                            boolean approvedWrite, boolean approvedDestructive,
                                            PatrolBudget budget) {
        boolean allowWrite = approvedWrite || scene.autoApproveWrite();
        boolean allowDestructive = approvedDestructive || scene.autoApproveDestructive();
        List<PlannedCase> kept = new ArrayList<>();
        int removed = 0;
        for (PlannedCase item : plan.cases()) {
            String risk = item.worstRisk() == null ? "READ" : item.worstRisk();
            boolean allowed = "READ".equals(risk)
                    || ("DESTRUCTIVE".equals(risk) ? allowDestructive : allowWrite);
            if (allowed) {
                kept.add(item);
            } else {
                removed += 1;
            }
        }
        if (removed == 0) {
            return plan;
        }
        budget.addStopReason("补充入口跳过 " + removed
                + " 条写/危险操作用例（未获得对应授权）");
        return new PlanResult(kept, plan.source(), plan.tokens(), plan.modelId(), plan.degraded(), plan.reason());
    }

    private void deferParentFinish(PatrolTaskEntity task, String status, String message, PatrolBudget budget) {
        if ("FAILED".equals(status) || "CANCELED".equals(status)) {
            cancelGatedChildren(task.getId());
        }
        refreshStats(task);
        List<String> advice = budget == null ? List.of() : budget.getAdvice();
        String adviceJson = advice.isEmpty() ? null : adviceJson(advice);
        task.setStatus("RUNNING");
        task.setCurrentStage("WAITING_RUNS");
        task.setResultMessage(trimMessage("主入口已结束，等待补充入口执行完成"));
        task.setAdviceJson(adviceJson);
        taskMapper.update(task);
        // 先落库再登记待收尾，避免并发子任务提前收尾后被本次 update 覆盖回 RUNNING。
        pendingParentFinishes.put(task.getId(), new PendingFinish(status, message, adviceJson));
        finalizeParentIfReady(task.getId());
    }

    /**
     * 主任务未能进入执行（失败/取消）时，仍在等待审批的补充入口直接取消，
     * 避免主任务永远等不到子任务结束。
     */
    private void cancelGatedChildren(Long parentId) {
        for (PatrolTaskEntity child : taskMapper.findByParentId(parentId)) {
            if (!"WAITING_APPROVAL".equals(child.getStatus())) {
                continue;
            }
            try {
                String message = "主任务未进入执行，补充入口未执行";
                child.setStatus("CANCELED");
                child.setCurrentStage("FINISHED");
                child.setResultMessage(message);
                child.setFinishedAt(LocalDateTime.now());
                child.setDurationMs(0L);
                taskMapper.update(child);
                taskMapper.finish(child.getId(), "CANCELED", message, retentionDays);
            } catch (Exception exception) {
                log.warn("Cancel gated supplement failed. taskId={}, reason={}",
                        child.getId(), exception.getMessage());
            }
        }
    }

    /** 主任务自身已结束且全部补充入口结束后，汇总子任务统计并最终收尾。 */
    private void finalizeParentIfReady(Long parentId) {
        PendingFinish pending = pendingParentFinishes.get(parentId);
        if (pending == null) {
            return;
        }
        if (taskMapper.existsActiveChild(parentId)) {
            return;
        }
        Optional<PatrolTaskEntity> optional = taskMapper.findById(parentId);
        if (optional.isEmpty()) {
            pendingParentFinishes.remove(parentId);
            return;
        }
        if (!pendingParentFinishes.remove(parentId, pending)) {
            return;
        }
        PatrolTaskEntity parent = optional.get();
        List<PatrolTaskEntity> children = taskMapper.findByParentId(parentId);
        int completed = 0;
        int failed = 0;
        int canceled = 0;
        int unreachable = 0;
        // 以主入口自身统计为基数，再叠加全部补充入口。
        int caseCount = parent.getCaseCount() == null ? 0 : parent.getCaseCount();
        int pass = parent.getCasePassCount() == null ? 0 : parent.getCasePassCount();
        int fail = parent.getCaseFailCount() == null ? 0 : parent.getCaseFailCount();
        int skip = parent.getCaseSkipCount() == null ? 0 : parent.getCaseSkipCount();
        int p0 = parent.getFindingP0Count() == null ? 0 : parent.getFindingP0Count();
        int p1 = parent.getFindingP1Count() == null ? 0 : parent.getFindingP1Count();
        int p2 = parent.getFindingP2Count() == null ? 0 : parent.getFindingP2Count();
        int tokens = parent.getLlmTokenUsed() == null ? 0 : parent.getLlmTokenUsed();
        boolean degraded = Boolean.TRUE.equals(parent.getDegraded());
        List<String> failedLabels = new ArrayList<>();
        for (PatrolTaskEntity child : children) {
            caseCount += child.getCaseCount() == null ? 0 : child.getCaseCount();
            pass += child.getCasePassCount() == null ? 0 : child.getCasePassCount();
            fail += child.getCaseFailCount() == null ? 0 : child.getCaseFailCount();
            skip += child.getCaseSkipCount() == null ? 0 : child.getCaseSkipCount();
            p0 += child.getFindingP0Count() == null ? 0 : child.getFindingP0Count();
            p1 += child.getFindingP1Count() == null ? 0 : child.getFindingP1Count();
            p2 += child.getFindingP2Count() == null ? 0 : child.getFindingP2Count();
            tokens += child.getLlmTokenUsed() == null ? 0 : child.getLlmTokenUsed();
            degraded = degraded || Boolean.TRUE.equals(child.getDegraded());
            if ("FAILED".equals(child.getStatus())) {
                failed += 1;
                failedLabels.add(child.getEntryLabel() == null || child.getEntryLabel().isBlank()
                        ? ("#" + child.getId()) : child.getEntryLabel());
            } else if ("UNREACHABLE".equals(child.getStatus())) {
                // 前置无法解析或起始状态不可达：该 Run 不计入失败，也不影响主任务状态。
                unreachable += 1;
            } else if ("CANCELED".equals(child.getStatus())) {
                canceled += 1;
            } else {
                completed += 1;
            }
        }
        String status = finalParentStatus(pending.status(), failed);
        String head = pending.message() == null ? "" : pending.message();
        // 主入口消息可能因降级/失败原因很长，先截断自身部分，避免末尾的补充入口汇总被整体截掉。
        if (!children.isEmpty() && head.length() > 1500) {
            head = head.substring(0, 1500) + "…";
        }
        StringBuilder message = new StringBuilder(head);
        if (!children.isEmpty()) {
            message.append("；补充入口 ").append(children.size()).append(" 个（完成 ").append(completed);
            if (unreachable > 0) {
                message.append("，不可达 ").append(unreachable);
            }
            message.append("，失败 ").append(failed);
            if (canceled > 0) {
                message.append("，取消 ").append(canceled);
            }
            message.append("）");
            if (!failedLabels.isEmpty()) {
                message.append("；失败入口：").append(String.join("、", failedLabels));
            }
        }
        parent.setStatus(status);
        parent.setCurrentStage("FINISHED");
        parent.setCaseCount(caseCount);
        parent.setCasePassCount(pass);
        parent.setCaseFailCount(fail);
        parent.setCaseSkipCount(skip);
        parent.setFindingP0Count(p0);
        parent.setFindingP1Count(p1);
        parent.setFindingP2Count(p2);
        parent.setLlmTokenUsed(tokens);
        parent.setDegraded(degraded);
        parent.setAdviceJson(mergeAdvice(pending.adviceJson(), children));
        parent.setResultMessage(trimMessage(message.toString()));
        parent.setFinishedAt(LocalDateTime.now());
        parent.setDurationMs(parent.getStartedAt() == null ? 0L
                : Duration.between(parent.getStartedAt(), LocalDateTime.now()).toMillis());
        taskMapper.update(parent);
        taskMapper.finish(parentId, status, trimMessage(message.toString()), retentionDays);
        sceneMapper.updateLastTask(parent.getSceneId(), LocalDateTime.now(), parentId, status);
        log.info("Patrol parent finalized. parentTaskId={}, status={}, children={}, cases={}",
                parentId, status, children.size(), caseCount);
    }

    private static String finalParentStatus(String parentStatus, int failedChildren) {
        if ("CANCELED".equals(parentStatus)) {
            return "CANCELED";
        }
        if (failedChildren > 0 || "FAILED".equals(parentStatus)) {
            return "FAILED";
        }
        return "COMPLETED";
    }

    /** 主任务与补充入口的解决建议去重合并，供概览直接展示。 */
    private String mergeAdvice(String parentAdviceJson, List<PatrolTaskEntity> children) {
        LinkedHashSet<String> advice = new LinkedHashSet<>();
        collectAdvice(advice, parentAdviceJson);
        for (PatrolTaskEntity child : children) {
            collectAdvice(advice, child.getAdviceJson());
        }
        return advice.isEmpty() ? null : adviceJson(new ArrayList<>(advice));
    }

    private void collectAdvice(Set<String> target, String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            target.addAll(MAPPER.readValue(json, new TypeReference<List<String>>() {
            }));
        } catch (Exception ignored) {
            // 建议 JSON 损坏时忽略，不影响任务收尾。
        }
    }

    private void safeFinish(PatrolTaskEntity task, String status, String message) {
        safeFinish(task, status, message, null);
    }

    private void safeFinish(PatrolTaskEntity task, String status, String message, PatrolBudget budget) {
        try {
            finish(task, status, message, budget);
        } catch (Exception exception) {
            log.warn("Patrol finish failed. taskId={}, reason={}", task.getId(), exception.getMessage());
        }
    }

    private static String adviceJson(List<String> advice) {
        try {
            return MAPPER.writeValueAsString(advice);
        } catch (Exception exception) {
            return null;
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
                scene.visibleTextLimit(), budget.getRemainingDurationMs(),
                setupStepsOf(task), "");
    }

    /** 补充入口的前置步骤（JSON 数组），主入口为空。 */
    private List<Map<String, Object>> setupStepsOf(PatrolTaskEntity task) {
        String json = task.getSetupStepsJson();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception exception) {
            log.warn("Parse patrol setup steps failed. taskId={}, reason={}",
                    task.getId(), exception.getMessage());
            return List.of();
        }
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
        snapshot.put("resolveModelId", scene.getResolveModelId());
        snapshot.put("supplementModelId", scene.getSupplementModelId());
        snapshot.put("supplementsJson", scene.getSupplementsJson());
        snapshot.put("budgetDurationMinutes", scene.getBudgetDurationMinutes());
        snapshot.put("visibleTextLimit", scene.getVisibleTextLimit() == null ? 1800 : scene.getVisibleTextLimit());
        snapshot.put("scrollScan", !Boolean.FALSE.equals(scene.getScrollScan()));
        snapshot.put("recordVideo", !Boolean.FALSE.equals(scene.getRecordVideo()));
        snapshot.put("autoApproveWrite", Boolean.TRUE.equals(scene.getAutoApproveWrite()));
        snapshot.put("autoApproveDestructive", Boolean.TRUE.equals(scene.getAutoApproveDestructive()));
        return toJson(snapshot);
    }

    public static String modelSnapshotJson(AiModelConfigEntity plan, AiModelConfigEntity judge,
                                           AiModelConfigEntity resolve, AiModelConfigEntity supplement) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("plan", modelNode(plan));
        snapshot.put("judge", modelNode(judge));
        snapshot.put("resolve", modelNode(resolve == null ? plan : resolve));
        snapshot.put("supplement", modelNode(supplement == null ? plan : supplement));
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
        node.put("visibleTextLength", snapshot.visibleTextLength());
        node.put("visibleTextTruncated", snapshot.visibleTextTruncated());
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

    /** 主任务等待补充入口期间暂存的自身结果。 */
    private record PendingFinish(String status, String message, String adviceJson) {
    }

    private record SceneSnapshot(String startUrl, String browser, boolean headless, String authType,
                                 String authCookieEncrypted, String authTokenEncrypted,
                                 String authTokenHeader, String authLocalStorageEncrypted,
                                 int visibleTextLimit, boolean scrollScan, boolean recordVideo,
                                 boolean autoApproveWrite, boolean autoApproveDestructive) {
    }
}
