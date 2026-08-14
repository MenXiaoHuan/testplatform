package com.example.platform.ai.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.example.platform.ai.hook.SystemPromptHook;
import com.example.platform.ai.output.ChatAssistantResult;
import com.example.platform.ai.skill.SkillIndexLoader;
import com.example.platform.ai.tools.LoadSkillContentTool;
import com.example.platform.ai.tools.LoadSkillDocumentTool;
import com.example.platform.ai.tools.LogPreprocessingTool;
import com.example.platform.ai.tools.RepositoryTool;
import com.example.platform.ai.tools.SceneTool;
import com.example.platform.ai.tools.TaskTool;
import com.example.platform.ai.tools.TraceQueryTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class ReactAgentConfig {

    private static final Logger log = LoggerFactory.getLogger(ReactAgentConfig.class);

    @Value("${platform.ai.system-prompt-path:classpath:AGENT.md}")
    private String systemPromptPath;

    @Bean("intelligent-assistant")
    public ReactAgent intelligentAssistantAgent(
            ChatModel model,
            RepositoryTool repositoryTool,
            SceneTool sceneTool,
            TaskTool taskTool,
            LogPreprocessingTool logPreprocessingTool,
            TraceQueryTool traceQueryTool,
            LoadSkillContentTool loadSkillContentTool,
            LoadSkillDocumentTool loadSkillDocumentTool,
            ResourceLoader resourceLoader,
            SystemPromptConfig systemPromptConfig,
            SkillIndexLoader skillIndexLoader) {

        log.info("Building intelligent-assistant ReactAgent (on-demand skill loading)");

        String basePrompt = systemPromptConfig.loadSystemPrompt(resourceLoader, systemPromptPath);
        // 在系统提示词末尾追加 skill 索引（仅 name+description，不包含正文）
        String systemPrompt = basePrompt + skillIndexLoader.getIndexText();

        return ReactAgent.builder()
                .name("intelligent-assistant")
                .description("智能测试平台助手，可以回答项目相关业务问题，也可以根据任务ID排查错误根因")
                .model(model)
                .methodTools(repositoryTool, sceneTool, taskTool, logPreprocessingTool, traceQueryTool,
                        loadSkillContentTool, loadSkillDocumentTool)
                .outputType(ChatAssistantResult.class)
                .hooks(SystemPromptHook.builder().systemText(systemPrompt).build())
                .hooks(ModelCallLimitHook.builder().runLimit(20).build())
                .build();
    }
}
