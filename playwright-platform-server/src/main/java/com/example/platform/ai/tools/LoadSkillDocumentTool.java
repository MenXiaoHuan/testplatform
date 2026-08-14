package com.example.platform.ai.tools;

import com.example.platform.ai.skill.SkillIndexLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 让 LLM 按需加载某技能目录下的子文档（如 error-analysis/playwright-error.md）。
 *
 * <p>设计动机：技能 SOP 在 SKILL.md 里通常只给出路由/决策树，遇到具体子场景时
 * 才需要查阅子文档的详细排查步骤。延迟加载可显著节省 token。</p>
 */
@Component
public class LoadSkillDocumentTool {

    private static final Logger log = LoggerFactory.getLogger(LoadSkillDocumentTool.class);

    private final SkillIndexLoader skillIndexLoader;

    public LoadSkillDocumentTool(SkillIndexLoader skillIndexLoader) {
        this.skillIndexLoader = skillIndexLoader;
    }

    @Tool(description = "Load a sub-document of a skill by skill name and document file name. " +
            "Use this when the SKILL.md routes you to a specific sub-document for detailed SOP. " +
            "docName is the file name like 'playwright-error.md'.")
    public String loadSkillDocument(
            @ToolParam(description = "Skill name, e.g. error-analysis")
            String skillName,
            @ToolParam(description = "Document file name, e.g. playwright-error.md")
            String docName) {
        log.info("AI tool: loadSkillDocument, skillName={}, docName={}", skillName, docName);
        if (skillName == null || skillName.isBlank() || docName == null || docName.isBlank()) {
            return "Both skillName and docName are required.";
        }
        List<String> available = skillIndexLoader.getSkillNames();
        if (!available.contains(skillName)) {
            return "Skill not found: " + skillName + ". Available: " + String.join(", ", available);
        }
        List<String> docs = skillIndexLoader.getDocumentsForSkill(skillName);
        if (!docs.contains(docName)) {
            return "Document not found in skill " + skillName + ": " + docName +
                    ". Available docs: " + String.join(", ", docs);
        }
        return skillIndexLoader.readDocContent(skillName, docName);
    }
}
