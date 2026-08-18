package com.example.platform.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 系统提示词加载器 —— 从 classpath 加载 AGENT.md 作为系统提示词文本。
 *
 * <p>调用方：{@link ReactAgentConfig} 在构建 Agent 时调用 {@link #loadSystemPrompt}。
 * 文件不存在或读取失败时返回空串并打印警告/错误日志。
 */
@Component
public class SystemPromptConfig {

    private static final Logger log = LoggerFactory.getLogger(SystemPromptConfig.class);

    public String loadSystemPrompt(ResourceLoader resourceLoader, String systemPromptPath) {
        try {
            Resource resource = resourceLoader.getResource(systemPromptPath);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    log.info("Loaded system prompt from {} ({} chars)", systemPromptPath, content.length());
                    return content;
                }
            } else {
                log.warn("System prompt file not found: {}", systemPromptPath);
                return "";
            }
        } catch (IOException e) {
            log.error("Failed to load system prompt from {}", systemPromptPath, e);
            return "";
        }
    }
}