package com.example.platform.patrol.dto;

/** 模型连接测试结果。 */
public record AiModelTestResponse(boolean success, String message, long elapsedMs) {
}
