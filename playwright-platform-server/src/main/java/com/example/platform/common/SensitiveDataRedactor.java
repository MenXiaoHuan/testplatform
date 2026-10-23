package com.example.platform.common;

import java.util.regex.Pattern;

/**
 * 日志/错误消息/阶段日志的统一脱敏工具。
 *
 * <p>平台会把 Git URL、命令输出、异常消息写入数据库、MinIO 和前端，
 * 因此不能只依赖 AI Trace 层的脱敏。这里覆盖最常见的几类凭据：
 * URL userinfo、Bearer Token、GitHub/DeepSeek token、key=value 形式。
 *
 * <p>它是兜底而不是完整的 DLP：测试代码自己打印的任意内容仍可能包含敏感信息，
 * 私有仓库应优先使用服务器侧 SSH/credential helper，而不是把 token 放进 URL。
 */
public final class SensitiveDataRedactor {
    private static final Pattern URL_USERINFO =
            Pattern.compile("(?i)(https?://)[^/@\\s]+@");
    private static final Pattern BEARER =
            Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]{12,}");
    private static final Pattern KEY_VALUE =
            Pattern.compile("(?i)((?:access[_-]?token|refresh[_-]?token|api[_-]?key|"
                    + "secret|password|passwd|pwd|token)\\s*[=:]\\s*)[^\\s,;]+");
    private static final Pattern GITHUB_TOKEN =
            Pattern.compile("\\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\\b");
    private static final Pattern OPENAI_STYLE_TOKEN =
            Pattern.compile("\\bsk-[A-Za-z0-9]{16,}\\b");
    private static final Pattern AWS_ACCESS_KEY =
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b");

    private SensitiveDataRedactor() {
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String redacted = URL_USERINFO.matcher(text).replaceAll("$1***@");
        redacted = BEARER.matcher(redacted).replaceAll("$1***");
        redacted = KEY_VALUE.matcher(redacted).replaceAll("$1***");
        redacted = GITHUB_TOKEN.matcher(redacted).replaceAll("***");
        redacted = OPENAI_STYLE_TOKEN.matcher(redacted).replaceAll("***");
        redacted = AWS_ACCESS_KEY.matcher(redacted).replaceAll("***");
        return redacted;
    }
}
