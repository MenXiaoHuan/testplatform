package com.example.platform.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class InputSanitizer {

    private static final Logger log = LoggerFactory.getLogger(InputSanitizer.class);

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)(?:ignore|forget|disregard|override)\\s+(?:the\\s+)?(?:previous|prior|above|earlier)\\s+(?:instructions?|prompt|rules?|directives?)"),
            Pattern.compile("(?i)(?:you are|act as|behave as)\\s+(?:a|an|the)\\s+(?:new|different|alternative)\\s+(?:assistant|model|ai|bot)"),
            Pattern.compile("(?i)(?:system\\s*prompt|system\\s*message|developer\\s*message)\\s*:"),
            Pattern.compile("(?i)(?:jailbreak|jail\\s*break|dan\\s*mode|do\\s*anything\\s*now)"),
            Pattern.compile("(?i)(?:reveal|show|display|print)\\s+(?:your|the)\\s+(?:system\\s*prompt|instructions?|rules?|prompt)"),
            Pattern.compile("(?i)(?:new\\s+instructions?|new\\s+prompt|change\\s+(?:your|the)\\s+(?:role|persona|behavior))"),
            Pattern.compile("(?i)(?:bypass|skip|ignore)\\s+(?:safety|content|security|ethical)\\s+(?:guidelines?|filters?|checks?|restrictions?)"),
            Pattern.compile("(?i)<\\s*script\\s*>.*?<\\s*/\\s*script\\s*>", Pattern.DOTALL),
            Pattern.compile("(?i)(?:exec|system|eval|process)\\s*\\("),
            Pattern.compile("(?i)(?:ignore\\s+all\\s+previous|disregard\\s+all\\s+above)")
    );

    private static final int MAX_INPUT_LENGTH = 10_000;

    @Value("${platform.ai.sanitizer.enabled:true}")
    private boolean enabled;

    @Value("${platform.ai.sanitizer.max-length:10000}")
    private int configuredMaxLength;

    public SanitizeResult sanitize(String input) {
        if (input == null || input.isBlank()) {
            return SanitizeResult.valid(input);
        }

        if (!enabled) {
            return SanitizeResult.valid(input);
        }

        String trimmed = input.trim();

        if (trimmed.length() > configuredMaxLength) {
            log.warn("Input exceeds maximum length: {} > {}", trimmed.length(), configuredMaxLength);
            return SanitizeResult.rejected(
                    "输入内容过长，最多允许" + configuredMaxLength + "字符",
                    SanitizeReason.TOO_LONG
            );
        }

        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                log.warn("Potential prompt injection detected: pattern={}, inputPreview={}",
                        pattern.pattern(), trimmed.substring(0, Math.min(100, trimmed.length())));
                return SanitizeResult.rejected(
                        "输入内容包含不安全的指令，请重新组织您的提问",
                        SanitizeReason.INJECTION_DETECTED
                );
            }
        }

        String sanitized = sanitizeDangerousContent(trimmed);
        return SanitizeResult.valid(sanitized);
    }

    private String sanitizeDangerousContent(String input) {
        String result = input;

        result = result.replaceAll("(?i)<script[^>]*>.*?</script>", "[已移除脚本内容]");
        result = result.replaceAll("(?i)<iframe[^>]*>.*?</iframe>", "[已移除iframe内容]");
        result = result.replaceAll("(?i)javascript:", "[已移除javascript引用]");

        return result;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public record SanitizeResult(
            String sanitizedInput,
            boolean valid,
            String rejectionReason,
            SanitizeReason reason
    ) {
        public static SanitizeResult valid(String input) {
            return new SanitizeResult(input, true, null, null);
        }

        public static SanitizeResult rejected(String reason, SanitizeReason sanitizeReason) {
            return new SanitizeResult(null, false, reason, sanitizeReason);
        }
    }

    public enum SanitizeReason {
        TOO_LONG,
        INJECTION_DETECTED,
        INVALID_FORMAT
    }
}