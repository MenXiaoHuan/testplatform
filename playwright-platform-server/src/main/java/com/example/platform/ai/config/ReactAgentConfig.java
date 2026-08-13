package com.example.platform.ai.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import com.example.platform.ai.output.ChatAssistantResult;
import com.example.platform.ai.skill.NamedSkillsRegistry;
import com.example.platform.ai.tools.LogPreprocessingTool;
import com.example.platform.ai.tools.RepositoryTool;
import com.example.platform.ai.tools.SceneTool;
import com.example.platform.ai.tools.TaskTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ReactAgentConfig {

    private static final Logger log = LoggerFactory.getLogger(ReactAgentConfig.class);

    @Bean
    public SkillRegistry rootSkillRegistry() {
        log.info("Loading skills from classpath:skills");
        return ClasspathSkillRegistry.builder()
                .classpathPath("skills")
                .build();
    }

    @Bean("intelligent-assistant")
    public ReactAgent intelligentAssistantAgent(
            ChatModel model,
            SkillRegistry rootSkillRegistry,
            RepositoryTool repositoryTool,
            SceneTool sceneTool,
            TaskTool taskTool,
            LogPreprocessingTool logPreprocessingTool) {

        log.info("Building intelligent-assistant ReactAgent");

        SkillRegistry agentRegistry = new NamedSkillsRegistry(
                rootSkillRegistry,
                List.of("error-analysis", "business-knowledge")
        );

        return ReactAgent.builder()
                .name("intelligent-assistant")
                .description("智能测试平台助手，可以回答项目相关业务问题，也可以根据任务ID排查错误根因")
                .model(model)
                .methodTools(repositoryTool, sceneTool, taskTool, logPreprocessingTool)
                .outputType(ChatAssistantResult.class)
                .hooks(SkillsAgentHook.builder().skillRegistry(agentRegistry).build())
                .hooks(ModelCallLimitHook.builder().runLimit(20).build())
                .build();
    }
}
