package com.example.platform.ai.trace;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * W3C Trace Context（{@code traceparent}）解析与生成。
 *
 * <p>格式：{@code 00-<32 位十六进制 trace-id>-<16 位十六进制 span-id>-<2 位 flags>}。
 * 内置 Agent 的 traceId 直接复用上游 trace-id，使外部调用方与 Langfuse 中的 trace 双向关联：
 * 外部系统按同一个 trace-id 在 Langfuse 检索，也能从 Langfuse traceId 反查请求日志。
 *
 * <p>上游未传或格式非法时生成新的 trace-id，保证链路仍可观测。
 */
public record W3cTraceContext(String traceId, String parentSpanId, String traceFlags) {

    public static final String HEADER = "traceparent";

    private static final String VERSION = "00";
    private static final Pattern PATTERN =
            Pattern.compile("^([0-9a-fA-F]{2})-([0-9a-fA-F]{32})-([0-9a-fA-F]{16})-([0-9a-fA-F]{2})$");
    private static final String ZERO_TRACE_ID = "00000000000000000000000000000000";
    private static final String ZERO_SPAN_ID = "0000000000000000";

    /** 无上游上下文时新建一条链路（采样标记默认 01）。 */
    public static W3cTraceContext create() {
        return new W3cTraceContext(newTraceId(), newSpanId(), "01");
    }

    /** 解析上游 traceparent；缺失或非法时回退为新链路。 */
    public static W3cTraceContext fromHeader(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return create();
        }
        Matcher matcher = PATTERN.matcher(traceparent.trim());
        if (!matcher.matches()) {
            return create();
        }
        String version = matcher.group(1).toLowerCase(Locale.ROOT);
        String traceId = matcher.group(2).toLowerCase(Locale.ROOT);
        String spanId = matcher.group(3).toLowerCase(Locale.ROOT);
        String flags = matcher.group(4).toLowerCase(Locale.ROOT);
        if (!VERSION.equals(version) || ZERO_TRACE_ID.equals(traceId) || ZERO_SPAN_ID.equals(spanId)) {
            return create();
        }
        return new W3cTraceContext(traceId, spanId, flags);
    }

    /** 生成响应头：沿用 trace-id，换成本服务的新 span-id。 */
    public String toHeader(String spanId) {
        return VERSION + "-" + traceId + "-" + spanId + "-" + traceFlags;
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String newSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
