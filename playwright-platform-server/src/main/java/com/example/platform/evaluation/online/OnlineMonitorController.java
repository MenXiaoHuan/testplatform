package com.example.platform.evaluation.online;

import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.common.ApiResponse;
import com.example.platform.evaluation.common.TargetType;
import com.example.platform.evaluation.langfuse.LangfuseMetricsResult;
import com.example.platform.evaluation.langfuse.LangfuseClient;
import com.example.platform.evaluation.langfuse.MetricsQuery;
import com.example.platform.evaluation.target.EvalTargetEntity;
import com.example.platform.evaluation.target.EvalTargetMapper;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/** 在线监控大盘 REST Controller —— P50/P95/Token/评分分布。 */
@RestController
@RequestMapping("/api/evaluation/online/monitor")
public class OnlineMonitorController {

    private static final String SOURCE_BUILTIN_AGENT = "builtin_agent";

    private final LangfuseClient langfuse;
    private final EvalTargetMapper targetMapper;

    public OnlineMonitorController(LangfuseClient langfuse, EvalTargetMapper targetMapper) {
        this.langfuse = langfuse;
        this.targetMapper = targetMapper;
    }

    @GetMapping("/metrics")
    public ApiResponse<LangfuseMetricsResult> metrics(
            @RequestParam String targetId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "HOUR") MetricsQuery.Granularity granularity) {
        return ApiResponse.ok(langfuse.aggregateMetrics(
                new MetricsQuery(resolveTraceTargetId(targetId), from, to, granularity)));
    }

    /** 内置 Agent 的 Trace 统一按 target_id=builtin_agent 归档。 */
    private String resolveTraceTargetId(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return targetId;
        }
        String normalized = targetId.trim();
        if (SOURCE_BUILTIN_AGENT.equalsIgnoreCase(normalized)) {
            requireSuperAdmin();
            return SOURCE_BUILTIN_AGENT;
        }
        try {
            EvalTargetEntity target = targetMapper.findById(Long.valueOf(normalized)).orElse(null);
            if (target != null && TargetType.BUILTIN_AGENT.name().equalsIgnoreCase(target.getTargetType())) {
                requireSuperAdmin();
                return SOURCE_BUILTIN_AGENT;
            }
        } catch (NumberFormatException ignored) {
            // 非数字 targetId 按原值查询
        }
        return normalized;
    }

    private void requireSuperAdmin() {
        var context = AuthContextHolder.get();
        if (context == null || !context.superAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "super admin required");
        }
    }
}
