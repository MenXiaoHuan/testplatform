package com.example.platform.patrol.service;

import com.example.platform.common.PageResponse;
import com.example.platform.patrol.dto.PatrolApprovalRequest;
import com.example.platform.patrol.dto.PatrolCaseResponse;
import com.example.platform.patrol.dto.PatrolCaseStepResponse;
import com.example.platform.patrol.dto.PatrolModelLogResponse;
import com.example.platform.patrol.dto.PatrolReportResponse;
import com.example.platform.patrol.dto.PatrolTaskDetailResponse;
import com.example.platform.patrol.dto.PatrolTaskResponse;
import com.example.platform.patrol.mapper.PatrolCaseMapper;
import com.example.platform.patrol.mapper.PatrolCaseStepMapper;
import com.example.platform.patrol.mapper.PatrolTaskMapper;
import com.example.platform.patrol.model.PatrolCaseEntity;
import com.example.platform.patrol.model.PatrolCaseStepEntity;
import com.example.platform.patrol.model.PatrolTaskEntity;
import com.example.platform.storage.service.ObjectStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 巡页任务查询、审批、取消、删除与证据读取。 */
@Service
public class PatrolTaskService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ACTIVE_STATUSES = Set.of("QUEUED", "PREPARING", "SNAPSHOTTING",
            "PLANNING", "WAITING_APPROVAL", "EXECUTING", "JUDGING");

    private final PatrolTaskMapper taskMapper;
    private final PatrolCaseMapper caseMapper;
    private final PatrolCaseStepMapper caseStepMapper;
    private final PatrolCoverageService coverageService;
    private final PatrolExecutionService executionService;
    private final PatrolRetentionService retentionService;
    private final PatrolModelLogService modelLogService;
    private final ObjectStorageService objectStorageService;
    private final String storageBucket;

    public PatrolTaskService(PatrolTaskMapper taskMapper,
                             PatrolCaseMapper caseMapper,
                             PatrolCaseStepMapper caseStepMapper,
                             PatrolCoverageService coverageService,
                             PatrolExecutionService executionService,
                             PatrolRetentionService retentionService,
                             PatrolModelLogService modelLogService,
                             ObjectStorageService objectStorageService,
                             @Value("${platform.storage.bucket}") String storageBucket) {
        this.taskMapper = taskMapper;
        this.caseMapper = caseMapper;
        this.caseStepMapper = caseStepMapper;
        this.coverageService = coverageService;
        this.executionService = executionService;
        this.retentionService = retentionService;
        this.modelLogService = modelLogService;
        this.objectStorageService = objectStorageService;
        this.storageBucket = storageBucket;
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
        requireTask(spaceId, id);
        return modelLogService.list(id);
    }

    public PatrolTaskDetailResponse detail(Long spaceId, Long id) {
        PatrolTaskEntity task = requireTask(spaceId, id);
        return new PatrolTaskDetailResponse(
                PatrolTaskResponse.from(task),
                task.getSnapshotJson(),
                task.getSnapshotScreenshotKey(),
                task.getModelSnapshotJson(),
                listCases(spaceId, id),
                report(spaceId, id));
    }

    public List<PatrolCaseResponse> listCases(Long spaceId, Long id) {
        PatrolTaskEntity task = requireTask(spaceId, id);
        List<PatrolCaseEntity> cases = caseMapper.findByTaskId(task.getId());
        Map<Long, List<PatrolCaseStepEntity>> steps = caseStepMapper.findByTaskId(task.getId()).stream()
                .collect(Collectors.groupingBy(PatrolCaseStepEntity::getCaseId));
        return cases.stream().map(caseEntity -> PatrolCaseResponse.from(caseEntity,
                steps.getOrDefault(caseEntity.getId(), List.of()).stream()
                        .map(PatrolCaseStepResponse::from).toList())).toList();
    }

    public PatrolReportResponse report(Long spaceId, Long id) {
        PatrolTaskEntity task = requireTask(spaceId, id);
        List<PatrolCaseEntity> cases = caseMapper.findByTaskId(task.getId());
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
                ? cases.stream().filter(item -> !"READ".equals(item.getWorstRisk()))
                .map(PatrolCaseEntity::getId).toList()
                : List.of();
        PatrolCoverage.Stats stats = coverageService.measure(task);
        PatrolReportResponse.CoverageStats coverage = new PatrolReportResponse.CoverageStats(
                stats.total(), stats.covered(), stats.rate());
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
                        task.getLlmTokenUsed() == null ? 0 : task.getLlmTokenUsed(),
                        task.getBudgetLlmTokens() == null ? 300000 : task.getBudgetLlmTokens()),
                pendingApproval);
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
        task.setResultMessage(allowWrite || allowDestructive ? "审批通过，开始执行" : "未授权写操作，仅执行只读用例");
        taskMapper.update(task);
        executionService.resume(id);
        return PatrolTaskResponse.from(requireTask(spaceId, id));
    }

    public void cancel(Long spaceId, Long id) {
        PatrolTaskEntity task = requireTask(spaceId, id);
        if (!ACTIVE_STATUSES.contains(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仅排队中或执行中的任务可以取消");
        }
        executionService.requestCancel(id);
    }

    public void delete(Long spaceId, Long id) {
        PatrolTaskEntity task = requireTask(spaceId, id);
        if (ACTIVE_STATUSES.contains(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务执行中，无法删除");
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
