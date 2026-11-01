package com.example.platform.ai.trace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class W3cTraceContextTest {

    @Test
    void shouldParseValidTraceparent() {
        W3cTraceContext context = W3cTraceContext.fromHeader(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        assertThat(context.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(context.parentSpanId()).isEqualTo("00f067aa0ba902b7");
        assertThat(context.traceFlags()).isEqualTo("01");
    }

    @Test
    void shouldNormalizeUppercaseHex() {
        W3cTraceContext context = W3cTraceContext.fromHeader(
                "00-4BF92F3577B34DA6A3CE929D0E0E4736-00F067AA0BA902B7-01");

        assertThat(context.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void shouldGenerateNewTraceWhenHeaderInvalid() {
        W3cTraceContext context = W3cTraceContext.fromHeader("not-a-traceparent");

        assertThat(context.traceId()).matches("[0-9a-f]{32}");
        assertThat(context.parentSpanId()).matches("[0-9a-f]{16}");
    }

    @Test
    void shouldRejectAllZeroTraceId() {
        W3cTraceContext context = W3cTraceContext.fromHeader(
                "00-00000000000000000000000000000000-00f067aa0ba902b7-01");

        assertThat(context.traceId()).doesNotStartWith("00000000000000000000000000000000");
    }

    @Test
    void shouldBuildResponseHeaderWithSameTraceId() {
        W3cTraceContext context = W3cTraceContext.fromHeader(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        String header = context.toHeader("1234567890abcdef");

        assertThat(header).isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-1234567890abcdef-01");
    }
}
