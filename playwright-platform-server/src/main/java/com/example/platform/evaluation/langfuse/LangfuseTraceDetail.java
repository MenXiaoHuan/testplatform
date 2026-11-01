package com.example.platform.evaluation.langfuse;

import java.util.List;

/** Trace 详情 —— Trace 头部 + 完整 Observation 链路（含父子关系，供 Trace 浏览器渲染 span 树）。 */
public record LangfuseTraceDetail(LangfuseTrace trace, List<LangfuseObservation> observations) {
}
