package com.example.platform.evaluation.online;

import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.common.ApiResponse;
import com.example.platform.evaluation.common.TargetType;
import com.example.platform.evaluation.langfuse.LangfuseClient;
import com.example.platform.evaluation.langfuse.LangfuseTrace;
import com.example.platform.evaluation.langfuse.LangfuseTraceDetail;
import com.example.platform.evaluation.langfuse.LangfuseTraceFilter;
import com.example.platform.evaluation.target.EvalTargetEntity;
import com.example.platform.evaluation.target.EvalTargetMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * 在线监控 Trace 浏览器 —— 按 Agent（target_id 标签）或 traceId 查询 trace 列表与完整链路详情。
 */
@RestController
@RequestMapping("/api/evaluation/online")
public class OnlineTraceController {

    /** 内置 Agent 的 trace 统一打上该来源标签。 */
    private static final String SOURCE_BUILTIN_AGENT = "builtin_agent";

    private final LangfuseClient langfuse;
    private final EvalTargetMapper targetMapper;

    public OnlineTraceController(LangfuseClient langfuse, EvalTargetMapper targetMapper) {
        this.langfuse = langfuse;
        this.targetMapper = targetMapper;
    }

    /** Trace 列表；targetId 转成 Langfuse tag target_id=<id>，traceId 优先精确查询单条。 */
    @GetMapping("/traces")
    public ApiResponse<TracePageResponse> traces(
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);

        String normalizedTraceId = blankToNull(traceId);
        if (normalizedTraceId != null) {
            LangfuseTraceDetail detail = langfuse.getTraceDetail(normalizedTraceId).orElse(null);
            if (detail == null) {
                return ApiResponse.ok(new TracePageResponse(List.of(), 0, safePage, safeSize));
            }
            if (isBuiltinAgentTrace(detail.trace())) {
                requireSuperAdmin();
            }
            return ApiResponse.ok(new TracePageResponse(List.of(detail.trace()), 1, safePage, safeSize));
        }

        String normalizedTargetId = resolveTraceTargetId(blankToNull(targetId));
        String normalizedStatus = status == null ? "" : status.trim().toLowerCase();
        String normalizedCategory = category == null ? "" : category.trim().toLowerCase();

        // 类别筛选：在线 / 离线对应 Langfuse source 标签；全部时沿用默认可见性策略
        String sourceTag = null;
        if ("online".equals(normalizedCategory)) {
            sourceTag = "source=online";
        } else if ("offline".equals(normalizedCategory)) {
            sourceTag = "source=offline";
        } else if ("builtin".equals(normalizedCategory) || "builtin_agent".equals(normalizedCategory)) {
            // 内部 Agent 数据仅超管可见
            requireSuperAdmin();
            sourceTag = "source=builtin_agent";
        } else if (normalizedTargetId == null && !isSuperAdmin()) {
            // 未指定 Agent 时默认展示全部 Trace；非超管仅看外部 Agent 线上 Trace，避免内置 Agent 数据泄露
            sourceTag = "source=online";
        }

        LangfuseTraceFilter filter = LangfuseTraceFilter.builder()
                .targetId(normalizedTargetId)
                .startTimeFrom(from)
                .startTimeTo(to)
                .onlyFailed("failed".equals(normalizedStatus))
                .onlySuccess("success".equals(normalizedStatus))
                .tags(sourceTag == null ? null : List.of(sourceTag))
                .build();

        Page<LangfuseTrace> result = langfuse.queryTraces(filter, PageRequest.of(safePage - 1, safeSize));
        return ApiResponse.ok(new TracePageResponse(
                result.getContent(), result.getTotalElements(), safePage, safeSize));
    }

    /** Trace 详情：Trace 头部 + 完整 Observation 链路（span 树 / 中间步骤）。 */
    @GetMapping("/traces/{traceId}")
    public ApiResponse<LangfuseTraceDetail> detail(@PathVariable String traceId) {
        LangfuseTraceDetail detail = langfuse.getTraceDetail(traceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Trace 不存在或 Langfuse 不可用"));
        if (isBuiltinAgentTrace(detail.trace())) {
            requireSuperAdmin();
        }
        return ApiResponse.ok(detail);
    }

    /** 是否为平台超管（无登录上下文时视为否）。 */
    private boolean isSuperAdmin() {
        var context = AuthContextHolder.get();
        return context != null && context.superAdmin();
    }

    private void requireSuperAdmin() {
        if (!isSuperAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "super admin required");
        }
    }

    /** 内置 Agent 的 Trace 统一按 target_id=builtin_agent 归档；其它 target 原样使用。 */
    private String resolveTraceTargetId(String targetId) {
        if (targetId == null) {
            return null;
        }
        if (SOURCE_BUILTIN_AGENT.equalsIgnoreCase(targetId)) {
            requireSuperAdmin();
            return SOURCE_BUILTIN_AGENT;
        }
        try {
            EvalTargetEntity target = targetMapper.findById(Long.valueOf(targetId)).orElse(null);
            if (target != null && TargetType.BUILTIN_AGENT.name().equalsIgnoreCase(target.getTargetType())) {
                requireSuperAdmin();
                return SOURCE_BUILTIN_AGENT;
            }
        } catch (NumberFormatException ignored) {
            // 非数字 targetId（外部 Agent 的字符串标识）按原值查询
        }
        return targetId;
    }

    private static boolean isBuiltinAgentTrace(LangfuseTrace trace) {
        return trace != null
                && trace.tags() != null
                && trace.tags().contains("source=" + SOURCE_BUILTIN_AGENT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
