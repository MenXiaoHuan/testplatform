package com.example.platform.ai.tools;

import com.example.platform.ai.skill.SkillIndexLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 让 LLM 按需加载某个技能的 SKILL.md 正文。
 *
 * <p>设计动机：技能索引（name + description）已在系统提示词中固定可见，
 * 但完整 SOP 正文不预加载到上下文，由 LLM 判断需要时调用本工具读取。</p>
 */
@Component
public class LoadSkillContentTool {

    private static final Logger log = LoggerFactory.getLogger(LoadSkillContentTool.class);

    private final SkillIndexLoader skillIndexLoader;

    public LoadSkillContentTool(SkillIndexLoader skillIndexLoader) {
        this.skillIndexLoader = skillIndexLoader;
    }

    @Tool(description = "Load the full content of a skill's SKILL.md by skill name. " +
            "Use this when you decide a specific skill is needed to answer the user's question. " +
            "Available skill names are listed in the system prompt's skill index section.")
    public String loadSkill(
            @ToolParam(description = "Skill name from the skill index, e.g. error-analysis or business-knowledge")
            String name) {
        log.info("AI tool: loadSkill, name={}", name);
        if (name == null || name.isBlank()) {
            return "Skill name is required.";
        }
        List<String> available = skillIndexLoader.getSkillNames();
        if (!available.contains(name)) {
            return "Skill not found: " + name + ". Available skills: " + String.join(", ", available);
        }
        String content = skillIndexLoader.readSkillContent(name);
        List<String> docs = skillIndexLoader.getDocumentsForSkill(name);
        StringBuilder sb = new StringBuilder();
        sb.append("# Skill: ").append(name).append("\n\n");
        sb.append(content);
        sb.append("\n\n## 该技能目录下可用文档\n");
        sb.append("如需加载以下子文档，调用 loadSkillDocument(skillName=\"").append(name)
                .append("\", docName=\"<文件名>\")：\n");
        for (String doc : docs) {
            sb.append("- ").append(doc).append("\n");
        }
        return sb.toString();
    }
}
