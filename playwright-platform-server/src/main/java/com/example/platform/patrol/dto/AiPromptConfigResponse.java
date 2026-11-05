package com.example.platform.patrol.dto;

import java.time.LocalDateTime;

/**
 * 提示词配置视图。
 *
 * @param content        当前生效的自定义内容；未配置时等于内置默认
 * @param defaultContent 内置默认内容
 * @param custom         是否已配置空间级覆盖
 */
public record AiPromptConfigResponse(Long id, String label, String labelName,
                                     String content, String defaultContent, boolean custom,
                                     String updatedBy, LocalDateTime updatedAt) {
}
