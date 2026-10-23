package com.example.platform.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveDataRedactorTest {
    @Test
    void shouldRedactUrlUserInfoAndTokens() {
        String text = "fatal: https://user:ghp_123456789012345678901234@github.com/org/repo.git "
                + "Authorization: Bearer abcdefghijklmnop "
                + "password=secret123 "
                + "sk-1234567890abcdef";

        String redacted = SensitiveDataRedactor.redact(text);

        assertThat(redacted).doesNotContain("ghp_123456789012345678901234");
        assertThat(redacted).doesNotContain("abcdefghijklmnop");
        assertThat(redacted).doesNotContain("secret123");
        assertThat(redacted).doesNotContain("sk-1234567890abcdef");
        assertThat(redacted).contains("https://***@github.com");
    }

    @Test
    void shouldKeepOrdinaryLogText() {
        assertThat(SensitiveDataRedactor.redact("12 passed (3.2s)"))
                .isEqualTo("12 passed (3.2s)");
    }
}
