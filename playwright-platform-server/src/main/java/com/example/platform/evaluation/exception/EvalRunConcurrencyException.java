package com.example.platform.evaluation.exception;

import com.example.platform.common.BusinessException;

/** 离线评测并发护栏触发（C15 per-space / C16 per-Target / C19 幂等）。 */
public class EvalRunConcurrencyException extends BusinessException {
    public EvalRunConcurrencyException(String code, String message) {
        super(code, message);
    }
}
