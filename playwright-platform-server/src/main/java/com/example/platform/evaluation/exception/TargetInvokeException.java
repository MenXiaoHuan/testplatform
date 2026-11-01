package com.example.platform.evaluation.exception;

import com.example.platform.common.BusinessException;

/** Adapter 调用异常（HTTP 超时 / API Key 缺失 / 网络不通 / 响应格式错误）。 */
public class TargetInvokeException extends BusinessException {
    public TargetInvokeException(String message) {
        super("TARGET_INVOKE_FAILED", message);
    }
    public TargetInvokeException(String message, Throwable cause) {
        super("TARGET_INVOKE_FAILED", message);
        initCause(cause);
    }
}
