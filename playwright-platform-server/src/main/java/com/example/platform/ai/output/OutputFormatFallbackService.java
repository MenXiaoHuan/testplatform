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

    private static final String[] FIELD_PATTERNS = {
            "response", "usedTools", "confidence", "response_text", "used_tools"
    };

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
                return ParseResult.success(result, "field_extraction");
            }
        } catch (Exception e) {
            errors.add("Field extraction failed: " + e.getMessage());
        }

        ChatAssistantResult fallbackResult = buildFallbackResult(rawText);
        log.warn("All parsing strategies failed, using fallback: errors={}", errors);
        return ParseResult.success(fallbackResult, "fallback");
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
        String response = getTextValue(node, "response", "response_text");
        List<String> usedTools = getArrayValue(node, "usedTools", "used_tools");
        String confidence = getTextValue(node, "confidence");

        return new ChatAssistantResult(
                response != null ? response : "",
                usedTools != null ? usedTools : List.of(),
                confidence != null ? confidence : "MEDIUM"
        );
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
        String response = null;
        List<String> usedTools = new ArrayList<>();
        String confidence = null;

        Pattern responsePattern = Pattern.compile(
                "(?:\"response\"|\"response_text\")\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher rm = responsePattern.matcher(text);
        if (rm.find()) {
            response = rm.group(1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }

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

        return new ChatAssistantResult(
                response != null ? response : text,
                usedTools,
                confidence != null ? confidence : "MEDIUM"
        );
    }

    private ChatAssistantResult buildFallbackResult(String rawText) {
        String responseText = rawText;
        if (responseText.length() > 2000) {
            responseText = responseText.substring(0, 2000) + "...";
        }
        return new ChatAssistantResult(responseText, List.of(), "LOW");
    }

    private boolean isValidResult(ChatAssistantResult result) {
        return result != null
                && result.response() != null
                && !result.response().isBlank();
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
            return new ParseResult(
                    new ChatAssistantResult("", List.of(), "LOW"),
                    false,
                    "none",
                    errorMessage
            );
        }
    }
}