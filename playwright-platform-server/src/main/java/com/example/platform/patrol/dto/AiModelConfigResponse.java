package com.example.platform.patrol.dto;

import com.example.platform.patrol.model.AiModelConfigEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 模型配置响应：密钥仅返回掩码。 */
public record AiModelConfigResponse(
        Long id,
        Long spaceId,
        String name,
        String providerType,
        String baseUrl,
        String apiKeyMasked,
        String modelId,
        boolean supportsVision,
        boolean supportsTools,
        boolean supportsJson,
        int maxTokens,
        BigDecimal temperature,
        boolean enabled,
        String lastTestStatus,
        String lastTestMessage,
        LocalDateTime lastTestedAt,
        LocalDateTime createdAt
) {
    public static AiModelConfigResponse from(AiModelConfigEntity entity) {
        String masked = entity.getApiKeyEncrypted() == null || entity.getApiKeyEncrypted().isBlank()
                ? ""
                : "****" + entity.getApiKeyEncrypted().substring(Math.max(0, entity.getApiKeyEncrypted().length() - 4));
        return new AiModelConfigResponse(
                entity.getId(),
                entity.getSpaceId(),
                entity.getName(),
                entity.getProviderType(),
                entity.getBaseUrl(),
                masked,
                entity.getModelId(),
                Boolean.TRUE.equals(entity.getSupportsVision()),
                Boolean.TRUE.equals(entity.getSupportsTools()),
                Boolean.TRUE.equals(entity.getSupportsJson()),
                entity.getMaxTokens() == null ? 4096 : entity.getMaxTokens(),
                entity.getTemperature(),
                !Boolean.FALSE.equals(entity.getEnabled()),
                entity.getLastTestStatus(),
                entity.getLastTestMessage(),
                entity.getLastTestedAt(),
                entity.getCreatedAt());
    }
}
