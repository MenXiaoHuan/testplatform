package com.example.platform.ai.output;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 输出解析服务。
 *
 * 工程化设计原则：
 * 1. 不破坏原则 —— 合法 JSON 直接返回，不做任何修改
 * 2. 管道 + 验证模式 —— 每个修复步骤独立运行，修复后立即验证可解析性，通过才采用
 * 3. 容错解析器优先 —— 使用 Jackson lenient 模式处理单引号、尾逗号、注释等边缘情况
 * 4. 保守回退 —— 所有修复失败时返回原始文本，不破坏数据
 * 5. 可观测性 —— 记录每个解析策略和修复步骤的决策路径
 */
@Service
public class OutputFormatFallbackService {

    private static final Logger log = LoggerFactory.getLogger(OutputFormatFallbackService.class);

    /** 标准解析器 */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 容错解析器 —— 处理模型输出的常见格式问题：
     * - 单引号字符串
     * - 注释 (// 和 /* *\/)
     * - 缺失值
     * - 未加引号的字段名
     */
    private static final ObjectMapper lenientMapper = new ObjectMapper()
            .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            .configure(JsonParser.Feature.ALLOW_MISSING_VALUES, true);

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile(
            "```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile(
            "\\{[\\s\\S]*\\}");

    // ==================== 公共入口 ====================

    public ParseResult parseAgentOutput(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return ParseResult.failure("Empty response from agent");
        }

        String cleanedText = preprocessText(rawText);
        String extractedJson = extractJson(cleanedText);
        String jsonCandidate = extractedJson != null ? extractedJson : cleanedText;

        List<String> errors = new ArrayList<>();

        // 策略 1：直接用容错解析器解析（最可能成功的 happy path）
        try {
            JsonNode node = lenientMapper.readTree(jsonCandidate);
            ChatAssistantResult result = parseFromJsonNode(node);
            if (isValidResult(result)) {
                log.debug("[OutputParse] strategy=lenient_parse sections={}",
                        result.sections() != null ? result.sections().size() : 0);
                return ParseResult.success(result, "lenient_parse");
            }
            errors.add("lenient_parse: invalid result");
        } catch (Exception e) {
            errors.add("lenient_parse: " + e.getMessage());
        }

        // 策略 2：修复管道（每个修复步骤独立运行 + 验证）
        String repaired = repairJson(jsonCandidate);
        if (repaired != null && !repaired.equals(jsonCandidate)) {
            try {
                JsonNode node = lenientMapper.readTree(repaired);
                ChatAssistantResult result = parseFromJsonNode(node);
                if (isValidResult(result)) {
                    log.debug("[OutputParse] strategy=repair_pipeline sections={}",
                            result.sections() != null ? result.sections().size() : 0);
                    return ParseResult.success(result, "repair_pipeline");
                }
                errors.add("repair_pipeline: invalid result");
            } catch (Exception e) {
                errors.add("repair_pipeline: " + e.getMessage());
            }
        }

        // 策略 3：BeanOutputConverter（Spring AI 内置转换器）
        try {
            BeanOutputConverter<ChatAssistantResult> converter =
                    new BeanOutputConverter<>(ChatAssistantResult.class);
            ChatAssistantResult result = converter.convert(repaired != null ? repaired : jsonCandidate);
            if (isValidResult(result)) {
                result = enrichSections(result, repaired != null ? repaired : jsonCandidate);
                log.debug("[OutputParse] strategy=bean_converter sections={}",
                        result.sections() != null ? result.sections().size() : 0);
                return ParseResult.success(result, "bean_converter");
            }
            errors.add("bean_converter: invalid result");
        } catch (Exception e) {
            errors.add("bean_converter: " + e.getMessage());
        }

        // 策略 4：字段级提取（正则兜底）
        try {
            ChatAssistantResult result = parseFieldByField(jsonCandidate);
            if (isValidResult(result)) {
                result = enrichSections(result, jsonCandidate);
                log.debug("[OutputParse] strategy=field_extraction sections={}",
                        result.sections() != null ? result.sections().size() : 0);
                return ParseResult.success(result, "field_extraction");
            }
        } catch (Exception e) {
            errors.add("field_extraction: " + e.getMessage());
        }

        // 最终兜底：原始文本作为单段落返回
        log.warn("[OutputParse] All strategies failed, using fallback. errors={}", errors);
        ChatAssistantResult fallbackResult = buildFallbackResult(rawText);
        return ParseResult.success(fallbackResult, "fallback");
    }

    // ==================== 文本预处理 ====================

    private String preprocessText(String text) {
        if (text == null) return "";
        String cleaned = text.trim();

        if (cleaned.startsWith("```")) {
            Matcher matcher = JSON_BLOCK_PATTERN.matcher(cleaned);
            if (matcher.find()) {
                cleaned = matcher.group(1).trim();
            }
        }

        cleaned = cleaned.replaceFirst("^```json\\s*", "")
                         .replaceFirst("^```JSON\\s*", "")
                         .replaceFirst("```\\s*$", "");

        return cleaned.trim();
    }

    /**
     * 提取 JSON 文本：优先匹配代码块，退化为括号平衡法。
     * 括号平衡法会跳过字符串内的括号，精确定位 JSON 边界。
     */
    private String extractJson(String text) {
        // 优先：```json ... ``` 代码块
        Matcher blockMatcher = JSON_BLOCK_PATTERN.matcher(text);
        if (blockMatcher.find()) {
            String candidate = blockMatcher.group(1).trim();
            if (candidate.startsWith("{")) {
                return candidate;
            }
        }

        // 退化：括号平衡法
        int firstBrace = text.indexOf('{');
        if (firstBrace < 0) {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        char stringChar = 0;
        boolean escaped = false;

        for (int i = firstBrace; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (inString) {
                if (c == stringChar) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                stringChar = c;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(firstBrace, i + 1);
                }
            }
        }

        // 最终退化：贪心正则
        Matcher objectMatcher = JSON_OBJECT_PATTERN.matcher(text);
        if (objectMatcher.find()) {
            return objectMatcher.group();
        }
        return null;
    }

    // ==================== JSON 修复管道 ====================

    /**
     * 修复管道：每个修复步骤独立运行 + 验证，通过即返回。
     * 不破坏合法 JSON（合法的直接返回）。
     * 修复失败返回原始文本（不比修复前更差）。
     */
    private String repairJson(String jsonStr) {
        // 合法 JSON 直接返回（最重要：不破坏合法数据）
        if (isValidJson(jsonStr)) {
            return jsonStr;
        }

        log.debug("[JsonRepair] Input invalid, attempting repairs. preview={}",
                jsonStr.substring(0, Math.min(300, jsonStr.length())));

        // 单步修复：每个修复器独立运行，验证后采用
        List<Map.Entry<String, java.util.function.Function<String, String>>> repairSteps = List.of(
                Map.entry("remove_duplicates", this::removeDuplicateKeys),
                Map.entry("strip_empty_fields", this::stripEmptyBlockFields),
                Map.entry("fix_unescaped_quotes", this::fixUnescapedQuotes)
        );

        // 尝试每个单步修复
        for (Map.Entry<String, java.util.function.Function<String, String>> step : repairSteps) {
            try {
                String repaired = step.getValue().apply(jsonStr);
                if (isValidJson(repaired)) {
                    log.debug("[JsonRepair] Repaired via single step: {}", step.getKey());
                    return repaired;
                }
            } catch (Exception e) {
                log.debug("[JsonRepair] Step {} failed: {}", step.getKey(), e.getMessage());
            }
        }

        // 组合修复：去重 → 去空字段 → 修引号
        try {
            String combined = jsonStr;
            combined = removeDuplicateKeys(combined);
            combined = stripEmptyBlockFields(combined);
            combined = fixUnescapedQuotes(combined);
            if (isValidJson(combined)) {
                log.debug("[JsonRepair] Repaired via combined pipeline");
                return combined;
            }
        } catch (Exception e) {
            log.debug("[JsonRepair] Combined pipeline failed: {}", e.getMessage());
        }

        // 全部失败：返回原始文本（保守回退，不破坏数据）
        log.warn("[JsonRepair] All repair strategies failed, returning original");
        return jsonStr;
    }

    private boolean isValidJson(String text) {
        if (text == null || text.isBlank()) return false;
        try {
            lenientMapper.readTree(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 修复器实现 ====================

    /**
     * 去除重复的 JSON 键（保留第一个出现的值）。
     * 使用正则匹配特定字段的重复模式。
     */
    private String removeDuplicateKeys(String json) {
        String[] keys = {"type", "level", "text", "items", "ordered", "language", "code", "headers", "rows"};
        for (String key : keys) {
            // 匹配 "key":"value",...,"key": 形式的重复，保留最后一个
            String pattern = "\"" + key + "\":\"[^\"]*\",\\s*\"" + key + "\":";
            json = json.replaceAll(pattern, "\"" + key + "\":");
        }
        return json;
    }

    /**
     * 修复字符串内未转义的引号。
     * 使用状态机：当遇到引号且后面不是 JSON 结构字符（,:}]) 时，判定为字符串内引号并转义。
     */
    private String fixUnescapedQuotes(String json) {
        StringBuilder sb = new StringBuilder(json.length() + 16);
        boolean inString = false;
        char stringChar = 0;
        int i = 0;

        while (i < json.length()) {
            char c = json.charAt(i);

            if (!inString) {
                if (c == '"' || c == '\'') {
                    inString = true;
                    stringChar = c;
                }
                sb.append(c);
                i++;
            } else {
                if (c == '\\') {
                    sb.append(c);
                    if (i + 1 < json.length()) {
                        sb.append(json.charAt(i + 1));
                        i += 2;
                    } else {
                        i++;
                    }
                } else if (c == stringChar) {
                    char next = (i + 1 < json.length()) ? json.charAt(i + 1) : '\0';
                    // 引号后跟结构字符 → 字符串结束
                    if (next == ',' || next == '}' || next == ']' || next == ':' || Character.isWhitespace(next)) {
                        inString = false;
                        sb.append(c);
                        i++;
                    } else {
                        // 引号后跟非结构字符 → 字符串内的未转义引号，转义它
                        sb.append('\\').append(c);
                        i++;
                    }
                } else if (c == '\n' || c == '\r') {
                    sb.append("\\n");
                    i++;
                } else {
                    sb.append(c);
                    i++;
                }
            }
        }
        return sb.toString();
    }

    /**
     * 去除 block 中不必要的空字段，减少 JSON 噪声。
     */
    private String stripEmptyBlockFields(String json) {
        json = json.replaceAll("\"code\":\"\",\\s*", "");
        json = json.replaceAll("\"headers\":\\[\\],\\s*", "");
        json = json.replaceAll("\"items\":\\[\\],\\s*", "");
        json = json.replaceAll("\"ordered\":false,\\s*", "");
        json = json.replaceAll("\"language\":\"\",\\s*", "");
        json = json.replaceAll("\"rows\":\\[\\],\\s*", "");
        json = json.replaceAll(",\\s*\"rows\":\\[\\]", "");
        json = json.replaceAll(",\\s*\"ordered\":false", "");
        return json;
    }

    // ==================== JSON 节点解析 ====================

    private ChatAssistantResult parseFromJsonNode(JsonNode node) {
        List<String> usedTools = getArrayValue(node, "usedTools", "used_tools");
        String confidence = getTextValue(node, "confidence");
        String responseType = getTextValue(node, "responseType", "response_type");

        ChatAssistantResult.FaultDetail faultDetail = null;
        JsonNode fdNode = node.get("faultDetail");
        if (fdNode != null && !fdNode.isNull() && fdNode.isObject()) {
            faultDetail = new ChatAssistantResult.FaultDetail(
                    getTextValue(fdNode, "fault_type", "faultType"),
                    getTextValue(fdNode, "root_cause", "rootCause"),
                    getTextValue(fdNode, "immediate_solution", "immediateSolution"),
                    getTextValue(fdNode, "long_term_optimize", "longTermOptimize"),
                    getTextValue(fdNode, "test_risk", "testRisk"),
                    getTextValue(fdNode, "reproduce_steps", "reproduceSteps")
            );
        }

        List<ContentBlock> sections = parseSections(node);

        return new ChatAssistantResult(
                usedTools != null ? usedTools : List.of(),
                confidence != null ? confidence : "MEDIUM",
                responseType != null ? responseType : "UNKNOWN",
                faultDetail,
                sections
        );
    }

    private List<ContentBlock> parseSections(JsonNode node) {
        JsonNode sectionsNode = node.get("sections");
        if (sectionsNode == null || !sectionsNode.isArray()) {
            return List.of();
        }
        List<ContentBlock> sections = new ArrayList<>();
        for (JsonNode block : sectionsNode) {
            sections.add(parseContentBlock(block));
        }
        return sections;
    }

    private ContentBlock parseContentBlock(JsonNode block) {
        String type = getTextValue(block, "type");
        Integer level = block.has("level") && !block.get("level").isNull() ? block.get("level").asInt() : null;
        String text = getTextValue(block, "text");
        List<String> items = getArrayValue(block, "items");
        Boolean ordered = block.has("ordered") && !block.get("ordered").isNull() ? block.get("ordered").asBoolean() : null;
        String language = getTextValue(block, "language");
        String code = getTextValue(block, "code");
        List<String> headers = getArrayValue(block, "headers");
        List<List<String>> rows = null;
        JsonNode rowsNode = block.get("rows");
        if (rowsNode != null && rowsNode.isArray()) {
            rows = new ArrayList<>();
            for (JsonNode rowNode : rowsNode) {
                if (rowNode.isArray()) {
                    List<String> row = new ArrayList<>();
                    rowNode.forEach(cell -> row.add(cell.isNull() ? "" : cell.asText()));
                    rows.add(row);
                }
            }
        }
        return new ContentBlock(type, level, text, items, ordered, language, code, headers, rows);
    }

    private String getTextValue(JsonNode node, String... fieldNames) {
        for (String name : fieldNames) {
            JsonNode val = node.get(name);
            if (val != null && !val.isNull()) {
                String text = val.asText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private List<String> getArrayValue(JsonNode node, String... fieldNames) {
        for (String name : fieldNames) {
            JsonNode val = node.get(name);
            if (val != null && val.isArray()) {
                List<String> result = new ArrayList<>();
                val.forEach(item -> {
                    if (!item.isNull()) {
                        result.add(item.asText());
                    }
                });
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }
        return null;
    }

    // ==================== 兜底策略 ====================

    private ChatAssistantResult enrichSections(ChatAssistantResult result, String rawJson) {
        if (result.sections() != null && !result.sections().isEmpty()) {
            return result;
        }
        try {
            String jsonStr = extractJson(rawJson);
            if (jsonStr != null) {
                JsonNode node = lenientMapper.readTree(jsonStr);
                List<ContentBlock> sections = parseSections(node);
                if (!sections.isEmpty()) {
                    return new ChatAssistantResult(
                            result.usedTools(), result.confidence(),
                            result.responseType(), result.faultDetail(),
                            sections
                    );
                }
            }
        } catch (Exception e) {
            log.debug("[OutputParse] Could not enrich sections from raw JSON: {}", e.getMessage());
        }
        return result;
    }

    private ChatAssistantResult parseFieldByField(String text) {
        List<String> usedTools = new ArrayList<>();
        String confidence = null;
        String responseType = null;

        Pattern toolsPattern = Pattern.compile(
                "(?:\"usedTools\"|\"used_tools\")\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher tm = toolsPattern.matcher(text);
        if (tm.find()) {
            String toolsStr = tm.group(1);
            Pattern toolItemPattern = Pattern.compile("\"([^\"]*)\"");
            Matcher tim = toolItemPattern.matcher(toolsStr);
            while (tim.find()) {
                usedTools.add(tim.group(1));
            }
        }

        Pattern confidencePattern = Pattern.compile("\"confidence\"\\s*:\\s*\"([^\"]*)\"");
        Matcher cm = confidencePattern.matcher(text);
        if (cm.find()) {
            confidence = cm.group(1);
        }

        Pattern typePattern = Pattern.compile("\"(?:responseType|response_type)\"\\s*:\\s*\"([^\"]*)\"");
        Matcher rpm = typePattern.matcher(text);
        if (rpm.find()) {
            responseType = rpm.group(1);
        }

        List<ContentBlock> sections = extractSectionsFromText(text);

        return new ChatAssistantResult(
                usedTools,
                confidence != null ? confidence : "MEDIUM",
                responseType != null ? responseType : "UNKNOWN",
                null,
                sections
        );
    }

    private List<ContentBlock> extractSectionsFromText(String text) {
        String jsonStr = extractJson(text);
        if (jsonStr == null) return List.of();
        try {
            JsonNode node = lenientMapper.readTree(jsonStr);
            return parseSections(node);
        } catch (Exception e) {
            return List.of();
        }
    }

    private ChatAssistantResult buildFallbackResult(String rawText) {
        String truncated = rawText;
        if (truncated.length() > 2000) {
            truncated = truncated.substring(0, 2000) + "...";
        }
        List<ContentBlock> sections = List.of(ContentBlock.paragraph(truncated));
        return new ChatAssistantResult(List.of(), "LOW", "UNKNOWN", null, sections);
    }

    private boolean isValidResult(ChatAssistantResult result) {
        return result != null
                && result.sections() != null
                && !result.sections().isEmpty();
    }

    // ==================== 工具方法 ====================

    public static String deriveTextFromSections(List<ContentBlock> sections) {
        if (sections == null || sections.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : sections) {
            switch (b.type() != null ? b.type() : "paragraph") {
                case "heading" -> {
                    int level = b.level() != null ? b.level() : 2;
                    sb.append("#".repeat(level)).append(' ').append(b.text() != null ? b.text() : "").append("\n\n");
                }
                case "paragraph" -> sb.append(b.text() != null ? b.text() : "").append("\n\n");
                case "list" -> {
                    if (b.items() != null) {
                        int i = 0;
                        for (String item : b.items()) {
                            String prefix = b.ordered() != null && b.ordered() ? (++i) + ". " : "- ";
                            sb.append(prefix).append(item).append('\n');
                        }
                        sb.append('\n');
                    }
                }
                case "code" -> sb.append("```").append(b.language() != null ? b.language() : "").append('\n')
                        .append(b.code() != null ? b.code() : "").append("\n```\n\n");
                case "quote" -> sb.append("> ").append(b.text() != null ? b.text() : "").append("\n\n");
                case "table" -> {
                    if (b.headers() != null && b.rows() != null) {
                        sb.append('|').append(String.join("|", b.headers())).append("|\n");
                        sb.append('|').append(b.headers().stream().map(h -> "---").collect(java.util.stream.Collectors.joining("|"))).append("|\n");
                        for (java.util.List<String> row : b.rows()) {
                            sb.append('|').append(String.join("|", row)).append("|\n");
                        }
                        sb.append('\n');
                    }
                }
                default -> sb.append(b.text() != null ? b.text() : "").append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    // ==================== 结果记录 ====================

    public record ParseResult(
            ChatAssistantResult result,
            boolean success,
            String strategy,
            String errorMessage
    ) {
        public static ParseResult success(ChatAssistantResult result, String strategy) {
            return new ParseResult(result, true, strategy, null);
        }

        public static ParseResult failure(String errorMessage) {
            List<ContentBlock> fallbackSections = List.of(
                    ContentBlock.paragraph("输出解析失败: " + errorMessage)
            );
            return new ParseResult(
                    new ChatAssistantResult(List.of(), "LOW", "UNKNOWN", null, fallbackSections),
                    false,
                    "none",
                    errorMessage
            );
        }
    }
}
