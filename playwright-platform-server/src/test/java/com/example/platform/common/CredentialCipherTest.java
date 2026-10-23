package com.example.platform.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CredentialCipherTest {
    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void shouldRoundTripWithoutStoringPlaintext() {
        CredentialCipher cipher = new CredentialCipher(KEY);

        String encrypted = cipher.encrypt("ghp_secret");

        assertThat(encrypted).startsWith("v1:").doesNotContain("ghp_secret");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("ghp_secret");
    }

    @Test
    void shouldFailClearlyWhenKeyIsMissing() {
        CredentialCipher cipher = new CredentialCipher("");

        assertThatThrownBy(() -> cipher.encrypt("secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLATFORM_CREDENTIAL_KEY");
    }
}
