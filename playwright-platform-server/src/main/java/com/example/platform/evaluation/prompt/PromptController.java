package com.example.platform.evaluation.prompt;

import com.example.platform.common.ApiResponse;
import com.example.platform.common.BusinessException;
import com.example.platform.evaluation.common.EvaluationErrorCode;
import com.example.platform.evaluation.common.PromptStatus;
import com.example.platform.evaluation.common.PromptType;
import com.example.platform.evaluation.exception.PromptNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Prompt 管理 REST Controller —— 提示词是独立资产，不绑定任何 Agent：
 * <ul>
 *   <li>平台内：建提示词 → 编辑版本 → 发布 ACTIVE → 废弃</li>
 *   <li>平台外：外部服务通过 Pull API（ETag 协商）拉取 ACTIVE 版本自行组装调用</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/evaluation/prompts")
public class PromptController {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$");
    private static final List<String> ROLES = List.of("system", "user", "assistant");

    private final PromptVersionService versionService;
    private final PromptPullService pullService;

    public PromptController(PromptVersionService versionService, PromptPullService pullService) {
        this.versionService = versionService;
        this.pullService = pullService;
    }

    // ================= 平台内管理 =================

    /** 当前 space 下所有提示词的聚合列表（左栏）。 */
    @GetMapping("/keys")
    public ApiResponse<List<PromptKeySummary>> keys(@RequestParam Long spaceId) {
        return ApiResponse.ok(versionService.listKeys(spaceId));
    }

    /** 新建提示词（创建第一个 DRAFT 版本）。 */
    @PostMapping
    public ApiResponse<EvalPromptVersionEntity> create(@RequestBody EvalPromptVersionEntity req) {
        validateCreate(req);
        return ApiResponse.ok(versionService.create(req));
    }

    /** 编辑 DRAFT 版本内容。 */
    @PutMapping("/versions/{versionId}")
    public ApiResponse<EvalPromptVersionEntity> updateDraft(
            @PathVariable Long versionId,
            @RequestParam Long spaceId,
            @RequestBody EvalPromptVersionEntity req) {
        validateContent(req, true);
        return ApiResponse.ok(versionService.updateDraft(versionId, spaceId, req));
    }

    /** 查询单个版本详情。 */
    @GetMapping("/versions/{versionId}")
    public ApiResponse<EvalPromptVersionEntity> detail(
            @PathVariable Long versionId, @RequestParam Long spaceId) {
        EvalPromptVersionEntity version = versionService.getById(versionId);
        requireSameSpace(version, spaceId);
        return ApiResponse.ok(version);
    }

    /** 删除任意状态的版本。 */
    @DeleteMapping("/versions/{versionId}")
    public ApiResponse<Void> deleteVersion(@PathVariable Long versionId, @RequestParam Long spaceId) {
        versionService.deleteVersion(versionId, spaceId);
        return ApiResponse.ok(null);
    }

    /** 删除整个提示词（包含全部版本）。 */
    @DeleteMapping("/keys/{promptKey}")
    public ApiResponse<Void> deletePromptGroup(@PathVariable String promptKey,
                                                @RequestParam Long spaceId) {
        versionService.deletePromptGroup(spaceId, promptKey);
        return ApiResponse.ok(null);
    }

    /** 任意状态之间流转：DRAFT / ACTIVE / DEPRECATED。 */
    @PutMapping("/{versionId}/status")
    public ApiResponse<Map<String, Object>> changeStatus(
            @PathVariable Long versionId,
            @RequestParam Long spaceId,
            @RequestParam String status) {
        EvalPromptVersionEntity v = versionService.changeStatus(versionId, spaceId, status);
        return ApiResponse.ok(Map.of("id", v.getId(), "status", v.getStatus()));
    }

    /** 发布任意状态 → ACTIVE。 */
    @PostMapping("/{versionId}/activate")
    public ApiResponse<Map<String, Object>> activate(
            @PathVariable Long versionId, @RequestParam Long spaceId) {
        EvalPromptVersionEntity v = versionService.activate(versionId, spaceId, "SYSTEM");
        return ApiResponse.ok(Map.of("id", v.getId(), "status", v.getStatus()));
    }

    /** 废弃任意状态 → DEPRECATED。 */
    @PostMapping("/{versionId}/deprecate")
    public ApiResponse<Void> deprecate(@PathVariable Long versionId, @RequestParam Long spaceId) {
        versionService.deprecate(versionId, spaceId);
        return ApiResponse.ok(null);
    }

    /** 查某 promptKey 的版本链。 */
    @GetMapping("/list")
    public ApiResponse<List<EvalPromptVersionEntity>> list(
            @RequestParam String promptKey, @RequestParam Long spaceId) {
        return ApiResponse.ok(versionService.listVersions(spaceId, promptKey));
    }

    // ================= 外部调用（Pull API） =================

    /**
     * 外部服务拉取 ACTIVE 版本；支持 If-None-Match 304 协商缓存。
     * 兼容旧路径 /{promptKey}，新接入建议使用 /active/{promptKey}。
     */
    @GetMapping({"/active/{promptKey}", "/{promptKey}"})
    public ResponseEntity<?> getActive(
            @PathVariable String promptKey,
            @RequestParam Long spaceId,
            @RequestHeader(value = "If-None-Match", required = false) String etag) {
        try {
            PromptPullService.PromptPullResult result = pullService.pullActive(promptKey, spaceId, etag);
            if (result.isOk()) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.ETAG, result.etag())
                        .body(Map.of(
                                "promptKey", promptKey,
                                "version", result.version().getVersionLabel(),
                                "status", result.version().getStatus(),
                                "template", result.version().getTemplate()
                        ));
            }
            return ResponseEntity.status(304).header(HttpHeaders.ETAG, result.etag()).build();
        } catch (PromptNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ================= 校验 =================

    private void validateCreate(EvalPromptVersionEntity req) {
        if (req.getSpaceId() == null) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "spaceId 不能为空");
        }
        // 新建提示词时由后端生成唯一 promptKey；新建版本时沿用已有 key
        if (req.getPromptKey() != null && !req.getPromptKey().isBlank()) {
            String key = req.getPromptKey().trim();
            if (!KEY_PATTERN.matcher(key).matches()) {
                throw new BusinessException(EvaluationErrorCode.BAD_REQUEST,
                        "promptKey 只允许字母/数字/下划线/中划线/点，且以字母或数字开头（最长 128）");
            }
            req.setPromptKey(key);
        }

        if (req.getVersionLabel() == null || req.getVersionLabel().isBlank()) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "versionLabel 不能为空");
        }
        req.setVersionLabel(req.getVersionLabel().trim());
        if (req.getVersionLabel().length() > 32) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "versionLabel 不能超过 32 字符");
        }

        req.setPromptType(parseType(req.getPromptType()));
        validateContent(req, true);
    }

    private void validateContent(EvalPromptVersionEntity req, boolean requireName) {
        if (requireName && (req.getName() == null || req.getName().isBlank())) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "提示词名称不能为空");
        }
        if (req.getName() != null && req.getName().length() > 256) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "提示词名称不能超过 256 字符");
        }
        if (req.getDescription() != null && req.getDescription().length() > 1024) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "描述不能超过 1024 字符");
        }

        if (req.getTemplate() == null || req.getTemplate().isBlank()) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "template 不能为空");
        }
        JsonNode messages;
        try {
            messages = JSON.readTree(req.getTemplate());
        } catch (Exception e) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST,
                    "template 不是合法 JSON: " + e.getMessage());
        }
        if (!messages.isArray() || messages.isEmpty()) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST,
                    "template 必须是至少一条消息的 JSON 数组");
        }
        for (JsonNode message : messages) {
            if (!message.isObject()) {
                throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "template 每条消息必须是 JSON 对象");
            }
            String role = message.path("role").asText("");
            String content = message.path("content").asText("");
            if (!ROLES.contains(role)) {
                throw new BusinessException(EvaluationErrorCode.BAD_REQUEST,
                        "消息 role 必须为 system / user / assistant，当前：" + role);
            }
            if (content.isBlank()) {
                throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "消息 content 不能为空");
            }
        }
        // 规范化 JSON
        try {
            req.setTemplate(JSON.writeValueAsString(messages));
        } catch (Exception e) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "template 序列化失败: " + e.getMessage());
        }

        String variables = req.getVariables();
        if (variables == null || variables.isBlank()) {
            req.setVariables("[]");
            return;
        }
        try {
            JsonNode vars = JSON.readTree(variables);
            if (!vars.isArray()) {
                throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "variables 必须是字符串数组");
            }
            for (JsonNode item : vars) {
                if (!item.isTextual()) {
                    throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "variables 只能包含字符串");
                }
            }
            req.setVariables(JSON.writeValueAsString(vars));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST,
                    "variables 不是合法 JSON: " + e.getMessage());
        }
    }

    private static String parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return PromptType.CUSTOM.name();
        }
        try {
            return PromptType.valueOf(raw.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST,
                    "不支持的 promptType: " + raw + "（可选 SYSTEM / JUDGING / EXTRACTION / CUSTOM）");
        }
    }

    private static void requireSameSpace(EvalPromptVersionEntity version, Long spaceId) {
        if (!version.getSpaceId().equals(spaceId)) {
            throw new BusinessException("PROMPT_NOT_FOUND", "跨 Space 访问被拒绝");
        }
    }
}
