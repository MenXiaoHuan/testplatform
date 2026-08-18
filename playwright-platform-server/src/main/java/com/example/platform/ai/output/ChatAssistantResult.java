package com.example.platform.ai.output;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Agent 输出结构 —— 模型期望输出的 JSON 顶层结构（对应系统提示词中约定的格式）。
 *
 * <p>字段：
 * <ul>
 *   <li>{@code traceId}        —— 链路追踪 ID，空则自动生成 UUID</li>
 *   <li>{@code usedTools}      —— Agent 调用的工具名列表</li>
 *   <li>{@code confidence}     —— 置信度 LOW/MEDIUM/HIGH</li>
 *   <li>{@code responseType}   —— 响应类型（ROOT_CAUSE/SUMMARY/UNKNOWN 等）</li>
 *   <li>{@code faultDetail}   —— 故障诊断详情，仅故障分析类响应非空</li>
 *   <li>{@code sections}      —— 结构化内容块数组，前端按块渲染</li>
 * </ul>
 *
 * <p>{@link #deriveResponse()} 把 sections 拼成 markdown 纯文本（用于同步接口返回）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatAssistantResult(
        String traceId,
        List<String> usedTools,
        String confidence,
        String responseType,
        FaultDetail faultDetail,
        List<ContentBlock> sections
) {
    public ChatAssistantResult {
        if (traceId == null || traceId.isBlank()) {
            traceId = java.util.UUID.randomUUID().toString();
        }
        if (usedTools == null) {
            usedTools = List.of();
        }
        if (confidence == null) {
            confidence = "MEDIUM";
        }
        if (responseType == null || responseType.isBlank()) {
            responseType = "UNKNOWN";
        }
        if (sections == null) {
            sections = List.of();
        }
    }

    public ChatAssistantResult(List<String> usedTools, String confidence) {
        this(null, usedTools, confidence, null, null, null);
    }

    public ChatAssistantResult(List<String> usedTools, String confidence, String responseType, FaultDetail faultDetail) {
        this(null, usedTools, confidence, responseType, faultDetail, null);
    }

    public ChatAssistantResult(List<String> usedTools, String confidence, String responseType, FaultDetail faultDetail, List<ContentBlock> sections) {
        this(null, usedTools, confidence, responseType, faultDetail, sections);
    }

    public String deriveResponse() {
        return OutputFormatFallbackService.deriveTextFromSections(sections);
    }

    public record FaultDetail(
            String fault_type,
            String root_cause,
            String immediate_solution,
            String long_term_optimize,
            String test_risk,
            String reproduce_steps
    ) {
        public FaultDetail {
            if (fault_type == null) fault_type = "";
            if (root_cause == null) root_cause = "";
            if (immediate_solution == null) immediate_solution = "";
            if (long_term_optimize == null) long_term_optimize = "";
            if (test_risk == null) test_risk = "";
            if (reproduce_steps == null) reproduce_steps = "无";
        }
    }
}
