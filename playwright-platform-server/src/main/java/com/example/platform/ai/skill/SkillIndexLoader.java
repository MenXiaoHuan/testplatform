package com.example.platform.ai.skill;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫描SKILL.md文件，解析 YAML frontmatter（name + description），
 * 生成「技能索引」文本用于追加到系统提示词末尾。
 *
 * <p>设计动机：避免把所有 skill 全文塞入上下文，只让 LLM 看到技能清单，
 * 由 LLM 决定何时调用 {@code loadSkill} 工具按需加载某个 skill 的正文。</p>
 */
@Component
public class SkillIndexLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillIndexLoader.class);

    private static final String SKILL_PATTERN = "classpath:skills/*/SKILL.md";
    private static final String SKILL_DOC_PATTERN = "classpath:skills/*/*.md";

    private final ResourcePatternResolver resourcePatternResolver;
    private final ResourceLoader resourceLoader;

    /** 解析后的 skill 索引：name -> description，按扫描顺序保留。 */
    private Map<String, String> skillIndex = Collections.emptyMap();

    /** skill 名 -> 该 skill 目录下所有 md 文档名（含 SKILL.md）。 */
    private Map<String, List<String>> skillDocuments = Collections.emptyMap();

    /** 拼接好的索引文本，启动时一次性生成。 */
    private String indexText = "";

    public SkillIndexLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        this.resourcePatternResolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
    }

    @PostConstruct
    public void load() {
        try {
            Resource[] skillResources = resourcePatternResolver.getResources(SKILL_PATTERN);
            Map<String, String> index = new LinkedHashMap<>();
            Map<String, List<String>> docs = new LinkedHashMap<>();

            for (Resource resource : skillResources) {
                try {
                    String content = readResource(resource);
                    Frontmatter fm = parseFrontmatter(content);
                    if (fm.name == null || fm.name.isBlank()) {
                        log.warn("SKILL.md missing frontmatter name: {}", resource.getURL());
                        continue;
                    }
                    index.put(fm.name, fm.description != null ? fm.description : "(无描述)");
                    docs.put(fm.name, listDocsForSkill(fm.name));
                } catch (IOException e) {
                    log.warn("Failed to read SKILL.md: {}", resource, e);
                }
            }

            this.skillIndex = Collections.unmodifiableMap(index);
            this.skillDocuments = Collections.unmodifiableMap(docs);
            this.indexText = buildIndexText(index, docs);
            log.info("Skill index loaded: skills={}, totalDocs={}",
                    index.keySet(), docs.values().stream().mapToInt(List::size).sum());
        } catch (IOException e) {
            log.error("Failed to scan skills directory", e);
            this.skillIndex = Collections.emptyMap();
            this.skillDocuments = Collections.emptyMap();
            this.indexText = "";
        }
    }

    public String getIndexText() {
        return indexText;
    }

    public Map<String, String> getSkillIndex() {
        return skillIndex;
    }

    public List<String> getSkillNames() {
        return new ArrayList<>(skillIndex.keySet());
    }

    public List<String> getDocumentsForSkill(String skillName) {
        return skillDocuments.getOrDefault(skillName, List.of());
    }

    /** 读取指定 skill 的 SKILL.md 完整正文（已剥离 frontmatter）。 */
    public String readSkillContent(String skillName) {
        return readDocContent(skillName, "SKILL.md");
    }

    /** 读取指定 skill 目录下指定文档的完整正文（含 frontmatter，子文档通常没有 frontmatter）。 */
    public String readDocContent(String skillName, String docName) {
        String location = "classpath:skills/" + skillName + "/" + docName;
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                return "[文档不存在] skill=" + skillName + ", doc=" + docName;
            }
            String content = readResource(resource);
            return stripFrontmatter(content);
        } catch (IOException e) {
            log.warn("Failed to read skill doc: {}/{}", skillName, docName, e);
            return "[读取失败] skill=" + skillName + ", doc=" + docName + ": " + e.getMessage();
        }
    }

    private List<String> listDocsForSkill(String skillName) {
        try {
            Resource[] resources = resourcePatternResolver.getResources(
                    "classpath:skills/" + skillName + "/*.md");
            List<String> names = new ArrayList<>();
            for (Resource r : resources) {
                String filename = r.getFilename();
                if (filename != null) {
                    names.add(filename);
                }
            }
            return names;
        } catch (IOException e) {
            log.warn("Failed to list docs for skill: {}", skillName, e);
            return List.of();
        }
    }

    private String buildIndexText(Map<String, String> index, Map<String, List<String>> docs) {
        if (index.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## 可用技能\n");
        index.forEach((name, desc) -> {
            sb.append("- **").append(name).append("**: ").append(desc);
            List<String> docList = docs.get(name);
            if (docList != null && !docList.isEmpty()) {
                sb.append(" (文档: ").append(String.join(", ", docList)).append(")");
            }
            sb.append("\n");
        });
        return sb.toString();
    }

    private String readResource(Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Frontmatter parseFrontmatter(String content) {
        if (content == null || content.isBlank()) {
            return new Frontmatter(null, null);
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("---")) {
            return new Frontmatter(null, null);
        }
        int end = trimmed.indexOf("\n---", 3);
        if (end < 0) {
            return new Frontmatter(null, null);
        }
        String frontmatter = trimmed.substring(3, end).trim();
        String name = null;
        String description = null;
        for (String line : frontmatter.split("\\r?\\n")) {
            if (line.startsWith("name:")) {
                name = line.substring("name:".length()).trim();
            } else if (line.startsWith("description:")) {
                description = line.substring("description:".length()).trim();
            }
        }
        return new Frontmatter(name, description);
    }

    private String stripFrontmatter(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("---")) {
            return content;
        }
        int end = trimmed.indexOf("\n---", 3);
        if (end < 0) {
            return content;
        }
        return trimmed.substring(end + 4).trim();
    }

    private record Frontmatter(String name, String description) {}
}
