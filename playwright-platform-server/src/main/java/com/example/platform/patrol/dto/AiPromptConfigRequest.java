package com.example.platform.patrol.dto;

/** 提示词配置请求：新建必须选择标签；同一空间同一标签只能有一条。 */
public record AiPromptConfigRequest(String label, String content) {
}
