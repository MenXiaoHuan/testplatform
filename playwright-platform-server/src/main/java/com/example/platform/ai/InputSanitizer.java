package com.example.platform.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 输入净化器 —— 对用户输入做 Prompt 注入防御，分级过滤 + Unicode 归一化 + 危险内容脱敏。
 *
 * <p>处理流程（{@link #sanitize}）：
 * <ol>
 *   <li>Unicode NFC 归一化 + 去除零宽字符（防止同形字符绕过）</li>
 *   <li>长度检查（默认上限 1 万字符）</li>
 *   <li>硬阻断 {@link #checkHardBlock} —— 命中越狱/角色覆盖/系统提示词泄露等高危模式直接拒绝</li>
 *   <li>软阻断 {@link #checkSoftBlock} —— 命中可疑但非攻击的指令模式（如 "请忽略"）拒绝</li>
 *   <li>危险内容脱敏 {@link #sanitizeDangerousContent} —— 替换 script/iframe/事件处理器等危险片段</li>
 * </ol>
 *
 * <p>{@link #analyze} 提供只读分析接口，用于在前端展示「输入风险评估」而不实际拦截。
 *
 * <p>配置项：platform.ai.sanitizer.{enabled, max-length, log-pattern-matches}
 */
@Component
public class InputSanitizer {

    private static final Logger log = LoggerFactory.getLogger(InputSanitizer.class);

    private static final int MAX_INPUT_LENGTH = 10_000;

    private static final List<Pattern> HARD_BLOCK_PATTERNS = List.of(
            Pattern.compile("(?i)(?:ignore|forget|disregard|override)\\s+(?:the\\s+)?(?:previous|prior|above|earlier)\\s+(?:instructions?|prompt|rules?|directives?)"),
            Pattern.compile("(?i)(?:jailbreak|jail\\s*break|dan\\s*mode|do\\s*anything\\s*now|nopqm)"),
            Pattern.compile("(?i)(?:you are|act as|behave as)\\s+(?:a|an|the)\\s+(?:new|different|alternative)\\s+(?:assistant|model|ai|bot)"),
            Pattern.compile("(?i)(?:reveal|show|display|print|output)\\s+(?:your|the)\\s+(?:system\\s*prompt|instructions?|rules?|prompt)"),
            Pattern.compile("(?i)(?:bypass|skip|ignore|disable)\\s+(?:safety|content|security|ethical)\\s+(?:guidelines?|filters?|checks?|restrictions?)"),
            Pattern.compile("(?i)(?:new\\s+instructions?|new\\s+prompt|change\\s+(?:your|the)\\s+(?:role|persona|behavior))"),
            Pattern.compile("(?i)(?:ignore\\s+all\\s+previous|disregard\\s+all\\s+above)"),
            Pattern.compile("(?i)\\b(system\\s*prompt|system\\s*message|developer\\s*message)\\s*:"),
            Pattern.compile("(?i)\\b(exec|system|eval|process)\\s*\\("),
            Pattern.compile("(?i)<\\s*script\\s*>.*?<\\s*/\\s*script\\s*>", Pattern.DOTALL),
            Pattern.compile("(?i)(?:drop\\s+table|drop\\s+database|drop\\s+collection)\\s+"),
            Pattern.compile("(?i)(?:union\\s+select|insert\\s+into|delete\\s+from|update\\s+.*\\s+set)\\s+")
    );

    private static final List<Pattern> SOFT_BLOCK_PATTERNS = List.of(
            Pattern.compile("(?i)```(?:system|prompt|instructions?)```"),
            Pattern.compile("(?i)(?:please\\s+ignore|kindly\\s+ignore|please\\s+disregard)"),
            Pattern.compile("(?i)(?:forget\\s+everything|start\\s+fresh|reset\\s+(?:your\\s+)?memory)"),
            Pattern.compile("(?i)(?:pretend|simulate|role\\s*play)\\s+(?:to\\s+be\\s+)?(?:a|an)\\s+"),
            Pattern.compile("(?i)(?:the\\s+above\\s+(?:instructions?|prompt|rules?))"),
            Pattern.compile("(?i)(?:hack|hack\\s*this|hack\\s*system|hack\\s+into)")
    );

    private static final List<String> INJECTION_KEYWORDS = List.of(
            "system prompt", "system message", "developer message",
            "ignore previous", "ignore above", "forget previous",
            "jailbreak", "dan mode", "do anything now",
            "reveal", "show your", "print your", "output your",
            "bypass safety", "skip safety", "disable safety",
            "new instructions", "new prompt", "change your role",
            "drop table", "drop database",
            "union select", "information_schema",
            "exec(", "system(", "eval(", "process("
    );

    private static final List<Character> ZERO_WIDTH_CHARS = List.of(
            '\u200b', '\u200c', '\u200d', '\u2060', '\ufeff'
    );

    private static final List<String> HOMOGLYPH_MAP = List.of(
            "а", "е", "о", "р", "с", "х", "у"
    );

    @Value("${platform.ai.sanitizer.enabled:true}")
    private boolean enabled;

    @Value("${platform.ai.sanitizer.max-length:10000}")
    private int configuredMaxLength;

    @Value("${platform.ai.sanitizer.log-pattern-matches:false}")
    private boolean logPatternMatches;

    public SanitizeResult sanitize(String input) {
        if (input == null || input.isBlank()) {
            return SanitizeResult.valid(input);
        }

        if (!enabled) {
            return SanitizeResult.valid(input);
        }

        String normalized = normalizeUnicode(input);

        if (normalized.length() > configuredMaxLength) {
            log.warn("Input exceeds maximum length: {} > {}", normalized.length(), configuredMaxLength);
            return SanitizeResult.rejected(
                    "输入内容过长，最多允许" + configuredMaxLength + "字符",
                    SanitizeReason.TOO_LONG
            );
        }

        SanitizeResult hardBlocked = checkHardBlock(normalized);
        if (hardBlocked != null) {
            return hardBlocked;
        }

        SanitizeResult softBlocked = checkSoftBlock(normalized);
        if (softBlocked != null) {
            return softBlocked;
        }

        String sanitized = sanitizeDangerousContent(normalized);
        return SanitizeResult.valid(sanitized);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public InjectionAnalysis analyze(String input) {
        if (input == null || input.isBlank()) {
            return new InjectionAnalysis(false, false, false, List.of());
        }

        String normalized = normalizeUnicode(input);
        boolean hasHardMatch = false;
        boolean hasSoftMatch = false;
        List<String> matchedKeywords = new ArrayList<>();

        for (Pattern p : HARD_BLOCK_PATTERNS) {
            if (p.matcher(normalized).find()) {
                hasHardMatch = true;
                break;
            }
        }

        for (Pattern p : SOFT_BLOCK_PATTERNS) {
            if (p.matcher(normalized).find()) {
                hasSoftMatch = true;
                break;
            }
        }

        String lower = normalized.toLowerCase();
        for (String keyword : INJECTION_KEYWORDS) {
            if (lower.contains(keyword)) {
                matchedKeywords.add(keyword);
            }
        }

        return new InjectionAnalysis(hasHardMatch, hasSoftMatch, !matchedKeywords.isEmpty(), matchedKeywords);
    }

    private String normalizeUnicode(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFC);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (!ZERO_WIDTH_CHARS.contains(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private SanitizeResult checkHardBlock(String input) {
        for (Pattern pattern : HARD_BLOCK_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.warn("[SANITIZE] Hard-block injection detected: pattern={}, inputPreview={}",
                        pattern.pattern(), input.substring(0, Math.min(100, input.length())));
                return SanitizeResult.rejected(
                        "输入内容包含不安全的指令，请重新组织您的提问",
                        SanitizeReason.INJECTION_DETECTED
                );
            }
        }
        return null;
    }

    private SanitizeResult checkSoftBlock(String input) {
        for (Pattern pattern : SOFT_BLOCK_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.warn("[SANITIZE] Soft-block suspicious pattern detected: pattern={}, inputPreview={}",
                        pattern.pattern(), input.substring(0, Math.min(100, input.length())));
                return SanitizeResult.rejected(
                        "输入内容包含可能的注入指令，请用自然语言重新描述您的问题",
                        SanitizeReason.SUSPICIOUS_PATTERN
                );
            }
        }
        return null;
    }

    private String sanitizeDangerousContent(String input) {
        String result = input;

        result = result.replaceAll("(?i)<script[^>]*>.*?</script>", "[已移除脚本内容]");
        result = result.replaceAll("(?i)<iframe[^>]*>.*?</iframe>", "[已移除iframe内容]");
        result = result.replaceAll("(?i)<\\s*embed[^>]*/?>", "[已移除嵌入内容]");
        result = result.replaceAll("(?i)javascript:", "[已移除javascript引用]");
        result = result.replaceAll("(?i)vbscript:", "[已移除vbscript引用]");
        result = result.replaceAll("(?i)on\\w+\\s*=", "[已移除事件处理器]");
        result = result.replaceAll("(?i)data\\s*:\\s*text/html", "[已移除HTML数据]");

        return result;
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

    public record InjectionAnalysis(
            boolean hardBlocked,
            boolean softBlocked,
            boolean keywordMatch,
            List<String> matchedKeywords
    ) {
        public boolean isSuspicious() {
            return hardBlocked || softBlocked || !matchedKeywords.isEmpty();
        }

        public boolean isDangerous() {
            return hardBlocked;
        }
    }

    public enum SanitizeReason {
        TOO_LONG,
        INJECTION_DETECTED,
        SUSPICIOUS_PATTERN,
        INVALID_FORMAT
    }
}
