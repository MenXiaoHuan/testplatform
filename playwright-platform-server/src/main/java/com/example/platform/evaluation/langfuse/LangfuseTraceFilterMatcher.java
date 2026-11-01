package com.example.platform.evaluation.langfuse;

/**
 * Langfuse Trace 本地过滤器 —— 服务端已按 tag 过滤，这里兜底保证
 * targetId / tags / sessionIds / 失败状态 / 关键字都真正生效。
 */
final class LangfuseTraceFilterMatcher {

    private LangfuseTraceFilterMatcher() {}

    static boolean matches(LangfuseTrace trace, LangfuseTraceFilter filter) {
        if (trace == null || filter == null) {
            return trace != null;
        }

        if (filter.targetId() != null && !filter.targetId().isBlank()) {
            String expectedTag = "target_id=" + filter.targetId();
            boolean hasTag = trace.tags() != null && trace.tags().contains(expectedTag);
            if (!hasTag && !filter.targetId().equals(trace.targetId())) {
                return false;
            }
        }

        if (filter.tags() != null && !filter.tags().isEmpty()) {
            if (trace.tags() == null || !trace.tags().containsAll(filter.tags())) {
                return false;
            }
        }

        if (filter.userId() != null && !filter.userId().isBlank()) {
            String actual = trace.userId();
            if (actual == null && trace.metadata() != null && trace.metadata().get("userId") != null) {
                actual = String.valueOf(trace.metadata().get("userId"));
            }
            if (actual == null || !filter.userId().equals(actual)) {
                return false;
            }
        }

        if (filter.sessionIds() != null && !filter.sessionIds().isEmpty()) {
            if (trace.sessionId() == null || !filter.sessionIds().contains(trace.sessionId())) {
                return false;
            }
        }

        if (Boolean.TRUE.equals(filter.onlyFailed())) {
            if (trace.tags() == null || !trace.tags().contains("error=true")) {
                return false;
            }
        }

        if (Boolean.TRUE.equals(filter.onlySuccess()) && !trace.success()) {
            return false;
        }

        if (filter.searchQuery() != null && !filter.searchQuery().isBlank()) {
            String haystack = String.join(" ",
                    nullToEmpty(trace.name()), nullToEmpty(trace.sessionId()),
                    nullToEmpty(trace.input()), nullToEmpty(trace.output())).toLowerCase();
            if (!haystack.contains(filter.searchQuery().trim().toLowerCase())) {
                return false;
            }
        }

        return true;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
