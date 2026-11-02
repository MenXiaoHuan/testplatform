package com.example.platform.patrol.dto;

import java.math.BigDecimal;

/** 模型配置请求：apiKey 为空表示更新时保留原密钥。 */
public record AiModelConfigRequest(
        String name,
        String providerType,
        String baseUrl,
        String apiKey,
        String modelId,
        Boolean supportsVision,
        Boolean supportsTools,
        Boolean supportsJson,
        Integer maxTokens,
        BigDecimal temperature,
        Boolean enabled
) {
}
