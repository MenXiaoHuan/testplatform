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