package com.example.platform.evaluation.online;

import com.example.platform.evaluation.langfuse.LangfuseTrace;

import java.util.List;

/** Trace 浏览器分页响应。 */
public record TracePageResponse(List<LangfuseTrace> items, long total, int page, int size) {
}
