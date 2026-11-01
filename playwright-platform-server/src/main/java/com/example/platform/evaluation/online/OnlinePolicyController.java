package com.example.platform.evaluation.online;

import com.example.platform.common.ApiResponse;
import com.example.platform.common.BusinessException;
import com.example.platform.evaluation.common.EvaluationErrorCode;
import com.example.platform.evaluation.target.EvalTargetEntity;
import com.example.platform.evaluation.target.EvalTargetMapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** 在线评测策略 CRUD + 手动扫描。 */
@RestController
@RequestMapping("/api/evaluation/online/policies")
public class OnlinePolicyController {

    private final EvalOnlinePolicyMapper policyMapper;
    private final EvalTargetMapper targetMapper;
    private final OnlinePolicyScanner scanner;

    public OnlinePolicyController(EvalOnlinePolicyMapper policyMapper,
                                  EvalTargetMapper targetMapper,
                                  OnlinePolicyScanner scanner) {
        this.policyMapper = policyMapper;
        this.targetMapper = targetMapper;
        this.scanner = scanner;
    }

    @PostMapping
    public ApiResponse<EvalOnlinePolicyEntity> create(@RequestBody CreatePolicyRequest request) {
        if (request.spaceId() == null || request.targetId() == null) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "spaceId / targetId 不能为空");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "策略名称不能为空");
        }
        if (request.evaluatorConfigs() == null || request.evaluatorConfigs().isBlank()) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "evaluatorConfigs 不能为空");
        }
        EvalTargetEntity target = targetMapper.findById(request.targetId())
                .orElseThrow(() -> new BusinessException(
                        EvaluationErrorCode.TARGET_NOT_FOUND, "Target id=" + request.targetId() + " 不存在"));
        if (!target.getSpaceId().equals(request.spaceId())) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "跨 Space 访问被拒绝");
        }

        EvalOnlinePolicyEntity policy = new EvalOnlinePolicyEntity();
        policy.setSpaceId(request.spaceId());
        policy.setTargetId(request.targetId());
        policy.setName(request.name().trim());
        policy.setStatus("ACTIVE");
        policy.setFilterConfig(blankDefault(request.filterConfig(), "{\"lookbackHours\":24,\"limit\":20}"));
        policy.setEvaluatorConfigs(request.evaluatorConfigs());
        policy.setAlertingConfig(blankDefault(request.alertingConfig(), "{\"lowScoreThreshold\":60}"));
        policy.setRateLimitConfig(blankDefault(request.rateLimitConfig(), "{}"));
        policy.setScanIntervalMinutes(request.scanIntervalMinutes() != null
                && request.scanIntervalMinutes() > 0 ? request.scanIntervalMinutes() : 5);
        policy.setNextScanAt(LocalDateTime.now());
        policyMapper.insert(policy);
        return ApiResponse.ok(policyMapper.findById(policy.getId()).orElse(policy));
    }

    @GetMapping
    public ApiResponse<List<EvalOnlinePolicyEntity>> list(@RequestParam Long spaceId) {
        return ApiResponse.ok(policyMapper.listBySpace(spaceId));
    }

    @GetMapping("/{id}")
    public ApiResponse<EvalOnlinePolicyEntity> get(@PathVariable Long id,
                                                   @RequestParam Long spaceId) {
        return ApiResponse.ok(requirePolicy(id, spaceId));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @RequestParam Long spaceId,
                                          @RequestParam String status) {
        requirePolicy(id, spaceId);
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!"ACTIVE".equals(normalized) && !"PAUSED".equals(normalized)) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "status 只允许 ACTIVE / PAUSED");
        }
        policyMapper.updateStatus(id, normalized);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/scan")
    public ApiResponse<OnlinePolicyScanner.ScanResult> scan(@PathVariable Long id,
                                                            @RequestParam Long spaceId) {
        EvalOnlinePolicyEntity policy = requirePolicy(id, spaceId);
        return ApiResponse.ok(scanner.scanNow(policy));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Long spaceId) {
        requirePolicy(id, spaceId);
        policyMapper.deleteById(id);
        return ApiResponse.ok(null);
    }

    private EvalOnlinePolicyEntity requirePolicy(Long id, Long spaceId) {
        EvalOnlinePolicyEntity policy = policyMapper.findById(id)
                .orElseThrow(() -> new BusinessException(
                        EvaluationErrorCode.BAD_REQUEST, "策略 id=" + id + " 不存在"));
        if (!policy.getSpaceId().equals(spaceId)) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "跨 Space 访问被拒绝");
        }
        return policy;
    }

    private static String blankDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public record CreatePolicyRequest(
            Long spaceId,
            Long targetId,
            String name,
            String filterConfig,
            String evaluatorConfigs,
            String alertingConfig,
            String rateLimitConfig,
            Integer scanIntervalMinutes) {}
}
