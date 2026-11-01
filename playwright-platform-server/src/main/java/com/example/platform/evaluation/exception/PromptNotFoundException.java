package com.example.platform.evaluation.exception;

import com.example.platform.common.BusinessException;

/** Prompt 不存在 / 跨 space 访问 / 版本号冲突。 */
public class PromptNotFoundException extends BusinessException {
    public PromptNotFoundException(String message) {
        super("PROMPT_NOT_FOUND", message);
    }
}
