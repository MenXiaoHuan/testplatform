package com.example.platform.ai.dto;

import java.util.List;
import java.util.Map;

/**
 * AI 对话响应 DTO（同步接口 /api/ai/chat 返回）。
 *
 * @param traceId            链路追踪 ID，可用于查询完整执行链路
 * @param response           助手回答的纯文本（由 sections 派生）
 * @param usedTools          本次对话调用的工具名列表
 * @param confidence         置信度 LOW/MEDIUM/HIGH
 * @param responseType       响应类型（如 ROOT_CAUSE/SUMMARY/UNKNOWN）
 * @param faultDetail        故障诊断详情（仅故障分析类响应非空）
 * @param taskId/sceneId     回声字段，原样返回请求中的关联 ID
 * @param processingTime     处理耗时描述
 * @param sessionId          会话 ID
 * @param contextCompressed  本次对话是否触发了上下文压缩
 */
public record ChatResponse(
        String traceId,
        String response,
        List<String> usedTools,
        String confidence,
        String responseType,
        Map<String, Object> faultDetail,
        Long taskId,
        Long sceneId,
        String processingTime,
        String sessionId,
        boolean contextCompressed
) {
    public ChatResponse {
        if (usedTools == null) {
            usedTools = List.of();
        }
        if (confidence == null) {
            confidence = "MEDIUM";
        }
        if (sessionId == null) {
            sessionId = "";
        }
        if (responseType == null) {
            responseType = "UNKNOWN";
        }
    }

    public ChatResponse(String traceId, String response, List<String> usedTools, String confidence,
                        String responseType, Long taskId, Long sceneId, String processingTime,
                        String sessionId, boolean contextCompressed) {
        this(traceId, response, usedTools, confidence, responseType, null,
                taskId, sceneId, processingTime, sessionId, contextCompressed);
    }
}
