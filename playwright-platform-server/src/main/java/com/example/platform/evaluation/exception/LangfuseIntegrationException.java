package com.example.platform.evaluation.exception;

import com.example.platform.common.BusinessException;

/** Langfuse 集成异常（查询不可用 / 写入失败）。离线评测降级继续，在线策略自动暂停。 */
public class LangfuseIntegrationException extends BusinessException {
    public LangfuseIntegrationException(String message) {
        super("LANGFUSE_UNAVAILABLE", message);
    }
    public LangfuseIntegrationException(String message, Throwable cause) {
        super("LANGFUSE_UNAVAILABLE", message);
        initCause(cause);
    }
}
