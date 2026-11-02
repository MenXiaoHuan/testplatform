package com.example.platform.patrol.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 巡页系统提示词加载器。
 *
 * <p>提示词以 Markdown 资源文件维护在 {@code classpath:patrol/prompts/} 下，启动时一次性加载并缓存，
 * 避免把提示词硬编码在 Java 代码里。资源缺失时启动失败，防止线上走到没有提示词的分支。
 */
@Component
public class PatrolPromptService {
    private static final String PROMPT_ROOT = "classpath:patrol/prompts/";

    private final Map<String, String> prompts;

    public PatrolPromptService(ResourceLoader resourceLoader) {
        Map<String, String> loaded = new LinkedHashMap<>();
        loaded.put("plan", read(resourceLoader, "plan-system.md"));
        loaded.put("supplement", read(resourceLoader, "supplement-system.md"));
        loaded.put("judge", read(resourceLoader, "judge-system.md"));
        this.prompts = Map.copyOf(loaded);
    }

    /** 用例规划系统提示词。 */
    public String plan() {
        return prompts.get("plan");
    }

    /** 覆盖率补全系统提示词。 */
    public String supplement() {
        return prompts.get("supplement");
    }

    /** 用例判定系统提示词。 */
    public String judge() {
        return prompts.get("judge");
    }

    private static String read(ResourceLoader resourceLoader, String filename) {
        Resource resource = resourceLoader.getResource(PROMPT_ROOT + filename);
        if (!resource.exists()) {
            throw new IllegalStateException("巡页提示词资源不存在: " + PROMPT_ROOT + filename);
        }
        try (InputStream input = resource.getInputStream()) {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
            if (content.isEmpty()) {
                throw new IllegalStateException("巡页提示词内容为空: " + PROMPT_ROOT + filename);
            }
            return content;
        } catch (IOException exception) {
            throw new IllegalStateException("读取巡页提示词失败: " + PROMPT_ROOT + filename, exception);
        }
    }
}
