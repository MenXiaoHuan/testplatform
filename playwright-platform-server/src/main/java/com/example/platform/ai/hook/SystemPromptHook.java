package com.example.platform.ai.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Prioritized;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesAgentHook;
import com.alibaba.cloud.ai.graph.serializer.AgentInstructionMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统提示词 Hook —— 在 ReactAgent 执行前注入系统提示词。
 *
 * <p>位置：{@link HookPosition#BEFORE_AGENT}，order=-200 保证最先执行。
 *
 * <p>作用：把 {@link com.example.platform.ai.config.SystemPromptConfig} 加载的 AGENT.md 内容
 * 作为指令消息追加到消息列表末尾，让 LLM 在每次推理前都能看到系统提示词。
 *
 * <p>构建器模式 {@link Builder} 便于在 {@code ReactAgentConfig} 中链式构造。
 */
@HookPositions(HookPosition.BEFORE_AGENT)
public class SystemPromptHook extends MessagesAgentHook implements Prioritized {

    private final String systemText;
    private ReactAgent reactAgent;

    public SystemPromptHook(String systemText) {
        this.systemText = systemText;
    }

    @Override
    public AgentCommand beforeAgent(List<Message> previousMessages, RunnableConfig config) {
        if (systemText == null || systemText.isBlank()) {
            return new AgentCommand(previousMessages);
        }
        AgentInstructionMessage instructionMessage = AgentInstructionMessage.builder().text(systemText).build();
        List<Message> newMessages = new ArrayList<>(previousMessages);
        newMessages.add(instructionMessage);
        return new AgentCommand(newMessages);
    }

    @Override
    public String getName() {
        return "SystemPromptHook";
    }

    @Override
    public int getOrder() {
        return -200;
    }

    @Override
    public ReactAgent getAgent() {
        return reactAgent;
    }

    @Override
    public void setAgent(ReactAgent agent) {
        this.reactAgent = agent;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String systemText;

        public Builder systemText(String systemText) {
            this.systemText = systemText;
            return this;
        }

        public SystemPromptHook build() {
            return new SystemPromptHook(systemText);
        }
    }
}