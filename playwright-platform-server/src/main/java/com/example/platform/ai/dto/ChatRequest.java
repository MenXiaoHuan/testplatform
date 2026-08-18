package com.example.platform.ai.dto;

/**
 * AI 对话请求 DTO。
 *
 * @param sessionId   会话 ID，空则自动生成 UUID
 * @param message     用户消息文本
 * @param taskId      关联的任务 ID（用于让 Agent 查询任务上下文，可空）
 * @param sceneId     关联的场景 ID（可空）
 * @param spaceId     空间 ID（必填，用于数据隔离）
 * @param saveHistory 是否把本次消息持久化到会话历史，空则默认 false
 */
public record ChatRequest(
        String sessionId,
        String message,
        Long taskId,
        Long sceneId,
        Long spaceId,
        Boolean saveHistory
) {
    public ChatRequest {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = java.util.UUID.randomUUID().toString();
        }
        if (saveHistory == null) {
            saveHistory = false;
        }
    }
}