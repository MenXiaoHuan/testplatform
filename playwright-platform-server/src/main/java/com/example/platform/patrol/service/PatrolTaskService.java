package com.example.platform.patrol.service;

import com.example.platform.common.PageResponse;
import com.example.platform.patrol.dto.PatrolApprovalRequest;
import com.example.platform.patrol.dto.PatrolCaseResponse;
import com.example.platform.patrol.dto.PatrolCaseStepResponse;
import com.example.platform.patrol.dto.PatrolModelLogResponse;
import com.example.platform.patrol.dto.PatrolReportResponse;
import com.example.platform.patrol.dto.PatrolRunResponse;
import com.example.platform.patrol.dto.PatrolTaskDetailResponse;
import com.example.platform.patrol.dto.PatrolTaskResponse;
import com.example.platform.patrol.mapper.PatrolCaseMapper;
import com.example.platform.patrol.mapper.PatrolCaseStepMapper;
import com.example.platform.patrol.mapper.PatrolTaskMapper;
import com.example.platform.patrol.model.PatrolCaseEntity;
import com.example.platform.patrol.model.PatrolCaseStepEntity;
import com.example.platform.patrol.model.PatrolTaskEntity;
import com.example.platform.patrol.model.PatrolTaskStatus;
import com.example.platform.storage.service.ObjectStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 巡页任务查询、审批、取消、删除与证据读取。 */
@Service
public class PatrolTaskService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(PatrolTaskService.class);

    private final PatrolTaskMapper taskMapper;
    private final PatrolCaseMapper caseMapper;
    private final PatrolCaseStepMapper caseStepMapper;
    private final PatrolCoverageService coverageService;
    private final PatrolExecutionService executionService;
    private final PatrolRetentionService retentionService;
    private final PatrolModelLogService modelLogService;
    private final ObjectStorageService objectStorageService;
    private final String storageBucket;
    private final int retentionDays;

    public PatrolTaskService(PatrolTaskMapper taskMapper,
                             PatrolCaseMapper caseMapper,
                             PatrolCaseStepMapper caseStepMapper,
                             PatrolCoverageService coverageService,
                             PatrolExecutionService executionService,
                             PatrolRetentionService retentionService,
                             PatrolModelLogService modelLogService,
                             ObjectStorageService objectStorageService,
                             @Value("${platform.storage.bucket}") String storageBucket,
                             @Value("${platform.patrol.retention-days:90}") int retentionDays) {
        this.taskMapper = taskMapper;
        this.caseMapper = caseMapper;
        this.caseStepMapper = caseStepMapper;
        this.coverageService = coverageService;
        this.executionService = executionService;
        this.retentionService = retentionService;
        this.modelLogService = modelLogService;
        this.objectStorageService = objectStorageService;
        this.storageBucket = storageBucket;
        this.retentionDays = retentionDays <= 0 ? 90 : retentionDays;
    }

    public PageResponse<PatrolTaskResponse> list(Long spaceId, int page, int size) {
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(50, Math.max(1, size));
        long total = taskMapper.countBySpaceId(spaceId);
        return PageResponse.of(
                taskMapper.findPageBySpaceId(spaceId, normalizedSize, (normalizedPage - 1) * normalizedSize)
                        .stream().map(PatrolTaskResponse::from).toList(),
                total, normalizedPage, normalizedSize);
    }

    public PatrolTaskResponse get(Long spaceId, Long id) {
        return PatrolTaskResponse.from(requireTask(spaceId, id));
    }

    public List<PatrolModelLogResponse> modelLogs(Long spaceId, Long id) {
        PatrolTaskEntity task = requireTask(spaceId, id);
        List<PatrolModelLogResponse> logs = new ArrayList<>();
        for (PatrolTaskEntity run : runTasks(task)) {
            logs.addAll(modelLogService.list(run.getId()));
        }
        logs.sort(Comparator.comparing(PatrolModelLogResponse::createdAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return logs;
    }

    public PatrolTaskDetailResponse detail(Long spaceId, Long id) {
        PatrolTaskEntity task = requireTask(spaceId, id);
        return new PatrolTaskDetailResponse(
                PatrolTaskResponse.from(task),
                task.getSnapshotJson(),
                task.getSnapshotScreenshotKey(),
                task.getModelSnapshotJson(),
                collectCases(task),
                report(task),
                collectRuns(task));
    }

    public List<PatrolCaseResponse> listCases(Long spaceId, Long id) {
        return collectCases(requireTask(spaceId, id));
    }

    /** 合并主入口与补充入口的用例；补充入口用例带 runId 与入口名称。 */
    private List<PatrolCaseResponse> collectCases(PatrolTaskEntity root) {
        List<PatrolCaseResponse> result = new ArrayList<>();
        for (PatrolTaskEntity run : runTasks(root)) {
            Map<Long, List<PatrolCaseStepEntity>> steps = caseStepMapper.findByTaskId(run.getId()).stream()
                    .collect(Collectors.groupingBy(PatrolCaseStepEntity::getCaseId));
            for (PatrolCaseEntity caseEntity : caseMapper.findByTaskId(run.getId())) {
                result.add(PatrolCaseResponse.from(caseEntity,
                        steps.getOrDefault(caseEntity.getId(), List.of()).stream()
                                .map(PatrolCaseStepResponse::from).toList(),
                        run.getId(), run.getParentTaskId() == null ? null : run.getEntryLabel()));
            }
        }
        return result;
    }

    /** 详情里的 Run 列表：仅在配置了补充入口时返回（含主入口）。 */
    private List<PatrolRunResponse> collectRuns(PatrolTaskEntity root) {
        List<PatrolTaskEntity> runs = runTasks(root);
        if (runs.size() <= 1) {
            return List.of();
        }
        // 主任务的统计列已汇总全部补充入口；展示主入口时需扣除子任务合计。
        int childCases = 0;
        int childPass = 0;
        int childFail = 0;
        int childSkip = 0;
        int childP0 = 0;
        int childP1 = 0;
        int childP2 = 0;
        int childTokens = 0;
        for (PatrolTaskEntity run : runs) {
            if (run.getParentTaskId() == null) {
                continue;
            }
            childCases += run.getCaseCount() == null ? 0 : run.getCaseCount();
            childPass += run.getCasePassCount() == null ? 0 : run.getCasePassCount();
            childFail += run.getCaseFailCount() == null ? 0 : run.getCaseFailCount();
            childSkip += run.getCaseSkipCount() == null ? 0 : run.getCaseSkipCount();
            childP0 += run.getFindingP0Count() == null ? 0 : run.getFindingP0Count();
            childP1 += run.getFindingP1Count() == null ? 0 : run.getFindingP1Count();
            childP2 += run.getFindingP2Count() == null ? 0 : run.getFindingP2Count();
            childTokens += run.getLlmTokenUsed() == null ? 0 : run.getLlmTokenUsed();
        }
        List<PatrolRunResponse> result = new ArrayList<>();
        for (PatrolTaskEntity run : runs) {
            PatrolCoverage.Stats stats = coverageService.measure(run);
            if (run.getParentTaskId() != null) {
                result.add(PatrolRunResponse.from(run, stats.total(), stats.covered(), stats.rate()));
                continue;
            }
            result.add(PatrolRunResponse.from(run,
                    own(run.getCaseCount(), childCases),
                    own(run.getCasePassCount(), childPass),
                    own(run.getCaseFailCount(), childFail),
                    own(run.getCaseSkipCount(), childSkip),
                    own(run.getFindingP0Count(), childP0),
                    own(run.getFindingP1Count(), childP1),
                    own(run.getFindingP2Count(), childP2),
                    own(run.getLlmTokenUsed(), childTokens),
                    stats.total(), stats.covered(), stats.rate()));
        }
        return result;
    }

    private static int own(Integer total, int childSum) {
        int value = total == null ? 0 : total;
        return Math.max(0, value - childSum);
    }

    /** 主任务展开为其自身 + 全部补充入口；补充入口任务只返回自身。 */
    private List<PatrolTaskEntity> runTasks(PatrolTaskEntity task) {
        if (task.getParentTaskId() != null
                || !"MAIN".equals(task.getEntryType() == null ? "MAIN" : task.getEntryType())) {
            return List.of(task);
        }
        List<PatrolTaskEntity> children = taskMapper.findByParentId(task.getId());
        if (children.isEmpty()) {
            return List.of(task);
        }
        List<PatrolTaskEntity> all = new ArrayList<>();
        all.add(task);
        all.addAll(children);
        return all;
    }

    public PatrolReportResponse report(Long spaceId, Long id) {
        return report(requireTask(spaceId, id));
    }

    private PatrolReportResponse report(PatrolTaskEntity task) {
        List<PatrolCaseEntity> own = caseMapper.findByTaskId(task.getId());
        List<PatrolCaseEntity> cases = new ArrayList<>(own);
        for (PatrolTaskEntity child : runTasks(task)) {
            if (!child.getId().equals(task.getId())) {
                cases.addAll(caseMapper.findByTaskId(child.getId()));
            }
        }
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
        double passRate = cases.isEmpty() ? 0d : Math.round((double) pass / cases.size() * 10000d) / 100d;
        List<Long> pendingApproval = "WAITING_APPROVAL".equals(task.getStatus())
                ? own.stream().filter(item -> !"READ".equals(item.getWorstRisk()))
                .map(PatrolCaseEntity::getId).toList()
                : List.of();
        List<String> pendingSupplementLabels = "WAITING_APPROVAL".equals(task.getStatus())
                ? taskMapper.findByParentId(task.getId()).stream()
                .filter(child -> "WAITING_APPROVAL".equals(child.getStatus()))
                .map(child -> child.getEntryLabel() == null || child.getEntryLabel().isBlank()
                        ? ("#" + child.getId()) : child.getEntryLabel())
                .toList()
                : List.of();
        // 覆盖率按各 Run 的状态快照分别统计后汇总。
        int totalElements = 0;
        int coveredElements = 0;
        for (PatrolTaskEntity run : runTasks(task)) {
            PatrolCoverage.Stats stats = coverageService.measure(run);
            totalElements += stats.total();
            coveredElements += stats.covered();
        }
        double coverageRate = totalElements == 0 ? 0d
                : Math.round((double) coveredElements / totalElements * 10000d) / 100d;
        PatrolReportResponse.CoverageStats coverage = new PatrolReportResponse.CoverageStats(
                totalElements, coveredElements, coverageRate);
        return new PatrolReportResponse(
                task.getId(),
                task.getStatus(),
                task.getResultMessage(),
                Boolean.TRUE.equals(task.getDegraded())
                        || cases.stream().anyMatch(item -> Boolean.TRUE.equals(item.getDegraded())),
                visionConfigured(task),
                new PatrolReportResponse.CaseStats(cases.size(), pass, fail, skip, passRate),
                coverage,
                new PatrolReportResponse.TokenStats(
                        task.getLlmTokenUsed() == null ? 0 : task.getLlmTokenUsed()),
                pendingApproval,
                pendingSupplementLabels);
    }

    /** 执行前一次性审批：按类别或按用例授权，然后恢复执行。 */
    public PatrolTaskResponse approve(Long spaceId, Long id, PatrolApprovalRequest request, String actor) {
        PatrolTaskEntity task = requireTask(spaceId, id);
        if (!"WAITING_APPROVAL".equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不在待审批状态");
        }
        boolean allowWrite = Boolean.TRUE.equals(request.allowWrite());
        boolean allowDestructive = Boolean.TRUE.equals(request.allowDestructive());
        List<Long> caseIds = request.approvedCaseIds() == null ? List.of() : request.approvedCaseIds();
        if (caseIds.stream().anyMatch(caseId -> caseMapper.findById(caseId)
                .map(item -> !item.getTaskId().equals(id)).orElse(true))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "审批用例不属于该任务");
        }
        // 补充入口直接审批：按其前置风险决定启动或取消。
        if (task.getParentTaskId() != null) {
            applySupplementApproval(task, allowWrite, allowDestructive, actor);
            return PatrolTaskResponse.from(requireTask(spaceId, id));
        }
        List<PatrolTaskEntity> gatedChildren = taskMapper.findByParentId(id).stream()
                .filter(child -> "WAITING_APPROVAL".equals(child.getStatus())).toList();
        int authorizedChildren = 0;
        for (PatrolTaskEntity child : gatedChildren) {
            if (supplementAuthorized(child, allowWrite, allowDestructive)) {
                authorizedChildren += 1;
            }
        }
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("source", "MANUAL");
        approval.put("allowWrite", allowWrite);
        approval.put("allowDestructive", allowDestructive);
        approval.put("caseIds", caseIds);
        try {
            task.setApprovalJson(MAPPER.writeValueAsString(approval));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "审批信息序列化失败");
        }
        task.setApprovedBy(actor);
        task.setApprovedAt(LocalDateTime.now());
        task.setStatus("RUNNING");
        task.setCurrentStage("EXECUTING");
        String message = allowWrite || allowDestructive ? "审批通过，开始执行" : "未授权写操作，仅执行只读用例";
        if (!gatedChildren.isEmpty()) {
            message = message + "；补充入口 " + gatedChildren.size() + " 个（启动 " + authorizedChildren
                    + "，未授权 " + (gatedChildren.size() - authorizedChildren) + "）";
        }
        task.setResultMessage(message);
        taskMapper.update(task);
        try {
            executionService.resume(id);
        } catch (RejectedExecutionException exception) {
            // 队列已满：回退到待审批，允许稍后重新审批。
            task.setStatus("WAITING_APPROVAL");
            task.setCurrentStage("WAITING_APPROVAL");
            task.setApprovalJson(null);
            task.setApprovedBy(null);
            task.setApprovedAt(null);
            task.setResultMessage("执行队列已满，请稍后重新审批");
            taskMapper.update(task);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "执行队列已满，请稍后重新审批");
        }
        // 主任务恢复后，统一启动/取消等待中的补充入口。
        for (PatrolTaskEntity child : gatedChildren) {
            try {
                applySupplementApproval(child, allowWrite, allowDestructive, actor);
            } catch (Exception exception) {
                log.warn("Supplement approval apply failed. taskId={}, reason={}",
                        child.getId(), exception.getMessage());
            }
        }
        return PatrolTaskResponse.from(requireTask(spaceId, id));
    }

    /** 该补充入口的前置风险是否已被本次审批覆盖。 */
    private boolean supplementAuthorized(PatrolTaskEntity child, boolean allowWrite, boolean allowDestructive) {
        String risk = PatrolExecutionService.preconditionRiskOf(child);
        if ("READ".equals(risk)) {
            return true;
        }
        return "DESTRUCTIVE".equals(risk) ? allowDestructive : allowWrite;
    }

    /** 把主任务的一次审批应用到补充入口：授权则启动完整流程，未授权则取消该入口。 */
    private void applySupplementApproval(PatrolTaskEntity child, boolean allowWrite,
                                         boolean allowDestructive, String actor) {
        if (!supplementAuthorized(child, allowWrite, allowDestructive)) {
            String risk = PatrolExecutionService.preconditionRiskOf(child);
            String message = "未授权" + ("DESTRUCTIVE".equals(risk) ? "破坏性" : "写")
                    + "操作前置，补充入口未执行";
            child.setStatus("CANCELED");
            child.setCurrentStage("FINISHED");
            child.setResultMessage(message);
            child.setFinishedAt(LocalDateTime.now());
            child.setDurationMs(0L);
            taskMapper.update(child);
            taskMapper.finish(child.getId(), "CANCELED", message, retentionDays);
            log.info("Patrol supplement rejected by approval. taskId={}, risk={}", child.getId(), risk);
            return;
        }
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("source", "MAIN");
        approval.put("allowWrite", allowWrite);
        approval.put("allowDestructive", allowDestructive);
        // 用例在审批后才按状态快照规划，因此按类别授权，不绑定具体用例。
        approval.put("caseIds", List.of());
        try {
            child.setApprovalJson(MAPPER.writeValueAsString(approval));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "审批信息序列化失败");
        }
        child.setApprovedBy(actor);
        child.setApprovedAt(LocalDateTime.now());
        child.setStatus("RUNNING");
        child.setCurrentStage("PREPARING");
        child.setResultMessage("审批通过，执行前置并生成状态快照与用例");
        taskMapper.update(child);
        try {
            executionService.start(child.getId());
        } catch (RejectedExecutionException exception) {
            // submit 已把任务标记为失败，主任务汇总时会体现该入口失败。
            log.warn("Patrol supplement start rejected after approval. taskId={}", child.getId());
        }
    }

    public void cancel(Long spaceId, Long id) {
        PatrolTaskEntity task = requireTask(spaceId, id);
        if (!PatrolTaskStatus.CANCELABLE.contains(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仅排队中或执行中的任务可以取消");
        }
        executionService.requestCancel(id);
    }

    public void delete(Long spaceId, Long id) {
        PatrolTaskEntity task = requireTask(spaceId, id);
        if (PatrolTaskStatus.ACTIVE.contains(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务执行中，无法删除");
        }
        List<PatrolTaskEntity> children = taskMapper.findByParentId(id);
        if (children.stream().anyMatch(child -> PatrolTaskStatus.ACTIVE.contains(child.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "补充入口仍在执行，无法删除");
        }
        for (PatrolTaskEntity child : children) {
            retentionService.purgeArtifacts(child);
            taskMapper.deleteById(child.getId());
        }
        retentionService.purgeArtifacts(task);
        taskMapper.deleteById(id);
    }

    public Evidence loadEvidence(Long spaceId, Long id, String key) {
        requireTask(spaceId, id);
        String prefix = "patrol/" + spaceId + "/" + id + "/";
        if (key == null || key.isBlank() || key.contains("..") || !key.startsWith(prefix)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "证据不属于该任务");
        }
        String filename = key.substring(key.lastIndexOf('/') + 1);
        String contentType = switch (extension(filename)) {
            case "webm" -> "video/webm";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "json" -> "application/json; charset=utf-8";
            case "html" -> "text/html; charset=utf-8";
            default -> "application/octet-stream";
        };
        try {
            return new Evidence(objectStorageService.getObject(storageBucket, key), contentType, filename);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "证据不存在或已被清理");
        }
    }

    private boolean visionConfigured(PatrolTaskEntity task) {
        try {
            JsonNode judge = MAPPER.readTree(task.getModelSnapshotJson()).path("judge");
            return judge.path("vision").asBoolean(false) && !judge.path("modelId").asText("").isBlank();
        } catch (Exception exception) {
            return false;
        }
    }

    private PatrolTaskEntity requireTask(Long spaceId, Long id) {
        return taskMapper.findByIdAndSpaceId(id, spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "巡页任务不存在"));
    }

    private static String extension(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    public record Evidence(InputStream stream, String contentType, String filename) {
    }
}
