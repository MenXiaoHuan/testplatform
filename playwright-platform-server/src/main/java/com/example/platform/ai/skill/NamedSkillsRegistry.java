package com.example.platform.ai.skill;

import com.alibaba.cloud.ai.graph.skills.SkillMetadata;
import com.alibaba.cloud.ai.graph.skills.registry.AbstractSkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class NamedSkillsRegistry extends AbstractSkillRegistry {

    private final SkillRegistry delegate;
    private final List<String> allowedSkillNames;

    public NamedSkillsRegistry(SkillRegistry delegate, List<String> skillNames) {
        this.delegate = delegate;
        this.allowedSkillNames = skillNames;
        this.skills = new HashMap<>();
        loadSkillsToRegistry();
    }

    @Override
    protected void loadSkillsToRegistry() {
        for (String name : allowedSkillNames) {
            delegate.get(name).ifPresent(skill -> skills.put(name, skill));
        }
    }

    @Override
    public String readSkillContent(String name) throws IOException {
        return delegate.readSkillContent(name);
    }

    @Override
    public String getSkillLoadInstructions() {
        return delegate.getSkillLoadInstructions();
    }

    @Override
    public String getRegistryType() {
        return "named";
    }

    @Override
    public SystemPromptTemplate getSystemPromptTemplate() {
        return delegate.getSystemPromptTemplate();
    }
}
