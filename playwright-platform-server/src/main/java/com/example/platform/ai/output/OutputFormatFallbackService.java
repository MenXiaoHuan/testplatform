package com.example.platform.ai.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OutputFormatFallbackService {

    private static final Logger log = LoggerFactory.getLogger(OutputFormatFallbackService.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile(
            "```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile(
            "\\{[\\s\\S]*\\}");

    public ParseResult parseAgentOutput(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return ParseResult.failure("Empty response from agent");
        }

        String cleanedText = preprocessText(rawText);

        List<String> errors = new ArrayList<>();

        try {
            BeanOutputConverter<ChatAssistantResult> converter =
                    new BeanOutputConverter<>(ChatAssistantResult.class);
            ChatAssistantResult result = converter.convert(cleanedText);
            if (isValidResult(result)) {
                result = enrichSections(result, cleanedText);
                return ParseResult.success(result, "bean_converter");
            }
            errors.add("BeanOutputConverter returned invalid result");
        } catch (Exception e) {
            errors.add("BeanOutputConverter failed: " + e.getMessage());
            log.debug("BeanOutputConverter failed, trying fallback: {}", e.getMessage());
        }

        try {
            String jsonStr = extractJson(cleanedText);
            if (jsonStr != null) {
                JsonNode node = objectMapper.readTree(jsonStr);
                ChatAssistantResult result = parseFromJsonNode(node);
                if (isValidResult(result)) {
                    return ParseResult.success(result, "json_extraction");
                }
                errors.add("JSON extraction produced invalid result");
            }
        } catch (Exception e) {
            errors.add("JSON extraction failed: " + e.getMessage());
            log.debug("JSON extraction failed: {}", e.getMessage());
        }

        try {
            ChatAssistantResult result = parseFieldByField(cleanedText);
            if (isValidResult(result)) {
                result = enrichSections(result, cleanedText);
                return ParseResult.success(result, "field_extraction");
            }
        } catch (Exception e) {
            errors.add("Field extraction failed: " + e.getMessage());
        }

        ChatAssistantResult fallbackResult = buildFallbackResult(rawText);
        log.warn("All parsing strategies failed, using fallback: errors={}", errors);
        return ParseResult.success(fallbackResult, "fallback");
    }

    private ChatAssistantResult enrichSections(ChatAssistantResult result, String rawJson) {
        if (result.sections() != null && !result.sections().isEmpty()) {
            return result;
        }
        try {
            String jsonStr = extractJson(rawJson);
            if (jsonStr != null) {
                JsonNode node = objectMapper.readTree(jsonStr);
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
            log.debug("Could not enrich sections from raw JSON: {}", e.getMessage());
        }
        return result;
    }

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

    private String extractJson(String text) {
        Matcher blockMatcher = JSON_BLOCK_PATTERN.matcher(text);
        if (blockMatcher.find()) {
            String candidate = blockMatcher.group(1).trim();
            if (candidate.startsWith("{")) {
                return candidate;
            }
        }

        Matcher objectMatcher = JSON_OBJECT_PATTERN.matcher(text);
        if (objectMatcher.find()) {
            return objectMatcher.group();
        }

        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }

        return null;
    }

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
            JsonNode node = objectMapper.readTree(jsonStr);
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
