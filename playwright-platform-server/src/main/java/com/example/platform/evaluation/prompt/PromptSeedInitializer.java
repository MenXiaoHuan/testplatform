package com.example.platform.evaluation.prompt;

import com.example.platform.evaluation.common.PromptStatus;
import com.example.platform.evaluation.config.EvaluationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * C24 Seed —— 应用启动时若 eval_prompt_version 表为空，自动用
 * classpath:AGENT.md 的内容 seed 为 agent-system-prompt 的 DRAFT 版本。
 * 若同 promptKey 已有任意版本（含 DRAFT），则跳过，避免覆盖用户数据。
 */
@Component
public class PromptSeedInitializer {

    private static final Logger log = LoggerFactory.getLogger(PromptSeedInitializer.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SEED_PROMPT_KEY = "agent-system-prompt";
    private static final String SEED_NAME = "Agent 系统提示词";
    private static final String SEED_VERSION = "v0.1-seed";
    private static final String SEED_PATH = "AGENT.md";

    private final EvalPromptVersionMapper mapper;
    private final EvaluationProperties props;

    public PromptSeedInitializer(EvalPromptVersionMapper mapper, EvaluationProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!props.getPrompt().isSeedFromClasspathAgentMd()) {
            log.info("Prompt seed disabled via config, skip");
            return;
        }
        try {
            // 只要该 promptKey 有任意版本 → 跳过（不覆盖用户已有数据）
            Long spaceId = 1L; // seed 放到默认 space，后续按需调整
            int existing = mapper.countByPromptKey(spaceId, SEED_PROMPT_KEY);
            if (existing > 0) {
                log.info("Prompt seed skipped: promptKey={} already has {} versions", SEED_PROMPT_KEY, existing);
                return;
            }
            String content = loadClasspathContent(SEED_PATH);
            if (content == null || content.isBlank()) {
                log.warn("Prompt seed skipped: classpath:{} is empty", SEED_PATH);
                return;
            }
            EvalPromptVersionEntity seed = new EvalPromptVersionEntity();
            seed.setSpaceId(spaceId);
            seed.setPromptKey(SEED_PROMPT_KEY);
            seed.setName(SEED_NAME);
            seed.setPromptType("SYSTEM");
            // 用 Jackson 序列化，保证反斜杠/换行/引号等字符都合法转义（手写 %s 会拼坏 JSON）
            seed.setTemplate(JSON.writeValueAsString(
                    List.of(Map.of("role", "system", "content", content))));
            seed.setVersionLabel(SEED_VERSION);
            seed.setStatus(PromptStatus.DRAFT.name());
            seed.setCreatedBy("SYSTEM_SEED");
            mapper.insert(seed);
            log.info("Prompt seed created: promptKey={}, version={}, spaceId={}",
                    SEED_PROMPT_KEY, SEED_VERSION, spaceId);
        } catch (Exception e) {
            // seed 失败不影响启动
            log.warn("Prompt seed failed (non-blocking): {}", e.getMessage());
        }
    }

    private static String loadClasspathContent(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
