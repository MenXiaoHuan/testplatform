package com.example.platform.evaluation.exception;

import com.example.platform.common.BusinessException;

/** SSRF 安全校验未通过（C7）。 */
public class SsrfViolationException extends BusinessException {
    public SsrfViolationException(String message) {
        super("SSRF_VIOLATION", message);
    }
}
