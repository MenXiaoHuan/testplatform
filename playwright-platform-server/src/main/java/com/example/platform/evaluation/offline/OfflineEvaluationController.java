package com.example.platform.evaluation.offline;

import com.example.platform.common.ApiResponse;
import com.example.platform.common.ApiErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 离线评测 Run REST Controller —— 创建/查询/取消 + 进度 SSE。 */
@RestController
@RequestMapping("/api/evaluation/runs")
public class OfflineEvaluationController {

    private final OfflineEvaluationOrchestrator orchestrator;
    private final EvalRunMapper runMapper;
    private final EvalRunCaseResultMapper caseResultMapper;

    public OfflineEvaluationController(OfflineEvaluationOrchestrator orchestrator,
                                       EvalRunMapper runMapper,
                                       EvalRunCaseResultMapper caseResultMapper) {
        this.orchestrator = orchestrator;
        this.runMapper = runMapper;
        this.caseResultMapper = caseResultMapper;
    }

    @PostMapping
    public ApiResponse<EvalRunEntity> start(@RequestBody OfflineEvaluationOrchestrator.StartRunRequest req) {
        return ApiResponse.ok(orchestrator.startRun(req));
    }

    @GetMapping("/{id}")
    public ApiResponse<EvalRunEntity> get(@PathVariable Long id) {
        return runMapper.findById(id)
                .map(ApiResponse::ok)
                .orElseThrow(() -> new com.example.platform.common.BusinessException(
                        "BAD_REQUEST", "Run id=" + id + " 不存在"));
    }

    @GetMapping
    public ApiResponse<java.util.List<EvalRunEntity>> list(@RequestParam Long spaceId) {
        return ApiResponse.ok(runMapper.listBySpace(spaceId));
    }

    /** 样本结果列表 —— 报告页据此展示每条样本的输出、耗时、Trace 链路。 */
    @GetMapping("/{id}/results")
    public ApiResponse<java.util.List<EvalRunCaseResultEntity>> results(@PathVariable Long id,
                                                                        @RequestParam Long spaceId) {
        EvalRunEntity run = runMapper.findById(id)
                .orElseThrow(() -> new com.example.platform.common.BusinessException(
                        "BAD_REQUEST", "Run id=" + id + " 不存在"));
        if (!run.getSpaceId().equals(spaceId)) {
            throw new com.example.platform.common.BusinessException(
                    "BAD_REQUEST", "跨 Space 访问被拒绝");
        }
        return ApiResponse.ok(caseResultMapper.listByRun(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        runMapper.forceStatusUpdate(id, "CANCELLED", 0, 0, null);
        return ApiResponse.ok(null);
    }

    /** 删除已结束的 Run 及其样本结果；执行中的 Run 需等待结束后再删除。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Long spaceId) {
        EvalRunEntity run = runMapper.findById(id)
                .orElseThrow(() -> new com.example.platform.common.BusinessException(
                        "BAD_REQUEST", "Run id=" + id + " 不存在"));
        if (!run.getSpaceId().equals(spaceId)) {
            throw new com.example.platform.common.BusinessException(
                    "BAD_REQUEST", "跨 Space 访问被拒绝");
        }
        if ("RUNNING".equals(run.getStatus()) || "PENDING".equals(run.getStatus())) {
            throw new com.example.platform.common.BusinessException(
                    "BAD_REQUEST", "任务执行中，请等待结束后再删除");
        }
        caseResultMapper.deleteByRunId(id);
        runMapper.deleteByIdAndSpace(id, spaceId);
        return ApiResponse.ok(null);
    }

    /** 进度 SSE —— 后续实现推送；当前返回一个事件占位。 */
    @GetMapping(value = "/{id}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<String> progress(@PathVariable Long id) {
        EvalRunEntity run = runMapper.findById(id).orElse(null);
        if (run == null) return ResponseEntity.notFound().build();
        String event = """
                event: progress
                data: {"runId": %d, "status": "%s", "completed": %d, "failed": %d, "total": %d}

                """.formatted(run.getId(), run.getStatus(),
                run.getCompletedSamples(), run.getFailedSamples(), run.getTotalSamples());
        return ResponseEntity.ok().body(event);
    }
}
