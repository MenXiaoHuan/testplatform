package com.example.platform.evaluation.common;

/** 被评目标类型枚举，对应 eval_target.target_type 列。 */
public enum TargetType {
    /** 直接调用 LLM API（如 DeepSeek OpenAI-compatible） */
    LLM_API,
    /**
     * 外部 Agent：可选 HTTP Endpoint 参与离线评测；也可通过 Langfuse SDK 接入在线 Trace。
     * 同一 target 可同时具备两种能力，Endpoint 为空时仅接收在线 Trace。
     */
    HTTP_AGENT,
    /** 平台内置 Agent（通过 AgentCallManager 委托） */
    BUILTIN_AGENT
}
